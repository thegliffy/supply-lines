package com.gliffy.supplylines.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import com.gliffy.supplylines.GameViewModel
import com.gliffy.supplylines.engine.NodeKind
import com.gliffy.supplylines.engine.NodeRuntime
import com.gliffy.supplylines.engine.ResourceKind

private object Palette {
    val Bg = Color(0xFF0E1116)
    val Panel = Color(0xFF161B22)
    val LineIdle = Color(0xFF3A4453)
    val LineHot = Color(0xFF7FB2FF)
    val TextMain = Color(0xFFD8DEE9)
    val TextDim = Color(0xFF8A93A3)
    val Source = Color(0xFFE0A458)
    val Converter = Color(0xFF4FB8A8)
    val Depot = Color(0xFF8A93A3)
    val Market = Color(0xFF6FCF7F)
    val Coin = Color(0xFFE5C07B)

    fun res(c: ResourceKind) = when (c) {
        ResourceKind.ORE -> Source
        ResourceKind.PART -> Converter
        ResourceKind.CHIP -> Color(0xFF9D7CE0)
    }

    fun kind(c: NodeKind) = when (c) {
        NodeKind.SOURCE -> Source
        NodeKind.CONVERTER -> Converter
        NodeKind.DEPOT -> Depot
        NodeKind.MARKET -> Market
    }

    fun shape(c: NodeKind) = when (c) {
        NodeKind.SOURCE -> "SOURCE"
        NodeKind.CONVERTER -> "CONVERTER"
        NodeKind.DEPOT -> "DEPOT"
        NodeKind.MARKET -> "MARKET"
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
    var selected by remember { mutableStateOf<String?>(null) }
    var connectFrom by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.toast.collect { msg ->
            if (msg != null) {
                toastMsg = msg
                delay(4000)
                if (vm.toast.value == msg) vm.consumeToast()
                toastMsg = null
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) vm.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun tapNode(id: String) {
        val from = connectFrom
        if (from != null && from != id) {
            if (!vm.tryBuildLine(from, id)) {
                vm.toast("Need ${engine.lineCost(from, id)} coins to connect")
            }
            connectFrom = null
            selected = null
        } else {
            selected = id
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, tick) { showResetDialog = true }
            MapCanvas(
                vm = vm,
                selected = selected,
                connectFrom = connectFrom,
                onNodeTap = ::tapNode,
                onEmpty = { selected = null; connectFrom = null },
                modifier = Modifier.weight(1f)
            )
            BottomPanel(
                vm = vm,
                tick = tick,
                selected = selected,
                connectFrom = connectFrom,
                onConnectMode = { connectFrom = it },
                onCancelConnect = { connectFrom = null }
            )
        }

        toastMsg?.let { msg ->
            Text(
                msg,
                color = Palette.TextMain,
                fontFamily = MONO,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp, start = 24.dp, end = 24.dp)
                    .background(Palette.Panel, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset game?", color = Palette.TextMain) },
                text = { Text("Sandbox build — all progress is wiped.", color = Palette.TextDim) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.resetGame(); showResetDialog = false; selected = null; connectFrom = null
                    }) { Text("RESET", color = Palette.Market) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("CANCEL", color = Palette.TextDim) }
                },
                containerColor = Palette.Panel
            )
        }
    }
}

