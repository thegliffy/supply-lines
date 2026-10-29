package com.gliffy.supplylines.engine

import kotlinx.serialization.Serializable

/** Items the belts carry — real Satisfactory names & recipes (no fluids yet). */
@Serializable
enum class Item {
    IRON_ORE, COPPER_ORE, COAL,
    IRON_INGOT, COPPER_INGOT, STEEL_INGOT, STEEL_BEAM,
    IRON_PLATE, IRON_ROD, IRON_SCREW,
    WIRE, CABLE,
    REINFORCED_IRON_PLATE, IRON_ROTOR, SMART_PLATING,
    MODULAR_FRAME, AUTOMATED_WIRING
}

@Serializable
enum class BuildingKind {
    MINER, SMELTER, MANUAL_BENCH, MANUFACTURER, CONSTRUCTOR, ASSEMBLER,
    FOUNDRY, STORAGE, HUB, ELEVATOR, SINK
}

/** One recipe a building can run: inputs consumed -> outputs produced, per craft. */
@Serializable
data class RecipeDef(
    val id: String,
    val building: BuildingKind,
    val inputs: Map<Item, Int>,
    val outputs: Map<Item, Int>,
    /** Seconds per craft at tier 0. */
    val craftTime: Double,
    /** Gate id that unlocks this recipe; null = available from the start. */
    val gate: String? = null
)

@Serializable
data class BuildingDef(
    val kind: BuildingKind,
    val name: String,
    /** Build cost in coins; 0 for fixed structures. */
    val cost: Long = 0,
    /** Miners must sit on a matching ore cell. */
    val needsOre: Item? = null,
    val fixed: Boolean = false,
    /** Gate that must be complete before this can be built; null = from start. */
    val gate: String? = null,
    /** Miners only: base items/sec at tier 0 (real Mk.1 rates). */
    val rate: Double = 0.0
)

/** Tech tree gate, sequential. Complete one to unlock the next (and recipes). */
@Serializable
data class GateDef(
    val id: String,
    val name: String,
    val building: BuildingKind,
    val requires: Map<Item, Int>
)

/** Terrain: ore spots on the 10x10 grid (x, y in 0..9). */
@Serializable
data class OreCell(val x: Int, val y: Int, val item: Item)

@Serializable
data class LineState(
    val a: String,
    val b: String,
    var speedTier: Int = 0,
    var capTier: Int = 0,
    var reserved: Int = 0
)

@Serializable
data class ShipmentState(
    val path: List<String>,
    var edge: Int = 0,
    var progress: Double = 0.0,
    val item: Item
)

@Serializable
data class BuildingRuntime(
    val id: String,
    val defId: String = "",
    val kind: BuildingKind,
    val x: Int,
    val y: Int,
    /** Fixed structures have no chosen recipe. Machines: the one it runs. */
    var recipe: String? = null,
    var tier: Int = 0,
    /** Items sitting here (unconsumed inputs or waiting outputs), per item. */
    var stock: MutableMap<String, Int> = mutableMapOf(),
    /** Fractional craft progress (seconds). */
    var craftClock: Double = 0.0,
    /** Demand credit per item, whole units. */
    var wantCredit: MutableMap<String, Double> = mutableMapOf()
)

@Serializable
data class WorldState(
    var coins: Long,
    /** All buildings on the map (fixed + placed), keyed by id. */
    var buildings: MutableMap<String, BuildingRuntime> = mutableMapOf(),
    var lines: MutableList<LineState> = mutableListOf(),
    var shipments: MutableList<ShipmentState> = mutableListOf(),
    /** gate id -> items delivered toward it. */
    var gateProgress: MutableMap<String, Long> = mutableMapOf(),
    /** Gates completed, in order. */
    var gatesDone: MutableList<String> = mutableListOf(),
    var earnedTotal: Long = 0,
    var lastSaveEpochSec: Long = 0,
    var cpsAlpha: Double = 0.0
)
