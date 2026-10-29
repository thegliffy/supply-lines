package com.gliffy.supplylines.engine

import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the REAL engine headless and dumps live state JSON to build/preview/.
 * tools/render_preview.py turns those into PNGs with the same palette the Compose UI uses.
 */
class RenderPreview {

    private val outDir = File("build/preview")

    private fun dtRun(e: GameEngine, seconds: Double, dt: Double = 0.1) {
        var t = 0.0
        while (t < seconds) { e.tick(dt); t += dt }
    }

    private fun dump(name: String, e: GameEngine) {
        outDir.mkdirs()
        val coord = HashMap<String, Pair<Int, Int>>()
        for (b in e.state.buildings.values) coord[b.id] = b.x to b.y
        // fixed structures live in state.buildings too (placed at game start) — covered above.

        val cells = buildJsonArray {
            for (o in e.content.oreCells) add(buildJsonObject {
                put("x", o.x); put("y", o.y); put("kind", "ore"); put("item", o.item.name)
            })
            for (b in e.state.buildings.values) add(buildJsonObject {
                put("x", b.x); put("y", b.y); put("kind", e.def(b.defId).kind.name); put("id", b.id)
                if (b.recipe != null) put("recipe", b.recipe)
            })
        }

        val bld = { id: String -> coord[id] }
        val lines = buildJsonArray {
            for (l in e.state.lines) {
                val a = bld(l.a); val b = bld(l.b)
                if (a == null || b == null) continue
                add(buildJsonObject {
                    put("ax", a.first); put("ay", a.second); put("bx", b.first); put("by", b.second)
                    put("hot", l.reserved > 0)
                })
            }
        }

        val dots = buildJsonArray {
            for (sh in e.state.shipments) {
                val ids = sh.path
                if (sh.edge + 1 >= ids.size) continue
                val a = bld(ids[sh.edge]) ?: continue
                val b = bld(ids[sh.edge + 1]) ?: continue
                val l = e.line(ids[sh.edge], ids[sh.edge + 1])
                val speed = if (l != null) e.content.speedTiers[l.speedTier] else e.content.speedTiers[0]
                val edgeLen = kotlin.math.sqrt(((a.first - b.first).toDouble().let { it * it }) + (a.second - b.second).toDouble().let { it * it })
                val f = if (edgeLen <= 0.0) 0.0 else (sh.progress / (edgeLen / speed)).coerceIn(0.0, 1.0)
                add(buildJsonObject {
                    put("x", a.first + (b.first - a.first) * f)
                    put("y", a.second + (b.second - a.second) * f)
                    put("item", sh.item.name)
                })
            }
        }

        val gate = e.currentGate()
        val gateJson = if (gate == null) null else buildJsonObject {
            put("name", gate.name)
            put("items", buildJsonArray {
                for ((item, need) in gate.requires) add(buildJsonObject {
                    put("label", item.name); put("have", e.gateCount(gate.id, item)); put("need", need)
                })
            })
        }

        val root = buildJsonObject {
            put("coins", e.state.coins)
            put("cps", "%.2f".format(e.state.cpsAlpha))
            put("grid", 10)
            put("cells", cells); put("lines", lines); put("dots", dots)
            if (gateJson != null) put("gate", gateJson)
            put("gatesDone", buildJsonArray { e.state.gatesDone.forEach { add(it) } })
        }
        File(outDir, "state-$name.json").writeText(root.toString())
        println("wrote build/preview/state-$name.json coins=${e.state.coins} " +
            "lines=${e.state.lines.size} shipments=${e.state.shipments.size}")
    }

    @Test fun renderStates() {
        val rich = Content.DEFAULT.copy(startCoins = 10000)
        // Fresh start: fixed structures + one line down, first ore en route.
        val fresh = GameEngine(rich)
        val m1 = fresh.place("miner", 1, 2)!!
        assertTrue(fresh.buildLine(m1, "hub"))
        dtRun(fresh, 12.0)
        dump("fresh", fresh)

        // Mid-game: g1 completes mid-run, g2 actively filling at dump time -> belts live.
        val e = GameEngine(rich)
        val minerFe = e.place("miner", 1, 2)!!
        val smelter = e.place("smelter", 2, 2, "smelter_fe")!!
        val benchRod = e.place("bench", 3, 3, "bench_rod")!!
        val benchScrew = e.place("bench", 3, 5, "bench_screw")!!
        val benchPlate = e.place("bench", 0, 6, "bench_plate")!!
        assertTrue(e.buildLine(minerFe, smelter))
        assertTrue(e.buildLine(smelter, benchRod))
        assertTrue(e.buildLine(smelter, benchPlate))
        assertTrue(e.buildLine(benchRod, benchScrew))
        assertTrue(e.buildLine(benchScrew, "hub"))
        assertTrue(e.buildLine(benchPlate, "hub"))
        assertTrue(e.buildLine(minerFe, "hub"))
        assertTrue(e.buildLine(smelter, "hub"))
        val minerCu = e.place("miner_cu", 8, 1)!!
        val smelterCu = e.place("smelter", 7, 1, "smelter_cu")!!
        assertTrue(e.buildLine(minerCu, smelterCu))
        assertTrue(e.buildLine(smelterCu, "hub"))
        dtRun(e, 900.0)
        e.upgradeLineSpeed(e.line(minerFe, "hub")!!)
        dtRun(e, 60.0)
        dump("midgame", e)
    }
}
