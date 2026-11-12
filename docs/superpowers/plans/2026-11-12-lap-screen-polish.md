# Lap Screen Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reclaim vertical space on the lap screen and add the reviewed playback, telemetry, marker, and replay-management affordances.

**Architecture:** Keep all changes in the Compose UI layer plus `SettingsStore`. Pure detection/formatting helpers live in small files with unit tests; composables stay dumb and read those helpers. The lap screen becomes a fixed header, a scrolling content area, and a sticky playback footer containing the ruler.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Media3 ExoPlayer 1.5.1, Robolectric + Compose UI tests, JUnit 4.

**Spec:** `docs/specs/2026-11-10-pocket-sim-engineer.md`

## Global Constraints

- Telemetry-only: no MediaProjection, screen capture, phone audio, Chiaki/Remote Play, or burned-in overlays. An on-screen HUD is allowed.
- Local-first: no accounts, analytics, or background cloud calls.
- Package `io.github.jlai403.pocketsimengineer`; `minSdk 29`, `targetSdk 36`.
- One lap per clip; replay optional and replaceable; one anchor at lap end using `lastLapTimeMs`.
- Frame ruler direction: drag right rewinds, drag left moves forward.
- Frame-ruler haptics remain optional.
- Full check: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Quick compile: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:compileDebugKotlin`.

## Review Focus

- Empty telemetry window: charts, HUD, sparklines, tire heatmap, and markers must render without crashing or dividing by zero.
- Fast ruler drags with markers/progress enabled: must not add per-frame allocations beyond today's path or drop the coalesced seek behaviour.
- Lap shorter than 10 s: ±5 s sparkline and HUD windows must clamp to lap bounds.
- Playback at 0.5×/2×: cursor and frame stepping must stay frame-accurate; speed must not change seek targets.
- Edit lap opened while a replacement import is pending: saving track/driver must not clear `sourceFile`, and menu actions on a lap with no video must be disabled.

---

### Task 1: Playback preferences and settings controls

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/settings/SettingsStore.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreenTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControlsTest.kt`

**Interfaces:**
- Produces:
  - `SettingsStore.defaultPlaybackSpeed: Float` (get/set, default `1f`)
  - `SettingsStore.telemetryHud: Boolean` (get/set, default `true`)
  - `internal val PLAYBACK_SPEEDS: List<Float>` = `listOf(0.5f, 1f, 1.5f, 2f)`
  - `internal fun playbackSpeedLabel(speed: Float): String` (`"0.5×"`, `"1×"`, `"1.5×"`, `"2×"`)

- [ ] **Step 1: Write the failing tests**

Add to `SettingsScreenTest`:
```kotlin
@Test
fun `default playback speed and hud persist`() {
    SettingsStore(context).defaultPlaybackSpeed = 1f
    SettingsStore(context).telemetryHud = true
    compose.setContent { PocketSimEngineerTheme { SettingsScreen() } }

    compose.onNodeWithTag("default-playback-speed").performScrollTo().performClick()
    compose.onNodeWithText("2×").performClick()

    compose.onNodeWithTag("telemetry-hud").performScrollTo().performClick()

    assertEquals(2f, SettingsStore(context).defaultPlaybackSpeed)
    assertFalse(SettingsStore(context).telemetryHud)
}
```
Add to `ReplayControlsTest`:
```kotlin
@Test
fun `playback speed labels drop trailing zeros`() {
    assertEquals("0.5×", playbackSpeedLabel(0.5f))
    assertEquals("1×", playbackSpeedLabel(1f))
    assertEquals("1.5×", playbackSpeedLabel(1.5f))
    assertEquals("2×", playbackSpeedLabel(2f))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest' --tests '*ReplayControlsTest'`
Expected: FAIL — unresolved `defaultPlaybackSpeed`, `telemetryHud`, `playbackSpeedLabel`.

- [ ] **Step 3: Implement the preferences and settings UI**

`SettingsStore`: add both properties with keys `default_playback_speed` and `telemetry_hud`.

