package com.gliffy.supplylines.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin simulation, JVM-testable. v0.2: 10x10 grid, real Satisfactory
 * early-game content. Buildings sit on grid cells (miners on matching ore,
 * machines on any empty cell). Belt lines connect buildings; items physically
 * travel belt-to-belt with per-line capacity. Recipes consume all inputs from
 * a machine's buffer when full; outputs dispatch to consumers wanting them.
 * Gates (HUB milestones -> Space Elevator phases) are sequential.
 */
class GameEngine(val content: GameContent, initial: WorldState? = null) {

    companion object { const val GRID = 10 }

    val state: WorldState = initial ?: fresh()
    var gateCompleted: ((GateDef) -> Unit)? = null

    private var nextBuildingId = 1

    init {
        if (initial != null) {
            val maxId = state.buildings.keys.mapNotNull { it.removePrefix("b").toIntOrNull() }.maxOrNull()
            nextBuildingId = (maxId ?: 0) + 1
        }
    }

    private fun fresh(): WorldState {
        val s = WorldState(coins = content.startCoins)
        content.fixedCells.forEachIndexed { i, (x, y) ->
            val defId = content.fixedDefIds[i]
            s.buildings[defId] = BuildingRuntime(id = defId, defId = defId,
                kind = content.defs.getValue(defId).kind, x = x, y = y)
        }
        return s
    }

    // ---------------- lookups ----------------

