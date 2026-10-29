package com.gliffy.supplylines.engine

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless engine tests: economy, routing, capacity, conversion, save round-trip.
 * All ticks are deterministic (dt = 0.1s).
 */
class GameEngineTest {

    private val dt = 0.1

    private fun content(
        nodes: Map<String, NodeDef>,
        startCoins: Long = 5000,
        capTiers: List<Int> = listOf(2, 3, 5, 8)
    ) = GameContent(
        nodes = nodes,
        startCoins = startCoins,
        speedTiers = listOf(0.10, 0.16, 0.25, 0.40, 0.65),
        speedUpgCosts = listOf(100, 400, 1200, 3600),
        capTiers = capTiers,
        capUpgCosts = listOf(150, 600, 1800),
        nodeRateMults = listOf(1.0, 2.0, 4.0, 8.0, 16.0),
        lineCostFactor = 80.0,
        lineCostMin = 10,
        wantCreditWindow = 30.0,
        dispatchStockCap = 10,
        offlineCapSec = 14400.0,
        offlineEfficiency = 0.5
    )

    private fun run(engine: GameEngine, seconds: Double) {
        var t = 0.0
        while (t < seconds) { engine.tick(dt); t += dt }
    }

    private val sA get() = NodeDef("A", NodeKind.SOURCE, "Source A", 0.1, 0.2,
        produce = ResourceKind.ORE, rate = 0.5)
    private val mB get() = NodeDef("B", NodeKind.MARKET, "Market B", 0.7, 0.6,
        wants = mapOf(ResourceKind.ORE to 0.5), pay = mapOf(ResourceKind.ORE to 10))

