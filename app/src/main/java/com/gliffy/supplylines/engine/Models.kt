package com.gliffy.supplylines.engine

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceKind { ORE, PART, CHIP }

@Serializable
enum class NodeKind { SOURCE, CONVERTER, DEPOT, MARKET }

/** Static definition of a map node. Coordinates are normalized 0..1 (x right, y down). */
@Serializable
data class NodeDef(
    val id: String,
    val kind: NodeKind,
    val name: String,
    val x: Double,
    val y: Double,
    /** Sources + converters: what this node pushes into the network. */
    val produce: ResourceKind? = null,
    /** Units per second at tier 0. */
    val rate: Double = 0.0,
    /** Consumes: resource -> demand units per second (markets pay, converters feed). */
    val wants: Map<ResourceKind, Double> = emptyMap(),
    /** Market payout in coins per unit delivered. */
    val pay: Map<ResourceKind, Int> = emptyMap(),
    /** Base cost of the first node-tier upgrade (escalates x4 per tier). */
    val upgBase: Long = 100,
    /** Coins to activate this node; 0 = active from the start. */
    val unlockCost: Long = 0
)

@Serializable
data class LineState(
    val a: String,
    val b: String,
    var speedTier: Int = 0,
    var capTier: Int = 0,
    /** Shipments currently occupying this line. */
    var reserved: Int = 0
)

@Serializable
data class ShipmentState(
    /** Node ids, origin .. destination. */
    val path: List<String>,
    var edge: Int = 0,
    /** Seconds elapsed on the current edge. */
    var progress: Double = 0.0,
    val res: ResourceKind
)

@Serializable
data class NodeRuntime(
    val id: String,
    var unlocked: Boolean,
    var tier: Int = 0,
    /** Items waiting to be dispatched (converted/produced), per resource. */
    var stock: MutableMap<String, Int> = mutableMapOf(),
    /** Fractional production credit (units). */
    var prodCredit: Double = 0.0,
    /** Consumable demand credit per resource, in whole units. */
    var wantCredit: MutableMap<String, Double> = mutableMapOf()
)

@Serializable
data class WorldState(
    var coins: Long,
    var nodes: MutableMap<String, NodeRuntime> = mutableMapOf(),
    var lines: MutableList<LineState> = mutableListOf(),
    var shipments: MutableList<ShipmentState> = mutableListOf(),
    var delivered: MutableMap<String, Long> = mutableMapOf(),
    var earnedTotal: Long = 0,
    /** Epoch seconds at save time (for offline earnings). */
    var lastSaveEpochSec: Long = 0,
    /** Fast exponential moving average of coins/sec for the UI + offline calc. */
    var cpsAlpha: Double = 0.0
)