    fun def(defId: String): BuildingDef = content.defs.getValue(defId)
    fun recipe(id: String?): RecipeDef? = id?.let { content.recipes[it] }
    fun building(id: String): BuildingRuntime? = state.buildings[id]
    fun line(a: String, b: String): LineState? =
        state.lines.find { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    fun gateById(id: String): GateDef = content.gates.first { it.id == id }
    fun currentGate(): GateDef? = content.gates.firstOrNull { it.id !in state.gatesDone }

    fun isGateDone(defId: String): Boolean {
        val g = def(defId).gate ?: return true
        return g in state.gatesDone
    }

    /** Gate progress counter store: "gateId:ITEM" -> delivered. */
    fun gateCount(gateId: String, item: Item): Long =
        state.gateProgress["$gateId:$item"] ?: 0L

    private fun bumpGate(gateId: String, item: Item) {
        val g = gateById(gateId)
        if (g.id != currentGate()?.id) return            // sequential only
        if (item !in g.requires) return
        val key = "$gateId:$item"
        val have = gateCount(gateId, item)
        if (have < g.requires.getValue(item)) {
            state.gateProgress[key] = have + 1
            if (g.requires.keys.all { gateCount(gateId, it) >= g.requires.getValue(it) }) {
                state.gatesDone.add(gateId)
                gateCompleted?.invoke(g)
            }
        }
    }

    /** Cells a def can go on right now (terrain + occupancy + gate). */
    fun canPlace(defId: String, x: Int, y: Int): Boolean {
        if (x !in 0 until GRID || y !in 0 until GRID) return false
        if (state.buildings.values.any { it.x == x && it.y == y }) return false
        val d = def(defId)
        if (!isGateDone(defId)) return false
        val ore = content.oreCells.find { it.x == x && it.y == y }?.item
        return if (d.needsOre != null) ore == d.needsOre else ore == null
    }

    /** Recipes available for a def right now (gate-unlocked). */
    fun recipesFor(defId: String): List<RecipeDef> =
        content.recipes.values.filter { it.building == def(defId).kind && (it.gate == null || it.gate in state.gatesDone) }

    fun lineCost(a: String, b: String): Long {
        val ba = building(a) ?: return Long.MAX_VALUE
        val bb = building(b) ?: return Long.MAX_VALUE
        val dist = sqrt(((ba.x - bb.x).toDouble().let { it * it }) + (ba.y - bb.y).toDouble().let { it * it })
        return max(content.lineCostMin, (dist * content.lineCostFactor).toLong())
    }

    // ---------------- actions ----------------

    /** Place a building; machines pass the chosen recipe id. Returns the new id. */
    fun place(defId: String, x: Int, y: Int, recipeId: String? = null): String? {
        if (!canPlace(defId, x, y)) return null
        val d = def(defId)
        if (state.coins < d.cost) return null
        val recipes = recipesFor(defId)
        if (d.kind != BuildingKind.MINER && d.kind != BuildingKind.STORAGE) {
            if (recipeId == null || recipeId !in recipes.map { it.id }) return null
        }
        state.coins -= d.cost
        val id = "b${nextBuildingId++}"
        state.buildings[id] = BuildingRuntime(id = id, defId = defId, kind = d.kind, x = x, y = y, recipe = recipeId)
        return id
    }

    /** Sell a placed building for half its cost back; removes its lines. */
    fun sell(id: String): Boolean {
        val b = building(id) ?: return false
        if (def(b.defId).fixed) return false
        state.coins += def(b.defId).cost / 2
        state.buildings.remove(id)
        state.lines.removeAll { it.a == id || it.b == id }
        state.shipments.removeAll { id in it.path }
        return true
    }

    fun buildLine(a: String, b: String): Boolean {
        if (a == b) return false
        if (building(a) == null || building(b) == null) return false
        if (line(a, b) != null) return false
        val cost = lineCost(a, b)
        if (state.coins < cost) return false
        state.coins -= cost
        state.lines.add(LineState(a, b))
        return true
    }

    fun upgradeCost(tier: Int): Long = content.upgBaseCost * (1L shl (2 * tier))

    fun upgradeBuilding(id: String): Boolean {
        val b = building(id) ?: return false
        if (b.tier >= content.rateMults.size - 1) return false
        val cost = upgradeCost(b.tier)
        if (state.coins < cost) return false
        state.coins -= cost; b.tier++
        return true
    }

    fun upgradeLineSpeed(l: LineState): Boolean {
        if (l.speedTier >= content.speedTiers.size - 1) return false
        val cost = content.speedUpgCosts[l.speedTier]
        if (state.coins < cost) return false
        state.coins -= cost; l.speedTier++
        return true
    }

    fun upgradeLineCap(l: LineState): Boolean {
        if (l.capTier >= content.capTiers.size - 1) return false
        val cost = content.capUpgCosts[l.capTier]
        if (state.coins < cost) return false
        state.coins -= cost; l.capTier++
        return true
    }

    // ---------------- simulation ----------------

    private fun mult(tier: Int) = content.rateMults[min(tier, content.rateMults.size - 1)]

    /** Who wants `item` right now (demand credit >= 1), machines + gates + sink. */
    private fun consumersOf(item: Item): List<BuildingRuntime> = state.buildings.values.filter { b ->
        (b.wantCredit[item.name] ?: 0.0) >= 1.0
    }

    /** Gate buildings accept current-gate items slowly; the sink buys anything priced. */
    private val gateAcceptRate = 0.5   // items/sec a HUB/ELEVATOR will take
    private val sinkRate = 0.4         // items/sec the AWESOME Sink buys

    /** Demand credit accrues at each consumer's recipe rate (or gate/sink rate). */
    private fun accumulateDemand(b: BuildingRuntime, dt: Double) {
        when (b.kind) {
            BuildingKind.HUB, BuildingKind.ELEVATOR -> {
                val g = currentGate() ?: return
                if (g.building != b.kind) return
                for (item in g.requires.keys) {
                    if (gateCount(g.id, item) >= g.requires.getValue(item)) continue
                    b.wantCredit[item.name] = min((b.wantCredit[item.name] ?: 0.0) + gateAcceptRate * dt,
                        content.wantCreditWindow)
                }
            }
            BuildingKind.SINK -> {
                for (item in content.sinkPrices.keys) {
                    b.wantCredit[item.name] = min((b.wantCredit[item.name] ?: 0.0) + sinkRate * dt,
                        content.wantCreditWindow)
                }
            }
            else -> {
                val r = recipe(b.recipe) ?: return
                val rate = mult(b.tier) / r.craftTime          // crafts per sec
                for ((item, qty) in r.inputs) {
                    val w = (b.wantCredit[item.name] ?: 0.0) + rate * qty * dt
                    b.wantCredit[item.name] = min(w, content.wantCreditWindow)
                }
            }
        }
    }

    private fun adj(id: String): List<LineState> =
        state.lines.filter { it.a == id || it.b == id }

    private fun bfsPath(from: String, to: String): List<String>? {
        if (from == to) return listOf(from)
        val prev = mutableMapOf<String, String>()
        val q = ArrayDeque<String>(); q.add(from)
        val seen = mutableSetOf(from)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            for (l in adj(cur)) {
                val nxt = if (l.a == cur) l.b else l.a
                if (nxt in seen) continue
                seen.add(nxt); prev[nxt] = cur
                if (nxt == to) {
                    val path = mutableListOf(to); var c = to
                    while (c != from) { c = prev.getValue(c); path.add(c) }
                    return path.reversed()
                }
                q.add(nxt)
            }
        }
        return null
    }