`ReplayControls.kt`:
```kotlin
internal val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)

internal fun playbackSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) "%.0f×".format(speed) else "%.1f×".format(speed)
```

`SettingsScreen` Display card: add a row with `Text("Default playback speed")`, a `GhostPill(playbackSpeedLabel(defaultSpeed), trailingIcon = Icons.Filled.KeyboardArrowDown, modifier = Modifier.testTag("default-playback-speed"))`, and a `DropdownMenu` over `PLAYBACK_SPEEDS` that updates local state and `settings.defaultPlaybackSpeed`. Add `SettingToggle(label = "Telemetry HUD", checked = hud, modifier = Modifier.testTag("telemetry-hud")) { hud = it; settings.telemetryHud = it }`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest' --tests '*ReplayControlsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/settings/SettingsStore.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreenTest.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControlsTest.kt
git commit -m "feat: add playback speed and HUD settings"
```

---

### Task 2: Lap event detection

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapEvents.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapEventsTest.kt`

**Interfaces:**
- Produces:
  - `enum class LapEventType { Brake, Coast, Throttle, Turn }`
  - `data class LapEvent(val type: LapEventType, val timeMs: Int)`
  - `internal fun lapEvents(window: List<Sample>, lap: LapMetadata): List<LapEvent>` — sorted by `timeMs`, at most one event per type per 600 ms, using brake/throttle unlocks and steering-magnitude peaks.

- [ ] **Step 1: Write the failing tests**

`LapEventsTest` (build samples with `SessionFixtures.sample(...)`, adding a `steering` argument to the fixture in this task):
```kotlin
@Test
fun `a braking zone marks its onset once`() {
    val window = samples(
        1_000 to 0, 1_100 to 200, 1_200 to 220, 1_300 to 210, 1_400 to 0,
    )
    val brakes = lapEvents(window, lap).filter { it.type == LapEventType.Brake }
    assertEquals(listOf(LapEvent(LapEventType.Brake, 1_100)), brakes)
}

@Test
fun `coasting marks where both pedals are released`() {
    val window = samples(
        1_000 to 200, 1_100 to 0, 1_200 to 0, 1_300 to 0,
    )
    assertTrue(lapEvents(window, lap).any { it.type == LapEventType.Coast && it.timeMs == 1_100 })
}

@Test
fun `a steering peak marks a turn`() {
    val window = steeringSamples(1_000 to 0f, 1_100 to 0.2f, 1_200 to 0.6f, 1_300 to 0.2f)
    val turns = lapEvents(window, lap).filter { it.type == LapEventType.Turn }
    assertEquals(listOf(LapEvent(LapEventType.Turn, 1_200)), turns)
}

@Test
fun `events are sorted by time`() {
    val window = steeringSamples(1_000 to 0.6f, 2_000 to 0f)
    val times = lapEvents(window, lap).map { it.timeMs }
    assertEquals(times.sorted(), times)
}
```
Add these helpers at the bottom of `LapEventsTest`:
```kotlin
private fun samples(vararg brake: Pair<Int, Int>): List<Sample> = brake.map { (ms, value) ->
    SessionFixtures.sample(timeNanos = ms * 1_000_000L, brake = value, throttle = 0, speedMps = 20f)
}

private fun steeringSamples(vararg points: Pair<Int, Float>): List<Sample> =
    points.map { (ms, steer) ->
        SessionFixtures.sample(timeNanos = ms * 1_000_000L, brake = 0, throttle = 0, speedMps = 20f)
            .copy(steering = steer)
    }

private val lap = LapMetadata(
    index = 1,
    lapTimeMs = 5_000,
    startNanos = 0,
    endNanos = 5_000_000_000L,
    sampleCount = 0,
    videoFile = "",
    leadInMs = 0,
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapEventsTest'`
Expected: FAIL — unresolved `lapEvents`.

- [ ] **Step 3: Implement `LapEvents.kt`**

