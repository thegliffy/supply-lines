package com.gliffy.supplylines.engine

import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The whole game model: tick-based simulation, BFS routing with per-line
 * capacity reservations, conversion, and the coin economy. Pure Kotlin — no
 * Android imports — so it is unit-testable on the JVM.
 */
class GameEngine(val content: GameContent, initial: WorldState? = null) {

    val state: WorldState

    init {
        state = initial ?: run {
            val nodes = content.nodes.map { (id, def) ->
                id to NodeRuntime(id, unlocked = def.unlockCost <= 0L)
            }.toMap().toMutableMap()
            WorldState(coins = content.startCoins, nodes = nodes)
        }
    }

    fun nodeDef(id: String): NodeDef = content.nodes[id] ?: error("no node $id")

    fun runtime(id: String): NodeRuntime = state.nodes[id] ?: error("no runtime $id")

    fun dist(a: String, b: String): Double {
        val na = nodeDef(a); val nb = nodeDef(b)
        return sqrt((na.x - nb.x) * (na.x - nb.x) + (na.y - nb.y) * (na.y - nb.y))
    }

    fun lineCost(a: String, b: String): Long = (content.lineCostFactor * dist(a, b) + 0.5).roundToLong().coerceAtLeast(content.lineCostMin)

    fun nodeUpgCost(id: String): Long {
        var c = nodeDef(id).upgBase
        repeat(runtime(id).tier) { c *= 4 }
        return c
    }

    fun lineSpeedUpgCost(l: LineState): Long? =
        if (l.speedTier >= content.speedTiers.lastIndex) null else content.speedUpgCosts[l.speedTier]

    fun lineCapUpgCost(l: LineState): Long? =
        if (l.capTier >= content.capTiers.lastIndex) null else content.capUpgCosts[l.capTier]

    fun canBuildLine(a: String, b: String): Boolean =
        runtime(a).unlocked && runtime(b).unlocked && line(a, b) == null && state.coins >= lineCost(a, b)

    fun buildLine(a: String, b: String): Boolean {
        if (!canBuildLine(a, b)) return false
        state.coins -= lineCost(a, b)
        state.lines.add(LineState(a, b))
        return true
    }

    fun unlockNode(id: String): Boolean {
        val rt = runtime(id)
        if (rt.unlocked) return false
        val cost = nodeDef(id).unlockCost
        if (cost <= 0L || state.coins < cost) return false
        state.coins -= cost
        rt.unlocked = true
        return true
    }

    fun upgradeNode(id: String): Boolean {
        val rt = runtime(id)
        if (!rt.unlocked || nodeDef(id).rate <= 0.0) return false
        val cost = nodeUpgCost(id)
        if (state.coins < cost) return false
        state.coins -= cost
        rt.tier += 1
        return true
    }

    fun upgradeLineSpeed(l: LineState): Boolean {
        val cost = lineSpeedUpgCost(l) ?: return false
        if (state.coins < cost) return false
        state.coins -= cost
        l.speedTier += 1
        return true
    }

    fun upgradeLineCap(l: LineState): Boolean {
        val cost = lineCapUpgCost(l) ?: return false
        if (state.coins < cost) return false
        state.coins -= cost
        l.capTier += 1
        return true
    }