    private fun reserve(path: List<String>): Boolean {
        for (i in 0 until path.size - 1) {
            val l = line(path[i], path[i + 1]) ?: return false
            if (l.reserved >= content.capTiers[l.capTier]) return false
        }
        for (i in 0 until path.size - 1) line(path[i], path[i + 1])!!.reserved++
        return true
    }

    private fun edgeOf(path: List<String>, edge: Int): LineState? =
        if (edge < path.size - 1) line(path[edge], path[edge + 1]) else null

    private fun release(l: LineState) { l.reserved = max(0, l.reserved - 1) }

    /** Try to send one buffered item from `from` toward the nearest consumer. */
    private fun dispatch(from: BuildingRuntime, item: Item): Boolean {
        val def = def(from.defId)
        val produces = when {
            def.kind == BuildingKind.MINER -> oreUnder(from) == item
            else -> recipe(from.recipe)?.outputs?.containsKey(item) == true
        }
        if (!produces) return false
        if ((from.stock[item.name] ?: 0) < 1) return false
        val consumers = consumersOf(item).filter { it.id != from.id }
        if (consumers.isEmpty()) return false
        val target = consumers
            .mapNotNull { c -> bfsPath(from.id, c.id)?.let { c to it } }
            .minByOrNull { it.second.size } ?: return false
        val path = target.second
        if (!reserve(path)) return false
        from.stock[item.name] = from.stock.getValue(item.name) - 1
        state.shipments.add(ShipmentState(path = path, item = item))
        return true
    }

    private fun oreUnder(b: BuildingRuntime): Item? =
        content.oreCells.find { it.x == b.x && it.y == b.y }?.item

    private fun canCraft(b: BuildingRuntime): Boolean {
        val r = recipe(b.recipe) ?: return false
        return r.inputs.all { (item, qty) -> (b.stock[item.name] ?: 0) >= qty }
    }

    private fun doCraft(b: BuildingRuntime) {
        val r = recipe(b.recipe) ?: return
        for ((item, qty) in r.inputs) b.stock[item.name] = b.stock.getValue(item.name) - qty
        for ((item, qty) in r.outputs) {
            b.stock[item.name] = min(content.stockCap, (b.stock[item.name] ?: 0) + qty)
        }
    }

    private fun deliverTo(b: BuildingRuntime, item: Item) {
        when (b.kind) {
            BuildingKind.SINK -> {
                val price = content.sinkPrices[item] ?: return
                state.coins += price; state.earnedTotal += price
            }
            BuildingKind.HUB, BuildingKind.ELEVATOR -> bumpGate(currentGate()?.id ?: return, item)
            else -> {
                // Machine: arrival goes to its input buffer.
                val r = recipe(b.recipe) ?: return
                if (item in r.inputs) b.stock[item.name] = min(content.stockCap, (b.stock[item.name] ?: 0) + 1)
            }
        }
    }