Thresholds: `BRAKE_ON = 60`, `THROTTLE_ON = 60`, `COAST_LIMIT = 20`, `TURN_STEERING = 0.35f`, `MIN_EVENT_GAP_MS = 600`. Onsets fire when a condition becomes true; coast requires `brake < 20 && throttle < 20 && speedMps > 3f`; turn fires at a local maximum of `abs(steering)` at or above `0.35f`. Track `lastTime` per type and skip an event when `timeMs - lastTime < 600`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapEventsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapEvents.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapEventsTest.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SessionFixtures.kt
git commit -m "feat: detect brake, coast, throttle, and turn markers"
```

---

### Task 3: Telemetry window helper and tire heatmap

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TireHeatmap.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiStateTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TireHeatmapTest.kt`

**Interfaces:**
- Produces:
  - `fun List<Sample>.windowAround(lap: LapMetadata, timeMs: Int, radiusMs: Int): List<Sample>`
  - `internal fun tireTemperatureColor(celsius: Float): Color` — `<60 °C` cold blue, `60–90 °C` blue→green, `90–120 °C` green→red, `>120 °C` red.
  - `@Composable internal fun TireHeatmap(fl: Float, fr: Float, rl: Float, rr: Float, imperial: Boolean, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing tests**

`UiStateTest` (reuse the existing `lap` field, which starts at `startNanos = 0`):
```kotlin
@Test
fun `window around keeps samples within the radius`() {
    val window = listOf(0, 1_000, 2_000, 3_000, 4_000, 5_000)
        .map { ms -> SessionFixtures.sample(timeNanos = ms * 1_000_000L) }
        .windowAround(lap, 3_000, 1_500)
    assertEquals(listOf(2_000L, 3_000L, 4_000L), window.map { it.timeNanos / 1_000_000 })
}

@Test
fun `window around an empty lap is empty`() {
    assertTrue(emptyList<Sample>().windowAround(lap, 0, 5_000).isEmpty())
}
```
`TireHeatmapTest`:
```kotlin
@Test
fun `tire color anchors are cold, optimal, and hot`() {
    assertEquals(AccentBlue, tireTemperatureColor(40f))
    assertEquals(AccentGreen, tireTemperatureColor(90f))
    assertEquals(Color(0xFFFF6B6B), tireTemperatureColor(130f))
}
```
(Exact anchor colors: reuse `AccentBlue`, `AccentGreen`, and `MaterialTheme.error`'s literal `0xFFFF6B6B`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*UiStateTest' --tests '*TireHeatmapTest'`
Expected: FAIL — unresolved `windowAround`, `tireTemperatureColor`.

- [ ] **Step 3: Implement the helper and heatmap**

`windowAround` filters `abs(lapTimeMs(lap) - timeMs) <= radiusMs`.

`TireHeatmap.kt`: use `androidx.compose.ui.graphics.lerp` for the gradient. Render a 2×2 grid (FL/FR over RL/RR), each tile ≈44 dp, background `tireTemperatureColor(celsius)`, label (`"FL"`) and temperature (`"%.0f°"`), converting to Fahrenheit when `imperial` (`c * 9 / 5 + 32`). Use black text at 70% alpha for contrast.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*UiStateTest' --tests '*TireHeatmapTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TireHeatmap.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiStateTest.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TireHeatmapTest.kt
git commit -m "feat: add telemetry windows and tire heatmap colors"
```

---

### Task 4: Charts, readout bars, sparklines, and map markers

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryCharts.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetrySparkline.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryChartsTest.kt`

**Interfaces:**
- Consumes: `windowAround`, `tireTemperatureColor`, `TireHeatmap`, `lapEvents`.
- Produces:
  - `@Composable internal fun TelemetrySparkline(window: List<Sample>, lap: LapMetadata, valueOf: (Sample) -> Float, maxValue: Float, color: Color, modifier: Modifier = Modifier)`
  - `ChartColumn` keeps its current signature; internally renders speed, a combined throttle/brake chart, RPM, gear, value bars, sparklines, and the tire heatmap.
  - `TrackMap` keeps its current signature; internally detects `lapEvents(window, lap)` and draws colored dots.