    @Test fun lineCostFollowsDistance() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        assertEquals(58L, e.lineCost("A", "B"))
    }

    @Test fun buildLineRejectsInsufficientCoins() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB), startCoins = 20))
        assertFalse(e.buildLine("A", "B"))
        assertEquals(20L, e.state.coins)
    }

    @Test fun noLineMeansNoFlow() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        run(e, 300.0)
        assertEquals(0L, e.state.delivered["ORE"] ?: 0L)
    }

    @Test fun simpleChainEarnsCoins() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        assertTrue(e.buildLine("A", "B"))
        run(e, 300.0)
        val delivered = e.state.delivered["ORE"] ?: 0
        assertTrue("delivered=$delivered", delivered > 30)
        assertEquals(5000L - 58L + delivered * 10L, e.state.coins)
    }

    @Test fun capacityUpgradeIncreasesThroughput() {
        val base = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        base.buildLine("A", "B")
        val upg = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        upg.buildLine("A", "B")
        assertTrue(upg.upgradeLineCap(upg.line("A", "B")!!))
        run(base, 300.0); run(upg, 300.0)
        val dBase = base.state.delivered["ORE"] ?: 0
        val dUpg = upg.state.delivered["ORE"] ?: 0
        assertTrue("upg=$dUpg should beat base=$dBase", dUpg > dBase)
    }

    @Test fun speedUpgradeIncreasesThroughput() {
        val base = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        base.buildLine("A", "B")
        val upg = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        upg.buildLine("A", "B")
        assertTrue(upg.upgradeLineSpeed(upg.line("A", "B")!!))
        run(base, 300.0); run(upg, 300.0)
        val dBase = base.state.delivered["ORE"] ?: 0
        val dUpg = upg.state.delivered["ORE"] ?: 0
        assertTrue("upg=$dUpg should beat base=$dBase", dUpg > dBase)
    }

    @Test fun bfsRoutesThroughDepot() {
        val src = NodeDef("S", NodeKind.SOURCE, "S", 0.1, 0.9, produce = ResourceKind.ORE, rate = 0.3)
        val depot = NodeDef("D", NodeKind.DEPOT, "D", 0.5, 0.5)
        val mkt = NodeDef("M", NodeKind.MARKET, "M", 0.9, 0.1,
            wants = mapOf(ResourceKind.ORE to 0.5), pay = mapOf(ResourceKind.ORE to 10))
        val e = GameEngine(content(mapOf("S" to src, "D" to depot, "M" to mkt)))
        e.buildLine("S", "D"); e.buildLine("D", "M")
        run(e, 300.0)
        assertTrue((e.state.delivered["ORE"] ?: 0) > 50)
    }

    @Test fun converterChainProducesAndSells() {
        val src = NodeDef("S", NodeKind.SOURCE, "S", 0.1, 0.2, produce = ResourceKind.ORE, rate = 0.4)
        val conv = NodeDef("C", NodeKind.CONVERTER, "C", 0.5, 0.5,
            produce = ResourceKind.PART, rate = 0.3, wants = mapOf(ResourceKind.ORE to 0.3))
        val mkt = NodeDef("M", NodeKind.MARKET, "M", 0.9, 0.2,
            wants = mapOf(ResourceKind.PART to 0.5), pay = mapOf(ResourceKind.PART to 100))
        val e = GameEngine(content(mapOf("S" to src, "C" to conv, "M" to mkt)))
        e.buildLine("S", "C"); e.buildLine("C", "M")
        run(e, 600.0)
        assertTrue((e.state.delivered["PART"] ?: 0) > 20)
        // Coin math: start - 2 line costs + deliveries*100 (ORE has no payout path here).
        val parts = e.state.delivered["PART"] ?: 0
        assertEquals(5000L - e.lineCost("S", "C") - e.lineCost("C", "M") + parts * 100L, e.state.coins)
    }

    @Test fun nodeUpgradeRaisesProduction() {
        val fast = NodeDef("S", NodeKind.SOURCE, "S", 0.1, 0.2, produce = ResourceKind.ORE, rate = 0.5, upgBase = 10)
        val mkt = NodeDef("M", NodeKind.MARKET, "M", 0.7, 0.6,
            wants = mapOf(ResourceKind.ORE to 2.0), pay = mapOf(ResourceKind.ORE to 1))
        val base = GameEngine(content(mapOf("S" to fast, "M" to mkt), capTiers = listOf(30)))
        base.buildLine("S", "M")
        val upg = GameEngine(content(mapOf("S" to fast, "M" to mkt), capTiers = listOf(30)))
        upg.buildLine("S", "M")
        assertTrue(upg.upgradeNode("S"))
        run(base, 600.0); run(upg, 600.0)
        assertTrue((upg.state.delivered["ORE"] ?: 0) > (base.state.delivered["ORE"] ?: 0))
    }

    @Test fun unlockNodeWorks() {
        val mkt = NodeDef("M", NodeKind.MARKET, "M", 0.7, 0.6,
            wants = mapOf(ResourceKind.ORE to 0.5), pay = mapOf(ResourceKind.ORE to 10), unlockCost = 500)
        val src = NodeDef("S", NodeKind.SOURCE, "S", 0.1, 0.2, produce = ResourceKind.ORE, rate = 0.5)
        val e = GameEngine(content(mapOf("S" to src, "M" to mkt)))
        assertFalse(e.buildLine("S", "M")) // locked target
        assertTrue(e.unlockNode("M"))
        assertTrue(e.buildLine("S", "M"))
    }

    @Test fun saveLoadRoundTrip() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        e.buildLine("A", "B")
        run(e, 120.0)
        val json = Json.encodeToString(WorldState.serializer(), e.state)
        val loaded = Json.decodeFromString(WorldState.serializer(), json)
        assertEquals(e.state.coins, loaded.coins)
        assertEquals(e.state.delivered, loaded.delivered)
        val before = loaded.delivered.toMap()
        val e2 = GameEngine(content(mapOf("A" to sA, "B" to mB)), loaded)
        run(e2, 60.0)
        assertTrue((e2.state.delivered["ORE"] ?: 0) > (before["ORE"] ?: 0))
    }

    @Test fun offlineEarningsHonourCap() {
        val e = GameEngine(content(mapOf("A" to sA, "B" to mB)))
        e.buildLine("A", "B")
        run(e, 300.0)
        val before = e.state.coins
        val grant = e.offlineEarnings(3600.0)
        assertTrue(grant > 0)
        assertTrue(e.state.cpsAlpha > 0.0)
        assertEquals(before + grant, e.state.coins)
    }
}