@Composable
private fun TopBar(vm: GameViewModel, tick: Int, onReset: () -> Unit) {
    val engine = vm.engine
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("${fmt(engine.state.coins.toDouble())} ⛁", fontFamily = MONO, fontSize = 26.sp,
                fontWeight = FontWeight.Bold, color = Palette.Coin)
            Text("+${fmt(engine.state.cpsAlpha * 60)}/min", fontFamily = MONO, fontSize = 12.sp,
                color = Palette.TextDim)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            val total = engine.state.delivered.values.sum()
            Text("$total sold", fontFamily = MONO, fontSize = 12.sp, color = Palette.TextDim)
            TextButton(onClick = onReset, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("RESET", fontSize = 11.sp, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun MapCanvas(
    vm: GameViewModel,
    selected: String?,
    connectFrom: String?,
    onNodeTap: (String) -> Unit,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = vm.engine
    val tick by vm.tick.collectAsState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val boxSize = remember { mutableStateOf(Offset.Zero) }

    val nodeRadius = with(density) { 22.dp.toPx() }
    val hitRadius = with(density) { 36.dp.toPx() }

    fun nodePx(id: String): Offset {
        val def = engine.nodeDef(id)
        val bs = boxSize.value
        return Offset((def.x * bs.x).toFloat(), (def.y * bs.y).toFloat())
    }

    fun hitTest(pos: Offset): String? {
        var best: String? = null
        var bestD = hitRadius
        for ((id, _) in engine.state.nodes) {
            val p = nodePx(id)
            val d = sqrt((p.x - pos.x) * (p.x - pos.x) + (p.y - pos.y) * (p.y - pos.y))
            if (d <= bestD) { bestD = d; best = id }
        }
        return best
    }

    Box(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onSizeChanged { boxSize.value = Offset(it.width.toFloat(), it.height.toFloat()) }
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { pos ->
                        val id = hitTest(pos)
                        if (id != null) onNodeTap(id) else onEmpty()
                    })
                }
        ) {
            // lines
            for (l in engine.state.lines) {
                val hot = connectFrom != null && (l.a == connectFrom || l.b == connectFrom)
                drawLine(
                    color = if (hot) Palette.LineHot else Palette.LineIdle,
                    start = nodePx(l.a), end = nodePx(l.b),
                    strokeWidth = if (hot) 5.dp.toPx() else 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            // shipments (the dots — this is the fun bit)
            for (sh in engine.state.shipments) {
                val a = nodePx(sh.path[sh.edge])
                val b = nodePx(sh.path[sh.edge + 1])
                val edgeTime = kotlin.math.sqrt(
                    ((engine.nodeDef(sh.path[sh.edge]).x - engine.nodeDef(sh.path[sh.edge + 1]).x)
                        .let { it * it } + (engine.nodeDef(sh.path[sh.edge]).y - engine.nodeDef(sh.path[sh.edge + 1]).y)
                            .let { it * it })
                ) / vm.engine.content.speedTiers[
                    engine.line(sh.path[sh.edge], sh.path[sh.edge + 1])!!.speedTier
                ]
                val t = (sh.progress / edgeTime).coerceIn(0.0, 1.0)
                drawCircle(Palette.res(sh.res), radius = 5.dp.toPx(), center = Offset(
                    (a.x + (b.x - a.x) * t).toFloat(), (a.y + (b.y - a.y) * t).toFloat()
                ))
            }
            // nodes
            for ((id, rt) in engine.state.nodes) {
                val def = engine.nodeDef(id)
                val c = nodePx(id)
                val r = nodeRadius
                val color = Palette.kind(def.kind)
                if (!rt.unlocked) {
                    drawCircle(
                        color = Palette.LineIdle, radius = r, center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                    )
                    drawCompatText(this, measurer, def.name.uppercase(), Palette.TextDim, c,
                        def.unlockCost.toString() + " ⛁", density)
                } else {
                    when (def.kind) {
                        NodeKind.SOURCE -> {
                            val path = Path().apply {
                                moveTo(c.x, c.y - r); lineTo(c.x + r * 0.9f, c.y + r * 0.7f); lineTo(c.x - r * 0.9f, c.y + r * 0.7f); close()
                            }
                            drawPath(path, color)
                        }
                        NodeKind.CONVERTER -> drawCompatRoundRect(this, c, r * 0.85f, color)
                        NodeKind.DEPOT -> {
                            val path = Path().apply {
                                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
                            }
                            drawPath(path, color)
                        }
                        NodeKind.MARKET -> drawCircle(color, radius = r, center = c)
                    }
                    // tier pips
                    for (i in 0 until rt.tier) {
                        drawCircle(Palette.Bg, radius = 3.dp.toPx(),
                            center = Offset(c.x - 8.dp.toPx() + i * 8.dp.toPx(), c.y + r + 8.dp.toPx()))
                    }
                    drawCompatText(this, measurer, def.name.uppercase(), Palette.TextDim, c, null, density)
                }
                if (id == selected || id == connectFrom) {
                    drawCircle(
                        color = if (id == connectFrom) Palette.LineHot else Color.White,
                        radius = r + 6.dp.toPx(), center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPanel(
    vm: GameViewModel,
    tick: Int,
    selected: String?,
    connectFrom: String?,
    onConnectMode: (String) -> Unit,
    onCancelConnect: () -> Unit
) {
    val engine = vm.engine
    Column(
        Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 16.dp, vertical = 12.dp)
            .height(172.dp)
    ) {
        val id = connectFrom ?: selected
        if (id == null) {
            Text("SUPPLY LINES", fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextMain)
            Spacer(Modifier.height(6.dp))
            Text("Tap a node → connect mines to converters, converters to markets. Lines carry limited cargo; upgrades raise throughput. Tapping another node while a node is selected builds a line.",
                fontSize = 12.sp, color = Palette.TextDim, lineHeight = 16.sp)
            return@Column
        }

        val def = engine.nodeDef(id)
        val rt = engine.runtime(id)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name.uppercase(), fontFamily = MONO, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = Palette.kind(def.kind))
            Spacer(Modifier.width(8.dp))
            Text("· ${Palette.shape(def.kind)}", fontFamily = MONO, fontSize = 11.sp, color = Palette.TextDim)
            Spacer(Modifier.weight(1f))
            if (!rt.unlocked) {
                Button(onClick = { if (!vm.tryUnlock(id)) vm.toast("Not enough coins") },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Market, contentColor = Palette.Bg)) {
                    Text("ACTIVATE ${def.unlockCost} ⛁", fontSize = 12.sp, fontFamily = MONO)
                }
            } else {
                OutlinedButton(onClick = { if (connectFrom == id) onCancelConnect() else onConnectMode(id) }) {
                    Text(if (connectFrom == id) "TAP TARGET" else "CONNECT", fontSize = 12.sp, fontFamily = MONO,
                        color = if (connectFrom == id) Palette.LineHot else Palette.TextMain)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (rt.unlocked) {
            if (def.rate > 0.0 && def.produce != null) {
                val mults = engine.content.nodeRateMults
                val rate = def.rate * mults[minOf(rt.tier, mults.lastIndex)]
                Text("Outputs ${Palette.shape(def.kind).let { "" }}${def.produce} at ${"%.1f".format(rate)}/s · tier ${rt.tier}",
                    fontFamily = MONO, fontSize = 12.sp, color = Palette.TextMain)
                if (rt.tier < mults.lastIndex) {
                    TextButton(onClick = { if (!vm.tryUpgradeNode(id)) vm.toast("Not enough coins") }) {
                        Text("OUTPUT x2 — ${engine.nodeUpgCost(id)} ⛁", fontSize = 12.sp, fontFamily = MONO,
                            color = Palette.Coin)
                    }
                }
            }
            if (def.wants.isNotEmpty()) {
                Text(def.wants.entries.joinToString(" · ") { (res, _) ->
                    "${def.pay[res]?.let { "sells $res @ ${it}⛁" } ?: "takes $res"}"
                }, fontFamily = MONO, fontSize = 12.sp, color = Palette.Market)
            }
            HorizontalDivider(color = Palette.LineIdle, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            val lines = engine.state.lines.filter { it.a == id || it.b == id }
            if (lines.isEmpty()) {
                Text("No supply lines yet.", fontSize = 12.sp, color = Palette.TextDim)
            }
            for (l in lines) {
                val other = if (l.a == id) l.b else l.a
                val capTable = engine.content.capTiers
                val spdTable = engine.content.speedTiers
                val cap = capTable[minOf(l.capTier, capTable.lastIndex)]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("→ ${engine.nodeDef(other).name.uppercase()} [${l.reserved}/$cap] spd ${"%.2f".format(spdTable[minOf(l.speedTier, spdTable.lastIndex)])}",
                        fontFamily = MONO, fontSize = 11.sp, color = Palette.TextMain)
                    Spacer(Modifier.weight(1f))
                    val spdCost = engine.lineSpeedUpgCost(l)
                    val capCost = engine.lineCapUpgCost(l)
                    if (spdCost != null) SmallUpButton("SPD ${spdCost}") { if (!vm.tryUpgradeLineSpeed(id, other)) vm.toast("Not enough coins") }
                    if (capCost != null) SmallUpButton("CAP ${capCost}") { if (!vm.tryUpgradeLineCap(id, other)) vm.toast("Not enough coins") }
                }
            }
        }
    }
}

@Composable
private fun SmallUpButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(28.dp)
    ) { Text(label, fontSize = 10.sp, fontFamily = MONO, color = Palette.Coin) }
}

// ---- canvas text helpers ----

private fun drawCompatText(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    measurer: androidx.compose.ui.text.TextMeasurer,
    label: String,
    color: Color,
    c: Offset,
    sub: String?,
    density: androidx.compose.ui.unit.Density
) {
    val style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 11.sp, fontFamily = MONO)
    val layout = measurer.measure(label, style)
    scope.drawText(measurer, label, Offset(c.x - layout.size.width / 2f, c.y + 26f * density.density), style)
    if (sub != null) {
        val subStyle = androidx.compose.ui.text.TextStyle(color = Palette.Coin, fontSize = 12.sp, fontFamily = MONO)
        val subLayout = measurer.measure(sub, subStyle)
        scope.drawText(measurer, sub, Offset(
            c.x - subLayout.size.width / 2f,
            c.y + 26f * density.density + layout.size.height + 2f
        ), subStyle)
    }
}

private fun drawCompatRoundRect(scope: androidx.compose.ui.graphics.drawscope.DrawScope, c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                androidx.compose.ui.geometry.CornerRadius(r * 0.3f, r * 0.3f)
            )
        )
    }
    scope.drawPath(path, color)
}


