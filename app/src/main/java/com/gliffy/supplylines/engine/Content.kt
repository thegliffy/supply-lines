package com.gliffy.supplylines.engine

/** Tunables + the game map. Pure Kotlin so JVM tests can build custom content. */
data class GameContent(
    val nodes: Map<String, NodeDef>,
    val startCoins: Long,
    val speedTiers: List<Double>,
    val speedUpgCosts: List<Long>,
    val capTiers: List<Int>,
    val capUpgCosts: List<Long>,
    val nodeRateMults: List<Double>,
    val lineCostFactor: Double,
    val lineCostMin: Long,
    val wantCreditWindow: Double,
    val dispatchStockCap: Int,
    val offlineCapSec: Double,
    val offlineEfficiency: Double
)

object Content {
    val DEFAULT = GameContent(
        nodes = mapOf(
            "drill_alpha" to NodeDef("drill_alpha", NodeKind.SOURCE, "Drill Alpha", 0.12, 0.18,
                produce = ResourceKind.ORE, rate = 0.25, upgBase = 100, unlockCost = 0),
            "drill_beta" to NodeDef("drill_beta", NodeKind.SOURCE, "Drill Beta", 0.10, 0.55,
                produce = ResourceKind.ORE, rate = 0.5, upgBase = 150, unlockCost = 150),
            "drill_gamma" to NodeDef("drill_gamma", NodeKind.SOURCE, "Drill Gamma", 0.22, 0.88,
                produce = ResourceKind.ORE, rate = 1.0, upgBase = 250, unlockCost = 900),
            "refiner" to NodeDef("refiner", NodeKind.CONVERTER, "Refinery", 0.42, 0.30,
                produce = ResourceKind.PART, rate = 0.15,
                wants = mapOf(ResourceKind.ORE to 0.15), upgBase = 250, unlockCost = 0),
            "fab" to NodeDef("fab", NodeKind.CONVERTER, "Fab", 0.62, 0.68,
                produce = ResourceKind.CHIP, rate = 0.08,
                wants = mapOf(ResourceKind.PART to 0.08), upgBase = 500, unlockCost = 600),
            "hub" to NodeDef("hub", NodeKind.DEPOT, "Central Hub", 0.38, 0.55,
                unlockCost = 120),
            "market_north" to NodeDef("market_north", NodeKind.MARKET, "North Market", 0.85, 0.12,
                wants = mapOf(ResourceKind.ORE to 0.12),
                pay = mapOf(ResourceKind.ORE to 3), unlockCost = 0),
            "market_south" to NodeDef("market_south", NodeKind.MARKET, "South Market", 0.88, 0.55,
                wants = mapOf(ResourceKind.PART to 0.12, ResourceKind.ORE to 0.05),
                pay = mapOf(ResourceKind.PART to 8, ResourceKind.ORE to 3), unlockCost = 300),
            "market_east" to NodeDef("market_east", NodeKind.MARKET, "East Market", 0.78, 0.88,
                wants = mapOf(ResourceKind.PART to 0.15, ResourceKind.CHIP to 0.06),
                pay = mapOf(ResourceKind.PART to 8, ResourceKind.CHIP to 25), unlockCost = 1500)
        ),
        startCoins = 80,
        // Belt speed tiers (normalized units/sec) and the cost to buy the NEXT tier.
        speedTiers = listOf(0.10, 0.16, 0.25, 0.40, 0.65),
        speedUpgCosts = listOf(100, 400, 1200, 3600),
        // Line capacity tiers (shipments in transit) and cost to buy the next.
        capTiers = listOf(2, 3, 5, 8),
        capUpgCosts = listOf(150, 600, 1800),
        nodeRateMults = listOf(1.0, 2.0, 4.0, 8.0, 16.0),
        lineCostFactor = 80.0,
        lineCostMin = 10,
        wantCreditWindow = 30.0,
        dispatchStockCap = 10,
        offlineCapSec = 4 * 3600.0,
        offlineEfficiency = 0.5
    )
}
