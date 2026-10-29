# Supply Lines

A tiny factory-logistics incremental for Android. Set up supply lines between
mines, converters and markets — goods physically travel along your lines, and
lines have capacity, so topology and bottlenecks are the game.

## Core loop
1. **Source → Convert → Sell.** Mines push Ore, refineries turn Ore into
   Parts (fabs turn Parts into Chips), markets pay coins per delivery.
2. **Draw the lines.** Tap a node, hit CONNECT, tap a destination. Each line
   carries a limited number of shipments in transit and goods route via
   shortest path — one thin line into a busy market will bottleneck.
3. **Spend coins on throughput.** Per-line speed and capacity upgrades,
   node output upgrades, and new nodes (locked nodes show their activation
   cost on the map). Idle earnings accrue offline (50% rate, 4h cap).

## Stack
- Kotlin + Jetpack Compose (Canvas-based map), Material 3 dark theme
- `app/src/main/java/.../engine/` is pure Kotlin (no Android imports) — the
  simulation, BFS routing, capacity reservations and economy are unit-tested
  on the JVM (`./gradlew testDebugUnitTest`)
- Save: JSON in filesDir, autosave every 30s + on pause

## Build
```
gradle assembleDebug   # AGP 8.5.2, compileSdk 35, minSdk 26
```

## Status
v0.1.0 — vertical slice: 9 nodes, 3 resource chains, per-line upgrades,
offline earnings. No sound, no animations beyond the moving dots, no
achievements. Playable.