    private fun arrive(shipment: ShipmentState) {
        val dest = building(shipment.path.last()) ?: return
        val item = shipment.item
        val wanted = (dest.wantCredit[item.name] ?: 0.0) >= 1.0
        if (!wanted) return                          // wait at node, retry later
        dest.wantCredit[item.name] = dest.wantCredit.getValue(item.name) - 1.0
        deliverTo(dest, item)
    }

    /** One simulation step. Returns coins actually earned this tick. */
    fun tick(dt: Double): Long {
        val earnedBefore = state.earnedTotal

        // Demand credit accrues at each consumer's recipe rate.
        for (b in state.buildings.values) accumulateDemand(b, dt)

        // Production: machines craft when inputs in buffer; miners generate ore.
        for (b in state.buildings.values) {
            when {
                recipe(b.recipe) != null -> {
                    b.craftClock += dt * mult(b.tier)
                    val r = recipe(b.recipe)!!
                    while (b.craftClock >= r.craftTime) {
                        if (!canCraft(b)) break
                        b.craftClock -= r.craftTime
                        doCraft(b)
                    }
                }
                def(b.defId).kind == BuildingKind.MINER -> {
                    val ore = oreUnder(b) ?: continue
                    b.craftClock += dt * mult(b.tier) * def(b.defId).rate
                    while (b.craftClock >= 1.0) {
                        b.craftClock -= 1.0
                        b.stock[ore.name] = min(content.stockCap, (b.stock[ore.name] ?: 0) + 1)
                    }
                }
            }
        }

        // Dispatch buffered outputs.
        for (b in state.buildings.values) {
            val items = when {
                def(b.defId).kind == BuildingKind.MINER -> listOfNotNull(oreUnder(b))
                else -> recipe(b.recipe)?.outputs?.keys?.toList() ?: emptyList()
            }
            for (item in items) dispatch(b, item)
        }

        // Move shipments; arrivals deliver at the destination.
        for (sh in state.shipments.toList()) {
            val l = edgeOf(sh.path, sh.edge) ?: continue
            val speed = content.speedTiers[l.speedTier]
            val a = building(sh.path[sh.edge])!!; val b = building(sh.path[sh.edge + 1])!!
            val edgeLen = sqrt(((a.x - b.x).toDouble().let { it * it }) + (a.y - b.y).toDouble().let { it * it })
            sh.progress += dt
            if (sh.progress >= edgeLen / speed) {
                release(l)
                sh.edge++; sh.progress = 0.0
                if (sh.edge >= sh.path.size - 1) {
                    arrive(sh)
                    state.shipments.remove(sh)
                } else {
                    // Reserve the next leg or hold at the node.
                    val nl = edgeOf(sh.path, sh.edge)
                    if (nl == null || nl.reserved >= content.capTiers[nl.capTier]) {
                        // Can't proceed: park it here without a reservation.
                        sh.progress = 0.0
                    } else nl.reserved++
                }
            }
        }

        // EMA of coins/sec.
        val gained = (state.earnedTotal - earnedBefore).toDouble() / max(dt, 1e-9)
        val a = 1.0 - Math.exp(-dt / 15.0)
        state.cpsAlpha += (gained - state.cpsAlpha) * a

        return (state.earnedTotal - earnedBefore)
    }

    /** Apply offline progress (capped, reduced efficiency). Returns coins granted. */
    fun offlineEarnings(elapsedSec: Double): Long {
        val capped = min(elapsedSec, content.offlineCapSec)
        val gain = (state.cpsAlpha * capped * content.offlineEfficiency).toLong()
        if (gain > 0) { state.coins += gain; state.earnedTotal += gain }
        return gain
    }
}
