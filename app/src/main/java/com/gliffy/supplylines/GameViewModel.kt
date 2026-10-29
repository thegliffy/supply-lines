package com.gliffy.supplylines

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.lifecycle.viewModelScope
import com.gliffy.supplylines.engine.BuildingDef
import com.gliffy.supplylines.engine.Content
import com.gliffy.supplylines.engine.GameEngine
import com.gliffy.supplylines.engine.GateDef
import com.gliffy.supplylines.engine.WorldState
import java.io.File

/** What the build menu can offer for a given empty cell. */
data class BuildOption(val defId: String, val recipeId: String?, val label: String, val cost: Long)

/**
 * Owns the engine + the tick loop, runs at 10 Hz on the main thread (the
 * engine is pure CPU-light simulation). Persistence is a JSON file in
 * filesDir; autosaves every 30s and whenever the app is paused.
 */
class GameViewModel(app: Application) : androidx.lifecycle.AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true }
    private val saveFile = File(app.filesDir, "save.json")

    val engine: GameEngine
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    init {
        val loaded = if (saveFile.exists()) {
            runCatching { json.decodeFromString(WorldState.serializer(), saveFile.readText()) }.getOrNull()
        } else null

        engine = GameEngine(Content.DEFAULT, loaded)

        if (loaded != null) {
            val elapsed = System.currentTimeMillis() / 1000 - loaded.lastSaveEpochSec
            if (elapsed > 60) {
                val grant = engine.offlineEarnings(elapsed.toDouble())
                if (grant > 0) _toast.value = "While you were away: +$grant coins"
            }
        }

        engine.gateCompleted = { g -> _toast.value = "${g.name} COMPLETE — new recipes unlocked" }

        viewModelScope.launch {
            var sinceSave = 0
            while (true) {
                delay(100)
                engine.tick(0.1)
                _tick.value++
                sinceSave++
                if (sinceSave >= 300) { sinceSave = 0; saveNow() }
            }
        }
    }

    fun saveNow() {
        ioScope.launch {
            runCatching {
                engine.state.lastSaveEpochSec = System.currentTimeMillis() / 1000
                saveFile.writeText(json.encodeToString(WorldState.serializer(), engine.state))
            }
        }
    }

    fun consumeToast() { _toast.value = null }

    fun resetGame() {
        saveFile.delete()
        val fresh = GameEngine(Content.DEFAULT)
        copyState(fresh, engine)
        _toast.value = "Game reset"
    }

    private fun copyState(from: GameEngine, to: GameEngine) {
        // engine.state is val; copy fields in.
        val t = to.state
        val s = from.state
        t.coins = s.coins; t.buildings.clear(); t.buildings.putAll(s.buildings)
        t.lines.clear(); t.lines.addAll(s.lines); t.shipments.clear(); t.shipments.addAll(s.shipments)
        t.gateProgress.clear(); t.gateProgress.putAll(s.gateProgress); t.gatesDone.clear(); t.gatesDone.addAll(s.gatesDone)
        t.earnedTotal = s.earnedTotal
        t.cpsAlpha = s.cpsAlpha
    }

    // ---- Actions (called by UI; engine decides legality) ----

    fun buildOptionsFor(x: Int, y: Int): List<BuildOption> {
        val out = mutableListOf<BuildOption>()
        for ((defId, d) in engine.content.defs) {
            if (d.fixed) continue
            if (!engine.canPlace(defId, x, y)) continue
            val recipes = engine.recipesFor(defId)
            if (recipes.isEmpty() && d.kind != com.gliffy.supplylines.engine.BuildingKind.MINER &&
                d.kind != com.gliffy.supplylines.engine.BuildingKind.STORAGE) continue
            when {
                recipes.isNotEmpty() -> recipes.forEach { r ->
                    out.add(BuildOption(defId, r.id, "${d.name} · ${r.id.substringAfter('_').uppercase()}", d.cost))
                }
                else -> out.add(BuildOption(defId, null, d.name, d.cost))
            }
        }
        return out.sortedBy { it.cost }
    }

    fun place(defId: String, recipeId: String?, x: Int, y: Int): Boolean {
        val id = engine.place(defId, x, y, recipeId) ?: return false
        return id.isNotEmpty()
    }

    fun sell(id: String): Boolean = engine.sell(id)
    fun tryBuildLine(a: String, b: String): Boolean = engine.buildLine(a, b)
    fun tryUpgradeBuilding(id: String): Boolean = engine.upgradeBuilding(id)
    fun tryUpgradeLineSpeed(a: String, b: String): Boolean =
        engine.line(a, b)?.let { engine.upgradeLineSpeed(it) } ?: false
    fun tryUpgradeLineCap(a: String, b: String): Boolean =
        engine.line(a, b)?.let { engine.upgradeLineCap(it) } ?: false

    fun currentGate(): GateDef? = engine.currentGate()
    fun defFor(defId: String): BuildingDef = engine.def(defId)

    fun toast(msg: String) { _toast.value = msg }
}