- [ ] **Step 1: Write the failing test**

`TelemetryChartsTest` (Robolectric, same pattern as `LapPlayerScreenTest`), with:
```kotlin
private val lap = LapMetadata(
    index = 1,
    lapTimeMs = 90_000,
    startNanos = 0,
    endNanos = 90_000_000_000L,
    sampleCount = 2,
    videoFile = "",
    leadInMs = 0,
)
private val samples = listOf(
    SessionFixtures.sample(0, brake = 0, throttle = 200, x = 0f, z = 0f),
    SessionFixtures.sample(90_000_000_000L, brake = 200, throttle = 0, x = 100f, z = 100f),
)
```
```kotlin
@Test
fun `telemetry shows the combined pedal chart and tire labels`() {
    compose.setContent {
        PocketSimEngineerTheme {
            ChartColumn(samples = samples, lap = lap, cursorMs = 1_000)
        }
    }
    compose.onNodeWithText("Throttle / Brake").assertExists()
    compose.onNodeWithText("FL").assertExists()
    compose.onNodeWithText("RR").assertExists()
}

@Test
fun `telemetry with no samples says so`() {
    compose.setContent {
        PocketSimEngineerTheme {
            ChartColumn(samples = emptyList(), lap = lap, cursorMs = 0)
        }
    }
    compose.onNodeWithText("No telemetry for this lap").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*TelemetryChartsTest'`
Expected: FAIL — missing "Throttle / Brake" / tire labels.

- [ ] **Step 3: Implement the readout and charts**

- Refactor `TraceChart` to accept `series: List<TraceSeries>` where `TraceSeries(color, valueOf)`; draw each path then the cursor once.
- Replace separate throttle/brake charts with one `TraceChart(label = "Throttle / Brake", maxValue = 255f, series = throttle + brake)`.
- Readout: `Row` with speed and gear (both `NumericText`); then a throttle row (`Text("THR")`, 6 dp value bar, `TelemetrySparkline` 40×16 dp, percent), a brake row (same, red), and an RPM row (amber bar scaled to `window.maxOf { it.rpm }`, numeric RPM). Use a private `ValueBar(fraction, color)` built from a `Box` track + fill.
- Add `TireHeatmap(...)` under the RPM row using the interpolated sample.
- `TrackMap`: compute `val events = remember(window, lap) { lapEvents(window, lap) }`; map each event with `window.sampleAt(lap, event.timeMs)` and `mapPoint`; draw a 4 dp dot in the event color (brake = `error`, coast = `onSurfaceVariant`, throttle = `primary`, turn = `tertiary`). Add `MarkerLegend()` under the map with the same colors and labels.

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*TelemetryChartsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryCharts.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetrySparkline.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryChartsTest.kt
git commit -m "feat: combine pedal charts and add telemetry bars and markers"
```

---

### Task 5: Ruler progress, event ticks, and compact height

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/FrameRulerTest.kt`

**Interfaces:**
- Produces: `FrameRuler(..., events: List<LapEvent> = emptyList(), showProgress: Boolean = false)`; height becomes `52.dp`.
- Consumes: `LapEvent`, `LapEventType`.

- [ ] **Step 1: Write the failing test**

`FrameRulerTest`:
```kotlin
@Test
fun `ruler renders progress and event markers`() {
    compose.setContent {
        PocketSimEngineerTheme {
            FrameRuler(
                valueMs = 5_000,
                startMs = 0,
                endMs = 10_000,
                frameStepMs = 33,
                enabled = true,
                onScrubStart = {},
                onScrub = {},
                events = listOf(LapEvent(LapEventType.Brake, 2_000)),
                showProgress = true,
                hapticsEnabled = false,
                modifier = Modifier.testTag("ruler"),
            )
        }
    }
    compose.onNodeWithTag("ruler").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*FrameRulerTest'`
Expected: FAIL — no `events`/`showProgress` parameters.

- [ ] **Step 3: Implement the ruler additions**

