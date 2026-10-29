package com.gliffy.supplylines.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt
import com.gliffy.supplylines.BuildOption
import com.gliffy.supplylines.GameViewModel
import com.gliffy.supplylines.engine.BuildingKind
import com.gliffy.supplylines.engine.BuildingRuntime
import com.gliffy.supplylines.engine.GameEngine
import com.gliffy.supplylines.engine.GateDef
import com.gliffy.supplylines.engine.Item

private object Palette {
    val Bg = Color(0xFF0E1116)
    val Panel = Color(0xFF161B22)
    val Grid = Color(0xFF1C232E)
    val LineIdle = Color(0xFF3A4453)
    val LineHot = Color(0xFF7FB2FF)
    val TextMain = Color(0xFFD8DEE9)
    val TextDim = Color(0xFF8A93A3)
    val Coin = Color(0xFFE5C07B)
    val Machine = Color(0xFF4FB8A8)
    val Miner = Color(0xFFE0A458)
    val Storage = Color(0xFF8A93A3)
    val Hub = Color(0xFFE5C07B)
    val Elevator = Color(0xFF9D7CE0)
    val Sink = Color(0xFF6FCF7F)
    val Bench = Color(0xFF66738A)

    fun item(c: Item) = when (c) {
        Item.IRON_ORE, Item.IRON_INGOT, Item.IRON_PLATE, Item.IRON_ROD, Item.IRON_SCREW,
        Item.REINFORCED_IRON_PLATE, Item.IRON_ROTOR, Item.SMART_PLATING -> Color(0xFFE0A458)
        Item.COPPER_ORE, Item.COPPER_INGOT, Item.WIRE, Item.CABLE, Item.AUTOMATED_WIRING -> Color(0xFFD07B4F)
        Item.COAL, Item.STEEL_INGOT, Item.STEEL_BEAM -> Color(0xFF9AA5B4)
        Item.MODULAR_FRAME -> Color(0xFF9D7CE0)
    }

    fun kind(c: BuildingKind) = when (c) {
        BuildingKind.MINER -> Miner
        BuildingKind.SMELTER, BuildingKind.MANUFACTURER, BuildingKind.CONSTRUCTOR,
        BuildingKind.ASSEMBLER, BuildingKind.FOUNDRY -> Machine
        BuildingKind.MANUAL_BENCH -> Bench
        BuildingKind.STORAGE -> Storage
        BuildingKind.HUB -> Hub
        BuildingKind.ELEVATOR -> Elevator
        BuildingKind.SINK -> Sink
    }

    /** Short tag drawn on the cell. */
    fun tag(c: BuildingKind) = when (c) {
        BuildingKind.MINER -> "MNR"
        BuildingKind.SMELTER -> "SML"
        BuildingKind.MANUAL_BENCH -> "BEN"
        BuildingKind.MANUFACTURER -> "MFG"
        BuildingKind.CONSTRUCTOR -> "CON"
        BuildingKind.ASSEMBLER -> "ASM"
        BuildingKind.FOUNDRY -> "FND"
        BuildingKind.STORAGE -> "STO"
        BuildingKind.HUB -> "HUB"
        BuildingKind.ELEVATOR -> "ELV"
        BuildingKind.SINK -> "SNK"
    }
}

private val MONO = FontFamily.Monospace

private fun fmt(n: Double): String {
    val abs = kotlin.math.abs(n)
    return when {
        abs >= 1_000_000_000 -> "%.2fB".format(n / 1_000_000_000)
        abs >= 1_000_000 -> "%.2fM".format(n / 1_000_000)
        abs >= 1_000 -> "%.1fK".format(n / 1_000)
        else -> "%.0f".format(n)
    }
}

