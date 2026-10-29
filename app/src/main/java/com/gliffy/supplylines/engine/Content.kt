package com.gliffy.supplylines.engine

import kotlinx.serialization.Serializable

/** Tunables + content. Pure Kotlin so JVM tests can build custom content. */
@Serializable
data class GameContent(
    val defs: Map<String, BuildingDef>,
    val recipes: Map<String, RecipeDef>,
    /** Sequential gates. Complete in order; each unlocks the next. */
    val gates: List<GateDef>,
    val oreCells: List<OreCell>,
    /** Fixed structures: x, y, defId — built at game start. */
    val fixedCells: List<Pair<Int, Int>>,
    val fixedDefIds: List<String>,
    val sinkPrices: Map<Item, Int>,
    val startCoins: Long,
    // Belt speed tiers (cells/sec) and the cost to buy the NEXT tier.
    val speedTiers: List<Double>,
    val speedUpgCosts: List<Long>,
    // Line capacity tiers (shipments in transit) and cost to buy the next.
    val capTiers: List<Int>,
    val capUpgCosts: List<Long>,
    val rateMults: List<Double>,
    val upgBaseCost: Long,
    val lineCostFactor: Double,
    val lineCostMin: Long,
    val wantCreditWindow: Double,
    val stockCap: Int,
    val offlineCapSec: Double,
    val offlineEfficiency: Double
)

private object R {
    // defId -> def
    val defs = mapOf(
        "miner" to BuildingDef(BuildingKind.MINER, "MINER MK.1", cost = 100, gate = null, needsOre = Item.IRON_ORE, rate = 0.25),
        "miner_cu" to BuildingDef(BuildingKind.MINER, "MINER MK.1 (CU)", cost = 100, gate = null, needsOre = Item.COPPER_ORE, rate = 0.25),
        "miner_coal" to BuildingDef(BuildingKind.MINER, "MINER MK.1 (COAL)", cost = 100, gate = null, needsOre = Item.COAL, rate = 0.25),
        "bench" to BuildingDef(BuildingKind.MANUAL_BENCH, "MANUAL BENCH", cost = 40, gate = null),
        "smelter" to BuildingDef(BuildingKind.SMELTER, "SMELTER", cost = 60, gate = null),
        "storage" to BuildingDef(BuildingKind.STORAGE, "STORAGE", cost = 25, gate = null),
        "constructor" to BuildingDef(BuildingKind.CONSTRUCTOR, "CONSTRUCTOR", cost = 150, gate = "g1"),
        "manufacturer" to BuildingDef(BuildingKind.MANUFACTURER, "MANUFACTURER", cost = 120, gate = "g2"),
        "assembler" to BuildingDef(BuildingKind.ASSEMBLER, "ASSEMBLER", cost = 300, gate = "g2"),
        "foundry" to BuildingDef(BuildingKind.FOUNDRY, "FOUNDRY", cost = 400, gate = "g3"),
        // Fixed structures
        "hub" to BuildingDef(BuildingKind.HUB, "HUB", fixed = true),
        "elevator" to BuildingDef(BuildingKind.ELEVATOR, "SPACE ELEVATOR", fixed = true),
        "sink" to BuildingDef(BuildingKind.SINK, "AWESOME SINK", fixed = true)
    )

    // Recipe ids: <building>_<thing>. Gate null = available from the start.
    val recipes = mapOf(
        // Starting (manual crafting — the real T0 grind)
        "bench_plate" to RecipeDef("bench_plate", BuildingKind.MANUAL_BENCH,
            mapOf(Item.IRON_INGOT to 3), mapOf(Item.IRON_PLATE to 2), 30.0),
        "bench_rod" to RecipeDef("bench_rod", BuildingKind.MANUAL_BENCH,
            mapOf(Item.IRON_INGOT to 1), mapOf(Item.IRON_ROD to 2), 30.0),
        "bench_screw" to RecipeDef("bench_screw", BuildingKind.MANUAL_BENCH,
            mapOf(Item.IRON_ROD to 1), mapOf(Item.IRON_SCREW to 4), 30.0),
        "smelter_fe" to RecipeDef("smelter_fe", BuildingKind.SMELTER,
            mapOf(Item.IRON_ORE to 3), mapOf(Item.IRON_INGOT to 2), 20.0),
        "smelter_cu" to RecipeDef("smelter_cu", BuildingKind.SMELTER,
            mapOf(Item.COPPER_ORE to 3), mapOf(Item.COPPER_INGOT to 2), 20.0),
        // After HUB g1
        "ctor_plate" to RecipeDef("ctor_plate", BuildingKind.CONSTRUCTOR,
            mapOf(Item.IRON_INGOT to 3), mapOf(Item.IRON_PLATE to 2), 12.0, "g1"),
        "ctor_rod" to RecipeDef("ctor_rod", BuildingKind.CONSTRUCTOR,
            mapOf(Item.IRON_INGOT to 1), mapOf(Item.IRON_ROD to 2), 6.0, "g1"),
        // After HUB g2
        "manu_screw" to RecipeDef("manu_screw", BuildingKind.MANUFACTURER,
            mapOf(Item.IRON_ROD to 1), mapOf(Item.IRON_SCREW to 4), 8.0, "g2"),
        "manu_wire" to RecipeDef("manu_wire", BuildingKind.MANUFACTURER,
            mapOf(Item.COPPER_INGOT to 1), mapOf(Item.WIRE to 2), 8.0, "g2"),
        "manu_cable" to RecipeDef("manu_cable", BuildingKind.MANUFACTURER,
            mapOf(Item.WIRE to 2), mapOf(Item.CABLE to 1), 16.0, "g2"),
        // After HUB g2 (assembler + the real multi-input machines)
        "asm_rip" to RecipeDef("asm_rip", BuildingKind.ASSEMBLER,
            mapOf(Item.IRON_PLATE to 6, Item.IRON_SCREW to 12), mapOf(Item.REINFORCED_IRON_PLATE to 1), 30.0, "g2"),
        "asm_rotor" to RecipeDef("asm_rotor", BuildingKind.ASSEMBLER,
            mapOf(Item.IRON_ROD to 5, Item.IRON_SCREW to 25), mapOf(Item.IRON_ROTOR to 1), 60.0, "g2"),
        "asm_plating" to RecipeDef("asm_plating", BuildingKind.ASSEMBLER,
            mapOf(Item.REINFORCED_IRON_PLATE to 1, Item.IRON_ROTOR to 1), mapOf(Item.SMART_PLATING to 1), 30.0, "g2"),
        // After HUB g3 (foundry unlocks; Phase 1 = the elevator finale)
        "fnd_steel" to RecipeDef("fnd_steel", BuildingKind.FOUNDRY,
            mapOf(Item.IRON_INGOT to 3, Item.COAL to 1), mapOf(Item.STEEL_INGOT to 2), 24.0, "g3"),
        "fnd_beam" to RecipeDef("fnd_beam", BuildingKind.FOUNDRY,
            mapOf(Item.STEEL_INGOT to 2), mapOf(Item.STEEL_BEAM to 1), 12.0, "g3"),
        "asm_frame" to RecipeDef("asm_frame", BuildingKind.ASSEMBLER,
            mapOf(Item.STEEL_BEAM to 3, Item.IRON_SCREW to 12), mapOf(Item.MODULAR_FRAME to 1), 60.0, "p1")
    )