- Change the Canvas height to `52.dp` and lift the label baseline so labels stay inside.
- When `showProgress`, draw a 4 dp rounded track from `0` to `fraction * size.width` at the bottom edge (`fraction = (valueMs - startMs) / (endMs - startMs)`).
- For each event inside `startMs..endMs`, compute `x` from `valueMs`/`pxPerMs` and draw a 2 dp dot at `y = 4.dp`, color by type (brake `error`, coast `onSurfaceVariant`, throttle `primary`, turn `tertiary`).

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*FrameRulerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/FrameRulerTest.kt
git commit -m "feat: add ruler progress and event markers"
```

---

### Task 6: Sticky playback footer and compact transport

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt`

**Interfaces:**
- Produces: `@Composable internal fun PlaybackFooter(cursorMs: Int, lapTimeMs: Int, frameStepMs: Long, enabled: Boolean, playing: Boolean, speed: Float, events: List<LapEvent>, hapticsEnabled: Boolean, onScrubStart: () -> Unit, onScrub: (Int) -> Unit, onScrubEnd: () -> Unit, onRewind: () -> Unit, onPlayPause: () -> Unit, onForward: () -> Unit, onSpeedChange: (Float) -> Unit)`
- Consumes: `FrameRuler`, `PLAYBACK_SPEEDS`, `playbackSpeedLabel`, `formatMillis`, `lapTimeLabel`.

- [ ] **Step 1: Write the failing tests**

Update `LapPlayerScreenTest` `lap with a clip does not ask for a replay` and add a `@Before` that resets playback preferences:
```kotlin
@Before
fun resetPlaybackPreferences() {
    SettingsStore(context).defaultPlaybackSpeed = 1f
    SettingsStore(context).telemetryHud = true
}
```
```kotlin
compose.onNodeWithText("Add replay").assertDoesNotExist()
compose.onNodeWithContentDescription("Frame ruler").assertExists()
compose.onNodeWithText("−1 frame").assertDoesNotExist()
compose.onNodeWithText("+1 frame").assertDoesNotExist()
compose.onNodeWithText("1×").assertExists()
```
Add:
```kotlin
@Test
fun `the speed menu offers playback rates`() {
    SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
    compose.setContent {
        PocketSimEngineerTheme {
            LapPlayerScreen("session-1", 1, onBack = {}, onImportSync = {})
        }
    }
    compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("1×").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("1×").performClick()
    compose.onNodeWithText("2×").assertExists()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: FAIL — no `1×`, still finds frame-step buttons.

- [ ] **Step 3: Implement the sticky footer**

- In `ReplayControls.kt`, make `TransportButton` accept `size: Dp` (default current sizes) so the footer can use 44 dp primary / 36 dp secondary. Add `PlaybackFooter` as a `Column` with a top `HorizontalDivider(Hairline)`, `surface` background, 8 dp padding:
  - time row: `NumericText(formatMillis(cursorMs) + " / " + lapTimeLabel(lapTimeMs))`.
  - `FrameRuler(showProgress = true, events = events, hapticsEnabled = hapticsEnabled, ...)`.
  - transport row: rewind, play/pause, forward, then `Spacer(weight(1f))` and a `SpeedPill(speed, onSpeedChange)` (a `GhostPill` showing `playbackSpeedLabel(speed)` with a `DropdownMenu` over `PLAYBACK_SPEEDS`, testTag `speed-menu`).
- In `LapPlayerScreen`: remove `Slider`, the `0:00 / lap` label `Row`, and the `FrameStepRow` call. Restructure the root `Column` so `ScreenHeader` and the scrolling content are padded, but `PlaybackFooter` is full-bleed at the bottom. The scrolling content gets `Modifier.weight(1f)` and bottom padding; the footer renders only when `videoFile != null`.
- Add `var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }` and `LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }`. Footer speed changes update only the state (session-only).
- Compute `val events = remember(samples, currentLap) { lapEvents(samples.lapWindow(currentLap), currentLap) }`.
- Remove now-unused `stepFrames`/`seekFramesFrom` locals.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplayControls.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt
git commit -m "feat: pin playback controls and ruler to a footer"
```