@Composable
fun GameScreen(vm: GameViewModel) {
    val engine = vm.engine
    val tick by vm.tick.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }          // building id
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // empty cell
    var connectFrom by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.toast.collect { msg ->
            if (msg != null) {
                toastMsg = msg
                kotlinx.coroutines.delay(4000)
                toastMsg = null
                vm.consumeToast()
            }
        }
    }

    // Map area occupies the top portion; panel below.
    val density = LocalDensity.current
    val panelH = 230
    val mapW = boxSize.width.toFloat()
    val mapH = (boxSize.height - panelH).coerceAtLeast(1).toFloat()
    val cell = minOf(mapW / 11f, mapH / 11f)          // one spare ring of padding
    val originX = (mapW - cell * 10) / 2
    val originY = (mapH - cell * 10) / 2
    fun cellPx(x: Int, y: Int) = Offset(originX + (x + 0.5f) * cell, originY + (y + 0.5f) * cell)
    fun bPx(b: BuildingRuntime) = cellPx(b.x, b.y)

    Column(Modifier.fillMaxSize().background(BgLocal)) {
        // ---- Top bar: coins + throughput ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("COINS", fontFamily = MONO, fontSize = 11.sp, color = Palette.TextDim)
                Text(fmt(engine.state.coins.toDouble()), fontFamily = MONO, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, color = Palette.Coin)
            }
            Spacer(Modifier.width(28.dp))
            Column {
                Text("INCOME", fontFamily = MONO, fontSize = 11.sp, color = Palette.TextDim)
                Text("${fmt(engine.state.cpsAlpha)}/s", fontFamily = MONO, fontSize = 16.sp, color = Palette.TextMain)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showResetDialog = true }) { Text("RESET", fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim) }
        }

        // ---- Map ----
        Box(
            Modifier.fillMaxWidth().withHeightDp((boxSize.height / density.density - panelH / density.density).coerceAtLeast(100f).dp)
                .onSizeChanged { boxSize = it }
                .pointerInput(tick) {
                    detectTapGestures { off ->
                        val gx = ((off.x - originX) / cell).toInt()
                        val gy = ((off.y - originY) / cell).toInt()
                        if (gx in 0 until 10 && gy in 0 until 10) {
                            val hit = engine.state.buildings.values.find { it.x == gx && it.y == gy }
                            if (hit != null) {
                                if (connectFrom != null && connectFrom != hit.id) {
                                    if (vm.tryBuildLine(connectFrom!!, hit.id)) connectFrom = null else vm.toast("Can't link: need coins")
                                    selected = hit.id; selectedCell = null
                                } else { selected = hit.id; selectedCell = null }
                            } else {
                                selectedCell = gx to gy; selected = null
                            }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // grid
                for (i in 0..10) for (j in 0..10) {
                    if (i in 0 until 10 && j in 0 until 10) {
                        val ore = engine.content.oreCells.find { it.x == j && it.y == i }
                        if (ore != null) {
                            drawRoundRect(Palette.item(ore.item).copy(alpha = 0.12f),
                                Offset(originX + j * cell, originY + i * cell),
                                androidx.compose.ui.geometry.Size(cell, cell), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                        }
                    }
                }
                for (i in 0..10) {
                    drawLine(Palette.Grid, Offset(originX + i * cell, originY), Offset(originX + i * cell, originY + 10 * cell), 1f)
                    drawLine(Palette.Grid, Offset(originX, originY + i * cell), Offset(originX + 10 * cell, originY + i * cell), 1f)
                }
                // supply lines
                for (l in engine.state.lines) {
                    val a = engine.building(l.a) ?: continue
                    val b = engine.building(l.b) ?: continue
                    val busy = l.reserved > 0
                    drawLine(if (busy) Palette.LineHot else Palette.LineIdle,
                        bPx(a), bPx(b), if (busy) 3.5f else 2f,
                        pathEffect = if (busy) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                }
                // shipments
                for (sh in engine.state.shipments) {
                    if (sh.edge >= sh.path.size - 1) continue
                    val a = engine.building(sh.path[sh.edge]) ?: continue
                    val b = engine.building(sh.path[sh.edge + 1]) ?: continue
                    val l = engine.line(sh.path[sh.edge], sh.path[sh.edge + 1]) ?: continue
                    val speed = engine.content.speedTiers[l.speedTier]
                    val edgeLen = sqrt(((a.x - b.x).toDouble() * (a.x - b.x)) + ((a.y - b.y).toDouble() * (a.y - b.y)))
                    val t = (sh.progress / (edgeLen / speed)).coerceIn(0.0, 1.0)
                    val p0 = bPx(a); val p1 = bPx(b)
                    drawCircle(Palette.item(sh.item), 7f, Offset(p0.x + (p1.x - p0.x) * t.toFloat(), p0.y + (p1.y - p0.y) * t.toFloat()))
                }
                // buildings
                for (b in engine.state.buildings.values) {
                    val c = bPx(b)
                    val col = Palette.kind(b.kind)
                    val big = b.kind == BuildingKind.HUB || b.kind == BuildingKind.ELEVATOR || b.kind == BuildingKind.SINK
                    val r = if (big) cell * 0.34f else cell * 0.26f
                    val sel = selected == b.id || connectFrom == b.id
                    when (b.kind) {
                        BuildingKind.MINER -> {
                            val p = Path().apply {
                                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close()
                            }
                            drawPath(p, col)
                        }
                        BuildingKind.STORAGE, BuildingKind.HUB -> {
                            val p = Path().apply {
                                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
                            }
                            drawPath(p, if (b.kind == BuildingKind.STORAGE) col.copy(alpha = 0.5f) else col)
                        }
                        BuildingKind.SINK, BuildingKind.ELEVATOR -> drawCircle(col, r, c)
                        else -> drawRoundRect(col, c - Offset(r, r), Size(r * 2, r * 2), androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                    }
                    if (sel) drawCircle(Palette.LineHot, r + 6f, c, style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                    if (b.tier > 0) for (i in 0 until b.tier)
                        drawCircle(col.copy(alpha = 0.7f), r + 10f + i * 7f, c, style = Stroke(width = 1.5f))
                    // tag
                    val ts = androidx.compose.ui.text.TextStyle(color = if (big) col else Palette.TextDim, fontSize = 12.sp, fontFamily = MONO)
                    val lr = measurer.measure(Palette.tag(b.kind), ts)
                    drawText(lr, color = if (big) col else Palette.TextDim,
                        topLeft = Offset(c.x - lr.size.width / 2, c.y + r + 4f))
                }
            }
        }

        // ---- Bottom panel ----
        Box(Modifier.fillMaxWidth().height(230.dp).background(Palette.Panel.copy(alpha = 0.6f)).padding(horizontal = 20.dp, vertical = 12.dp)) {
            val gate = engine.currentGate()
            val selB = selected?.let { engine.building(it) }
            val cellSel = selectedCell
            when {
                selB != null -> BuildingPanel(vm, engine, selB, gate,
                    onConnect = { connectFrom = selB.id; selected = null },
                    onCancelConnect = { connectFrom = null })
                cellSel != null -> BuildMenu(vm, cellSel.first, cellSel.second, onPick = { opt ->
                    if (vm.place(opt.defId, opt.recipeId, cellSel.first, cellSel.second)) {
                        vm.toast("Built ${opt.label}")
                        selectedCell = null
                    } else vm.toast("Can't build here")
                })
                else -> GateSummary(vm, engine)
            }
        }

        toastMsg?.let {
            Box(Modifier.fillMaxWidth().padding(bottom = 240.dp), contentAlignment = Alignment.BottomCenter) {
                Text(it, fontFamily = MONO, fontSize = 13.sp, color = Palette.Coin,
                    modifier = Modifier.background(Palette.Panel.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(onDismissRequest = { showResetDialog = false },
            confirmButton = { Button(onClick = { showResetDialog = false; vm.resetGame() }) { Text("RESET") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("CANCEL") } },
            title = { Text("Reset game?", fontFamily = MONO) },
            text = { Text("Wipes coins, buildings, belts and tech progress.", fontFamily = MONO, fontSize = 13.sp) })
    }
}

private val BgLocal = Palette.Bg

private fun Modifier.withHeightDp(h: androidx.compose.ui.unit.Dp) = this.height(h)

@Composable
private fun GateSummary(vm: GameViewModel, engine: GameEngine) {
    val gate = engine.currentGate()
    Column {
        if (gate == null) {
            Text("ALL PHASES COMPLETE — T4 UNLOCKED", fontFamily = MONO, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = Palette.Elevator)
            Spacer(Modifier.height(6.dp))
            Text("Phase 2 (real game): 1000 Smart Plating, 1000 Modular Frames, 100 Automated Wiring — coming in a later update.",
                fontFamily = MONO, fontSize = 11.sp, color = Palette.TextDim)
        } else {
            Text("TECH TREE · ${gate.name}", fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim)
            Spacer(Modifier.height(6.dp))
            gate.requires.forEach { (item, need) ->
                val have = engine.gateCount(gate.id, item)
                Text("${itemShort(item)}  ${minOf(have, need.toLong())}/$need", fontFamily = MONO, fontSize = 14.sp,
                    color = if (have >= need) Palette.Sink else Palette.TextMain)
            }
            Spacer(Modifier.height(6.dp))
            Text("Deliver to ${gate.name.substringBefore(' ')} via belts · complete to unlock the next tier.",
                fontFamily = MONO, fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}

@Composable
private fun BuildingPanel(vm: GameViewModel, engine: GameEngine, b: BuildingRuntime, gate: GateDef?,
                          onConnect: () -> Unit, onCancelConnect: () -> Unit) {
    val def = engine.def(b.defId)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, fontFamily = MONO, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = Palette.kind(b.kind))
            Spacer(Modifier.width(10.dp))
            Text("MK.${b.tier + 1}", fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim)
        }
        val r = engine.recipe(b.recipe)
        if (r != null) {
            Text(r.inputs.entries.joinToString(" + ") { "${it.value} ${itemShort(it.key)}" } +
                    " → " + r.outputs.entries.joinToString(" + ") { "${it.value} ${itemShort(it.key)}" },
                fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onConnect) { Text("CONNECT", fontFamily = MONO, fontSize = 12.sp) }
            if (def.kind != BuildingKind.STORAGE && b.tier < engine.content.rateMults.size - 1) {
                OutlinedButton(onClick = { vm.tryUpgradeBuilding(b.id) }) {
                    Text("MK.${b.tier + 2} · ${engine.upgradeCost(b.tier)}", fontFamily = MONO, fontSize = 12.sp)
                }
            }
            if (!def.fixed) {
                OutlinedButton(onClick = { vm.sell(b.id) }) { Text("SELL ${def.cost / 2}", fontFamily = MONO, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun BuildMenu(vm: GameViewModel, x: Int, y: Int, onPick: (BuildOption) -> Unit) {
    val opts = vm.buildOptionsFor(x, y)
    Column {
        Text("BUILD ON CELL ($x,$y)", fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim)
        Spacer(Modifier.height(6.dp))
        if (opts.isEmpty()) Text("Nothing can go here.", fontFamily = MONO, fontSize = 13.sp, color = Palette.TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            opts.take(4).forEach { opt ->
                Button(onClick = { onPick(opt) }, enabled = true,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Panel, contentColor = Palette.TextMain)) {
                    Text("${opt.label}\n${opt.cost}¤", fontFamily = MONO, fontSize = 11.sp)
                }
            }
        }
        if (opts.size > 4) {
            Text("+${opts.size - 4} more — scroll list in full build sheet", fontFamily = MONO, fontSize = 10.sp, color = Palette.TextDim)
        }
    }
}

private fun itemShort(i: Item) = when (i) {
    Item.IRON_ORE -> "IRON ORE"
    Item.COPPER_ORE -> "COPPER"
    Item.COAL -> "COAL"
    Item.IRON_INGOT -> "INGOT"
    Item.COPPER_INGOT -> "CU INGOT"
    Item.STEEL_INGOT -> "STEEL"
    Item.STEEL_BEAM -> "BEAM"
    Item.IRON_PLATE -> "PLATE"
    Item.IRON_ROD -> "ROD"
    Item.IRON_SCREW -> "SCREW"
    Item.WIRE -> "WIRE"
    Item.CABLE -> "CABLE"
    Item.REINFORCED_IRON_PLATE -> "RIP"
    Item.IRON_ROTOR -> "ROTOR"
    Item.SMART_PLATING -> "PLATING"
    Item.MODULAR_FRAME -> "FRAME"
    Item.AUTOMATED_WIRING -> "WIRING"
}

@Composable
private fun ColumnScopeSpaced() { Spacer(Modifier.height(6.dp)) }