    /** Sequential gates. HUB gates are real HUB-milestone costs; p1 = real Phase 1. */
    val gates = listOf(
        GateDef("g1", "HUB UPGRADE MK.I", BuildingKind.HUB,
            mapOf(Item.IRON_ROD to 10, Item.IRON_INGOT to 10, Item.IRON_ORE to 10)),
        GateDef("g2", "HUB UPGRADE MK.II", BuildingKind.HUB,
            mapOf(Item.IRON_PLATE to 30, Item.IRON_SCREW to 100)),
        GateDef("g3", "HUB UPGRADE MK.III", BuildingKind.HUB,
            mapOf(Item.WIRE to 200, Item.CABLE to 100, Item.REINFORCED_IRON_PLATE to 20, Item.IRON_ROTOR to 20)),
        GateDef("p1", "PHASE 1 · SMART PLATING", BuildingKind.ELEVATOR,
            mapOf(Item.SMART_PLATING to 50))
    )

    // 10x10 grid ore (x, y in 0..9) — iron amber, copper orange, coal black.
    val ore = listOf(
        OreCell(1, 2, Item.IRON_ORE), OreCell(7, 7, Item.IRON_ORE), OreCell(2, 8, Item.IRON_ORE),
        OreCell(8, 1, Item.COPPER_ORE), OreCell(1, 6, Item.COPPER_ORE),
        OreCell(6, 3, Item.COAL), OreCell(3, 9, Item.COAL)
    )

    // Sink prices (coins/unit — roughly real sink-value ratios).
    val sinkPrices = mapOf(
        Item.IRON_ORE to 1, Item.COPPER_ORE to 2, Item.COAL to 2,
        Item.IRON_INGOT to 2, Item.COPPER_INGOT to 3, Item.STEEL_INGOT to 5,
        Item.IRON_PLATE to 3, Item.IRON_ROD to 2, Item.IRON_SCREW to 1,
        Item.WIRE to 1, Item.CABLE to 3,
        Item.REINFORCED_IRON_PLATE to 15, Item.IRON_ROTOR to 12,
        Item.STEEL_BEAM to 8, Item.SMART_PLATING to 25,
        Item.MODULAR_FRAME to 30
    )

    // Fixed structures: hub mid-left, elevator top-center, sink mid-right.
    val fixed = listOf(
        Triple(1, 4, "hub"), Triple(5, 0, "elevator"), Triple(8, 4, "sink")
    )
}

object Content {
    val DEFAULT = GameContent(
        defs = R.defs,
        recipes = R.recipes,
        gates = R.gates,
        oreCells = R.ore,
        fixedCells = R.fixed.map { it.first to it.second },
        fixedDefIds = R.fixed.map { it.third },
        sinkPrices = R.sinkPrices,
        startCoins = 350,
        // Belt tiers Mk1..Mk5 (cells/sec).
        speedTiers = listOf(0.8, 1.3, 2.0, 3.0, 4.5),
        speedUpgCosts = listOf(120, 480, 1440, 4320),
        capTiers = listOf(2, 3, 5, 8),
        capUpgCosts = listOf(150, 600, 1800),
        rateMults = listOf(1.0, 2.0, 4.0, 8.0),
        upgBaseCost = 200,
        lineCostFactor = 40.0,
        lineCostMin = 25,
        wantCreditWindow = 30.0,
        stockCap = 12,
        offlineCapSec = 4 * 3600.0,
        offlineEfficiency = 0.5
    )
}