---

### Task 7: Header overflow menu, edit sheet, and driver subtitle

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt`

**Interfaces:**
- Consumes: `EditLapSheet`, `TrackNamer`, `TrackCatalog`, `TrackCandidate`, `DriverProfileStore`, `countryFlagEmoji`, `LapSummary`.
- Produces: `ScreenHeader` actions = a `MoreVert` `DropdownMenu` with `Share video` (disabled when `videoFile == null`), `Export CSV`, and `Edit lap`; subtitle = `"Track · 🇬🇧 Name"` when a driver is assigned.

- [ ] **Step 1: Write the failing tests**

Update existing tests that assert inline `Replace replay` / `Driver` (add imports for `DriverProfile` and `DriverProfileStore`):
```kotlin
@Test
fun `edit lap opens from the header and offers replay replacement`() {
    SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
    compose.setContent { PocketSimEngineerTheme { LapPlayerScreen("session-1", 1, {}, {}) } }
    compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithContentDescription("More").performClick()
    compose.onNodeWithText("Edit lap").performClick()

    compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText("Replace replay").fetchSemanticsNodes().isNotEmpty()
    }
}

@Test
fun `the lap header shows the assigned driver`() {
    DriverProfileStore(context).setOwner(DriverProfile("skino2024", "Skino", "GB"))
    SessionFixtures.install(
        context,
        SessionFixtures.session(lapTimesMs = listOf(90_000), driverPsnId = "skino2024"),
    )
    compose.setContent { PocketSimEngineerTheme { LapPlayerScreen("session-1", 1, {}, {}) } }
    compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText("Suzuka · 🇬🇧 Skino").fetchSemanticsNodes().isNotEmpty()
    }
}
```
Remove the `Replace replay` assertion from `lap with a clip does not ask for a replay` and the `Driver` section assertions (if any). Keep the `Resume sync` / pending tests, but expect them inside the edit sheet.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: FAIL — no `More`, no `Edit lap`, no driver subtitle.

- [ ] **Step 3: Implement the header menu and edit sheet**

- Add `var menuOpen`, `var editing`, `var candidates` state; `val profileStore = remember { DriverProfileStore(context) }`; `val namer = remember { TrackNamer(store, TrackCatalog(context)) }`.
- Load `val summary by produceState<LapSummary?>(null, sessionId, lapIndex, sessionReload) { value = withContext(Dispatchers.IO) { store.summaries().firstOrNull { it.sessionId == sessionId && it.lapIndex == lapIndex } } }`.
- Derive `driverProfile = currentLap.driverPsnId?.let { profileStore.profile(it) } ?: profileStore.owner()`, `driverLabel = listOfNotNull(countryFlagEmoji(it.countryCode), it.nickname).joinToString(" ")`, and `subtitle = listOfNotNull(session?.trackLabel, driverLabel).joinToString(" · ").ifBlank { null }`.
- Header actions: `IconButton` with `Icons.Filled.MoreVert` (`contentDescription = "More"`) + `DropdownMenu` with `Share video` (calls `Sharing.shareFile`, disabled when no video), `Export CSV`, `Edit lap` (calls `openEdit()`).
- `openEdit()` sets `editing = true`, clears `candidates`, and when `session?.trackLabel == TrackNamer.UNKNOWN` loads `namer.candidates(sessionId, lapIndex)`.
- Render `EditLapSheet` when `editing && summary != null`, wiring `onSaveTrack` (`store.rename`), `onAssignDriver` (`store.setLapDriver`, `profileStore.save`, `sessionReload++`), `onAddReplay` (`importLauncher.launch`), `onOpenPlayStation`, `onResumeSync` (`onImportSync(null)`).
- Remove the inline `Replace replay` pill and the `DriverSection` call. Keep the compact `Add replay` source pill when no replay exists; keep a `Resume sync` pill only for a pending import.
- Update `a pending import can be resumed or replaced` and `an existing clip survives a pending replacement import` to open `Edit lap` and assert `Resume sync` / `Replace replay` / the pending text inside the sheet.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt
git commit -m "feat: move lap edits into a header sheet"
```