    fun line(a: String, b: String): LineState? =
        state.lines.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) }

    /** One simulation step (seconds). */
    fun tick(dt: Double) {
        for (rt in state.nodes.values) {
            if (!rt.unlocked) continue
            val def = nodeDef(rt.id)

            // Demand credit accumulates so consumers can take whole items.
            val demandMult = if (def.rate > 0.0) rateMult(rt.tier) else 1.0
            for ((res, rate) in def.wants) {
                val key = res.name
                val effRate = rate * demandMult
                val cap = (effRate * content.wantCreditWindow).coerceAtLeast(1.0)
                val cur = (rt.wantCredit[key] ?: 0.0) + effRate * dt
                rt.wantCredit[key] = cur.coerceAtMost(cap)
            }

            // Producers (sources and converters' output side handled via stock).
            if (def.produce != null && def.rate > 0.0 && def.wants.isEmpty()) {
                rt.prodCredit += def.rate * rateMult(rt.tier) * dt
                if (rt.prodCredit >= 1.0) {
                    val whole = rt.prodCredit.toInt()
                    rt.prodCredit -= whole
                    addToStock(rt, def.produce.name, whole)
                }
            }

            for ((resKey, _) in rt.stock) {
                val res = ResourceKind.valueOf(resKey)
                while ((rt.stock[resKey] ?: 0) > 0) {
                    if (!dispatch(rt.id, res)) break
                }
            }
        }

        advanceShipments(dt)

        // EMA of coins/sec over the whole tick, not per event (tau ~15s).
        val instRate = tickEarned / dt
        state.cpsAlpha = if (cpsAlphaInit) {
            state.cpsAlpha + (instRate - state.cpsAlpha) * (1.0 - kotlin.math.exp(-dt / 15.0))
        } else {
            cpsAlphaInit = true
            instRate
        }
        tickEarned = 0.0
    }

    private var tickEarned = 0.0
    private var cpsAlphaInit = false

    private fun advanceShipments(dt: Double) {
        val done = ArrayList<ShipmentState>()
        for (sh in state.shipments) {
            val from = sh.path[sh.edge]
            val to = sh.path[sh.edge + 1]
            val l = line(from, to) ?: error("shipment line missing")
            val edgeTime = dist(from, to) / content.speedTiers[l.speedTier]
            sh.progress += dt
            while (sh.progress >= edgeTime && sh.edge < sh.path.size - 2) {
                sh.progress -= edgeTime
                sh.edge += 1
            }
            if (sh.edge >= sh.path.size - 2 && sh.progress >= edgeTime) {
                arrive(sh)
                done.add(sh)
            }
        }
        state.shipments.removeAll(done)
    }

    private fun arrive(sh: ShipmentState) {
        // Release the whole path reservation.
        for (i in 0 until sh.path.size - 1) {
            line(sh.path[i], sh.path[i + 1])?.let { it.reserved -= 1 }
        }
        val dest = runtime(sh.path.last())
        val def = nodeDef(sh.path.last())
        val res = sh.res
        val credit = dest.wantCredit[res.name] ?: 0.0
        if (credit >= 1.0) {
            dest.wantCredit[res.name] = credit - 1.0
            if (def.pay[res] != null) {
                earn(def.pay[res]!!.toLong())
                state.delivered[res.name] = (state.delivered[res.name] ?: 0L) + 1
            } else if (def.produce != null) {
                // Converter: input consumed -> output goes to dispatch stock.
                addToStock(dest, def.produce.name, 1)
            }
            // If nobody wanted it after all, the item is lost (shouldn't happen:
            // dispatch only targets nodes with credit).
        }
    }

    /** Find a route from [from] to the nearest consumer of [res] and dispatch one item. */
    private fun dispatch(from: String, res: ResourceKind): Boolean {
        val path = bfsPath(from, res) ?: return false
        // Whole-path capacity check.
        for (i in 0 until path.size - 1) {
            val l = line(path[i], path[i + 1]) ?: return false
            if (l.reserved >= content.capTiers[l.capTier]) return false
        }
        for (i in 0 until path.size - 1) line(path[i], path[i + 1])!!.reserved += 1
        val rt = runtime(from)
        rt.stock[res.name] = (rt.stock[res.name] ?: 0) - 1
        state.shipments.add(ShipmentState(path = path, res = res))
        return true
    }

    private fun bfsPath(from: String, res: ResourceKind): List<String>? {
        // BFS; destinations are unlocked nodes whose wantCredit for res >= 1.
        val parent = mutableMapOf<String, String>()
        val visited = mutableSetOf(from)
        val queue = ArrayDeque<String>()
        queue.add(from)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val def = nodeDef(cur)
            if (cur != from && (runtime(cur).wantCredit[res.name] ?: 0.0) >= 1.0 && def.wants.containsKey(res)) {
                val path = mutableListOf(cur)
                var n = cur
                while (n != from) { n = parent[n]!!; path.add(n) }
                path.reverse()
                return path
            }
            for (l in state.lines) {
                var nxt: String? = null
                if (l.a == cur) nxt = l.b else if (l.b == cur) nxt = l.a
                if (nxt == null || !visited.add(nxt)) continue
                if (!runtime(nxt).unlocked) continue
                parent[nxt] = cur
                queue.add(nxt)
            }
        }
        return null
    }

    private fun addToStock(rt: NodeRuntime, resKey: String, n: Int) {
        val cap = content.dispatchStockCap
        rt.stock[resKey] = ((rt.stock[resKey] ?: 0) + n).coerceAtMost(cap)
    }

    private fun rateMult(tier: Int): Double {
        val table = content.nodeRateMults
        return table[minOf(tier, table.lastIndex)]
    }

    private fun earn(amount: Long) {
        state.coins += amount
        state.earnedTotal += amount
        tickEarned += amount.toDouble()
    }

    /**
     * Offline earnings: called on load with seconds away. Grants 50% of the
     * remembered throughput rate, capped at 4 hours. Returns coins granted.
     */
    fun offlineEarnings(elapsedSec: Double): Long {
        val capped = minOf(elapsedSec, content.offlineCapSec)
        if (capped <= 0 || state.cpsAlpha <= 0.0) return 0
        val grant = (state.cpsAlpha * capped * content.offlineEfficiency).toLong()
        if (grant > 0) { state.coins += grant; state.earnedTotal += grant }
        return grant
    }
}