---

### Task 8: Telemetry HUD over the replay

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryHud.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt`

**Interfaces:**
- Consumes: `windowAround`, `sampleAt`, `TelemetrySparkline`, `SettingsStore.telemetryHud`.
- Produces: `@Composable internal fun TelemetryHud(window: List<Sample>, lap: LapMetadata, cursorMs: Int, imperial: Boolean, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// LapPlayerScreenTest already resets playback preferences in @Before from Task 6.
@Test
fun `the telemetry hud shows over the replay by default`() {
    SettingsStore(context).telemetryHud = true
    SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
    compose.setContent { PocketSimEngineerTheme { LapPlayerScreen("session-1", 1, {}, {}) } }
    compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().isNotEmpty()
    }
}

@Test
fun `the hud stays hidden when disabled in settings`() {
    SettingsStore(context).telemetryHud = false
    SessionFixtures.install(context, SessionFixtures.session(lapTimesMs = listOf(90_000)))
    compose.setContent { PocketSimEngineerTheme { LapPlayerScreen("session-1", 1, {}, {}) } }
    compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Lap 1 · 1:30.000").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, compose.onAllNodesWithTag("telemetry-hud").fetchSemanticsNodes().size)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: FAIL — no `telemetry-hud` node.

- [ ] **Step 3: Implement the HUD**

- `TelemetryHud`: `val window = remember(window, lap, cursorMs) { windowAround(lap, cursorMs, 5_000) }`; `val current = window.sampleAt(lap, cursorMs)`. Render a `Column` with `Modifier.testTag("telemetry-hud").clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)).padding(8.dp)`: a `Row` with speed (`NumericText`) and `Gear N`, then a `Row` with two `TelemetrySparkline`s (throttle green, brake red) at 64×16 dp. Label them `THR` / `BRK` in `labelSmall`.
- In `LapPlayerScreen`, wrap the `PlayerView` in a `Box` (already present) and add `if (settings.telemetryHud && currentLap != null) TelemetryHud(..., modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))` inside that `Box`.
- Guard empty samples inside `TelemetryHud`: if `window.isEmpty() || current == null`, render nothing.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/TelemetryHud.kt \
  app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt \
  app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt
git commit -m "feat: overlay throttle and brake telemetry on the replay"
```

---

### Task 9: Documentation and full verification

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/specs/2026-11-10-pocket-sim-engineer.md`

- [ ] **Step 1: Update docs**

In `DESIGN.md`:
- Lap screen: fixed header with overflow menu, scrolling content, sticky footer with time, ruler, and transport; ruler is the only scrubber.
- Telemetry: combined throttle/brake chart, value bars, ±5 s sparklines, tire heatmap, event dots on map and ruler (legend: brake/coast/throttle/turn), turn from steering peaks.
- Playback: speed dropdown 0.5×/1×/1.5×/2×; default from Settings, session-only changes; HUD overlay default on, setting-controlled.

In the spec:
- Playback section: replace the slider+ruler description with the sticky footer/ruler-as-scrubber behaviour, speed control, and HUD.
- Library/telemetry section: add tire heatmap and event markers.

- [ ] **Step 2: Run the full check**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add DESIGN.md docs/specs/2026-11-10-pocket-sim-engineer.md
git commit -m "docs: describe the polished lap screen"
```

---

## Self-Review Notes

- Spec coverage: playback ruler behaviour, haptics, telemetry charts, driver assignment, replay replacement, and local-first constraints all map to tasks above. Sectors and next-turn are explicitly out of scope.
- Deferred: landscape two-pane, ghost-lap overlay, sector/turn jump buttons, auto-hide chrome.
- Dead code: `DriverSection` and `AddDriverDialog` remain (still covered by `DriverSectionTest`); only the lap-screen usage is removed. Remove them in a separate cleanup if the unused composable becomes a lint warning.
