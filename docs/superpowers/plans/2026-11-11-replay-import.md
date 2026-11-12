# Replay Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace screen capture with telemetry-only single-lap capture, then import a PS App replay, sync it with one lap-line anchor, and review the trimmed clip.

**Architecture:** Phase 0 fixes GT7 decoding for all four packet formats. Capture becomes a per-lap coordinator inside `TelemetryService`: it buffers the lap in progress and writes one session containing one lap when the line is crossed. Replays arrive as MP4s through the share sheet or SAF, are trimmed losslessly against a single frame anchor, and attach to that lap.

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer, `MediaExtractor`/`MediaMuxer`, kotlinx.serialization, BouncyCastle Salsa20, JUnit4.

**Spec:** `docs/specs/2026-11-10-pocket-sim-engineer.md`

## Global Constraints

- Android native Kotlin/Compose; `minSdk 29`, `targetSdk 36`, package `io.github.jlai403.pocketsimengineer`.
- Local-only app: LAN UDP telemetry, no cloud APIs, no accounts.
- No new permissions. `RECORD_AUDIO` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` are deleted.
- One clip = one lap. One anchor per clip. No per-session sync offset for new clips.
- Imported video is copied into app storage and trimmed by remux only; never re-encoded.
- Track name comes from lap coordinates, not from GT7; manual rename overrides.
- `./gradlew :core:test :app:testDebugUnitTest` and `:app:lintDebug` must pass.
- Version code stays `1000 + git commit count`; no manual bumps.

## Review Focus

1. **Connected mid-lap, then Capture this lap** — must reject the partial lap, not save it. Pinned in Task 3.
2. **Clip shorter than the lap** — must block the cut and say how much is missing. Pinned in Task 8.
3. **Wrong lap attached** — computed start frame must read ~0% track, otherwise block. Pinned in Task 8.
4. **Share URI cannot be read (link, expired grant)** — copy must fail cleanly and leave the SAF path usable. Pinned in Task 9.
5. **Legacy session with no per-lap clip** — library must show it without a crash and offer Add replay. Pinned in Task 6.

---

## Task 1: Decode every GT7 packet format

**Files:**
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Protocol.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Crypto.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Decoder.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7CryptoTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7DecoderTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/TestPacketFactory.kt`

**Interfaces:**
- Consumes: `Gt7Protocol.KEY`, `PacketAParser.parse`.
- Produces: `Gt7Protocol.HEARTBEAT: String`, `Gt7Protocol.PACKET_SIZES: Set<Int>`, `Gt7PacketFormat.forSize(size: Int): Gt7PacketFormat?`, `Gt7Crypto.decrypt(datagram: ByteArray): ByteArray?`.

- [ ] **Step 1: Write the failing tests**

Add to `Gt7CryptoTest`:

```kotlin
@Test
fun `packet C decrypts with the BEEF constant`() {
    val packet = TestPacketFactory.packet(format = Gt7PacketFormat.C, lapCount = 3)
    val decrypted = Gt7Crypto.decrypt(packet)
    assertNotNull(decrypted)
    assertEquals(368, decrypted!!.size)
}

@Test
fun `packet A still decrypts with the BEAF constant`() {
    val packet = TestPacketFactory.packet(format = Gt7PacketFormat.A, lapCount = 3)
    assertNotNull(Gt7Crypto.decrypt(packet))
}
```

Create `Gt7DecoderTest`:

```kotlin
@Test
fun `all four formats decode to a sample`() {
    Gt7PacketFormat.entries.forEach { format ->
        val result = Gt7Decoder().decode(
            TestPacketFactory.packet(format = format, lapCount = 4),
            receivedAtNanos = 1_000,
        )
        assertTrue("$format failed: $result", result is DecodeResult.Success)
        assertEquals(4, (result as DecodeResult.Success).sample.lapCount)
    }
}

@Test
fun `unknown size is rejected with its size in the reason`() {
    val result = Gt7Decoder().decode(ByteArray(123), 1_000)
    assertEquals(DecodeResult.Invalid("unsupported-size:123"), result)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests '*Gt7*'`
Expected: FAIL — `Gt7PacketFormat` and `TestPacketFactory.packet` do not exist.

- [ ] **Step 3: Implement the format table and decryption fallback**

In `Gt7Protocol` add:

```kotlin
const val HEARTBEAT = "C"
val PACKET_SIZES = setOf(296, 316, 344, 368)
```

Create `Gt7PacketFormat` (same file) with sizes and XOR constants: A `296 / 0xDEADBEAF`, B `316 / 0xDEADBEEF`, `~` `344 / 0x55FABB4F`, C `368 / 0xDEADBEEF`. `forSize` returns null for other sizes.

Change `Gt7Crypto.decrypt` to try `Gt7PacketFormat.forSize(datagram.size)` first, then every other constant, returning the first result whose magic is `Gt7Protocol.MAGIC`. Keep the `size < 68` guard.

Change `Gt7Decoder` to accept any size in `Gt7Protocol.PACKET_SIZES` and parse with `PacketAParser.parse` unchanged; the A fields are at the same offsets in all formats.

Extend `TestPacketFactory` with `packet(format: Gt7PacketFormat, lapCount: Int): ByteArray` that builds the existing A body, pads to `format.size` with zeros, and encrypts with `format`’s XOR constant.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests '*Gt7*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol
git commit -m "fix: decode GT7 packet formats B, ~ and C"
```

---

## Task 2: Log decode failures with size and reason

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/DecodeFailureLog.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/UdpTelemetrySource.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryState.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/DecodeFailureLogTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/recording/StreamNotificationTextTest.kt` (compile update only)

**Interfaces:**
- Consumes: `DecodeResult.Invalid(reason)`, `Diagnostics.log`.
- Produces: `DecodeFailureLog.record(size: Int, reason: String): Boolean`, `DecodeFailureLog.snapshot(): List<String>`, `TelemetryUiState.lastDecodeFailure: String`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `records at most five distinct failures`() {
    val log = DecodeFailureLog(limit = 5)
    repeat(9) { log.record(368, "decrypt-or-magic-$it") }
    assertEquals(5, log.snapshot().size)
    assertEquals("368 decrypt-or-magic-0", log.snapshot().first())
}

@Test
fun `duplicate failure is recorded once`() {
    val log = DecodeFailureLog()
    assertTrue(log.record(368, "decrypt-or-magic"))
    assertFalse(log.record(368, "decrypt-or-magic"))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:test --tests '*DecodeFailureLogTest'`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement and wire it**

Create the ring log:

```kotlin
class DecodeFailureLog(private val limit: Int = 5) {
    fun record(size: Int, reason: String): Boolean
    fun snapshot(): List<String>
}
```

In `UdpTelemetrySource`, on `DecodeResult.Invalid`, call `record(packet.length, result.reason)`. When it returns true, call `Diagnostics.log("decode failed size=${packet.length} reason=${result.reason}")` and publish `lastDecodeFailure = "${packet.length} ${result.reason}"`.

Also replace the heartbeat literal `"A"` with `Gt7Protocol.HEARTBEAT.toByteArray()` so the console sends packet C.

Add `val lastDecodeFailure: String = ""` to `TelemetryUiState` and include it in the notification details in `StreamNotificationText.details` after the error count.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:test :app:testDebugUnitTest --tests '*DecodeFailureLogTest' --tests '*StreamNotificationTextTest'`
Expected: PASS.

On the phone: install the debug build, connect, and drive. Expect packets/s near 60, a flat error count, and an empty `lastDecodeFailure`. If errors remain, the notification now shows the packet size and reason.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/DecodeFailureLog.kt \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/DecodeFailureLogTest.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/recording/StreamNotificationTextTest.kt
git commit -m "feat: report GT7 decode failures by size and reason"
```

---

## Task 3: Buffer one lap and decide when to save it

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/capture/LapSampleBuffer.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/capture/CaptureState.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/capture/LapCaptureCoordinator.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetector.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/capture/LapSampleBufferTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/capture/LapCaptureCoordinatorTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetectorTest.kt` (add cases)

**Interfaces:**
- Consumes: `Sample`, `LapBoundary`.
- Produces:
  - `LapDetector.currentLapStartNanos: Long?`, `LapDetector.currentLapStartObserved: Boolean` — true only after a lap count increase, false for the lap that was already in progress when the app connected.
  - `LapSampleBuffer.add(sample: Sample)`, `samplesFor(boundary: LapBoundary, preRollNanos: Long = 2_000_000_000L): List<Sample>`, `covers(boundary: LapBoundary): Boolean`, `trimBefore(nanos: Long)`, `clear()`.
  - `sealed interface CaptureState { Idle; Capturing; Saved(sessionId: String, lapTimeMs: Int); Rejected(reason: String) }`
  - `interface LapSink { fun save(boundary: LapBoundary, samples: List<Sample>): SessionMetadata }`
  - `class LapCaptureCoordinator(buffer: LapSampleBuffer = LapSampleBuffer(), sink: LapSink)` with `state: MutableStateFlow<CaptureState>`, `requestCapture(expectedLapStartNanos: Long)`, `cancelCapture()`, `onSample(sample)`, `onBoundary(boundary)`, `onDisconnect()`.

- [ ] **Step 1: Write the failing tests**

Add to `LapDetectorTest`:

```kotlin
@Test
fun `lap start is unobserved until a crossing happens`() {
    val detector = LapDetector()
    detector.onSample(sample(lapCount = 1, atMs = 30_000))
    assertFalse(detector.currentLapStartObserved)

    detector.onSample(sample(lapCount = 2, atMs = 90_000, lastLapTimeMs = 60_000))
    assertTrue(detector.currentLapStartObserved)
}

@Test
fun `lap reset makes the next start unobserved`() {
    val detector = LapDetector()
    detector.onSample(sample(lapCount = 3, atMs = 0))
    detector.onSample(sample(lapCount = 1, atMs = 10_000))
    assertFalse(detector.currentLapStartObserved)
}
```

Add to `LapCaptureCoordinatorTest`:

```kotlin
@Test
fun `capture saves the lap whose start was requested`() {
    val sink = RecordingSink()
    val coordinator = LapCaptureCoordinator(sink = sink)
    coordinator.requestCapture(expectedLapStartNanos = 1_000)
    (0..120).forEach { coordinator.onSample(sample(atMs = it * 1_000L, lapCount = 1)) }
    coordinator.onBoundary(boundary(startMs = 1_000, endMs = 120_000, lap = 1))

    assertEquals(1, sink.saved.size)
    assertTrue(coordinator.state.value is CaptureState.Saved)
}

@Test
fun `capture requested after connecting mid-lap rejects the partial lap`() {
    val sink = RecordingSink()
    val coordinator = LapCaptureCoordinator(sink = sink)
    coordinator.onSample(sample(atMs = 30_000, lapCount = 1))
    coordinator.requestCapture(expectedLapStartNanos = 30_000)
    coordinator.onSample(sample(atMs = 31_000, lapCount = 1))
    coordinator.onBoundary(boundary(startMs = 30_000, endMs = 31_000, lap = 1))

    assertTrue(sink.saved.isEmpty())
    assertTrue(coordinator.state.value is CaptureState.Rejected)
}

@Test
fun `boundary for a different lap start is rejected`() {
    val sink = RecordingSink()
    val coordinator = LapCaptureCoordinator(sink = sink)
    coordinator.requestCapture(expectedLapStartNanos = 999)
    coordinator.onSample(sample(atMs = 1_000, lapCount = 1))
    coordinator.onBoundary(boundary(startMs = 1_000, endMs = 60_000, lap = 1))

    assertTrue(sink.saved.isEmpty())
    assertTrue(coordinator.state.value is CaptureState.Rejected)
}

@Test
fun `cancel and disconnect discard the capture`() {
    val sink = RecordingSink()
    val coordinator = LapCaptureCoordinator(sink = sink)
    coordinator.requestCapture(expectedLapStartNanos = 1_000)
    coordinator.cancelCapture()
    coordinator.onSample(sample(atMs = 1_000, lapCount = 1))
    coordinator.onBoundary(boundary(startMs = 1_000, endMs = 2_000, lap = 1))
    assertTrue(sink.saved.isEmpty())

    coordinator.requestCapture(expectedLapStartNanos = 2_000)
    coordinator.onDisconnect()
    assertEquals(CaptureState.Idle, coordinator.state.value)
}
```

Use a `RecordingSink` test double returning a fixed `SessionMetadata`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests '*LapCaptureCoordinatorTest' --tests '*LapSampleBufferTest' --tests '*LapDetectorTest'`
Expected: FAIL — the new properties and types do not exist.

- [ ] **Step 3: Implement the detector, buffer, and coordinator**

`LapDetector`: add `@Volatile var currentLapStartNanos: Long? = null` and `@Volatile var currentLapStartObserved: Boolean = false` with private setters. Set both on the first sample and on a lap count decrease (`observed = false`). On a lap count increase, after emitting the boundary, set `currentLapStartNanos = sample.timeNanos` and `currentLapStartObserved = true`.

`LapSampleBuffer`: keep samples in arrival order, drop anything older than 15 minutes on `add`, return `samplesFor` filtered to `[boundary.startNanos - preRollNanos, boundary.endNanos]`, and `covers` returns `samples.firstOrNull()?.timeNanos?.let { it < boundary.startNanos } == true`. Strict `<` is deliberate: if the first buffered sample equals the boundary start, the lap has no observed start. Mark its methods `@Synchronized`: `requestCapture` runs on the service thread while samples arrive on the UDP thread.

`LapCaptureCoordinator.onBoundary`:

```kotlin
when {
    state.value !is CaptureState.Capturing -> buffer.trimBefore(boundary.endNanos - 3_000_000_000L)
    boundary.startNanos != expectedStartNanos || !buffer.covers(boundary) -> {
        state.value = CaptureState.Rejected("Lap started before the app connected")
        expectedStartNanos = null
        buffer.trimBefore(boundary.endNanos - 3_000_000_000L)
    }
    else -> {
        val saved = sink.save(boundary, buffer.samplesFor(boundary))
        state.value = CaptureState.Saved(saved.sessionId, boundary.lapTimeMs)
        expectedStartNanos = null
        buffer.trimBefore(boundary.endNanos - 3_000_000_000L)
    }
}
```

`requestCapture(expectedLapStartNanos)` sets `expectedStartNanos` and `Capturing`; `cancelCapture()` sets `Idle` and clears it; `onDisconnect()` clears the buffer, expected start, and `Idle` when capturing.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:test --tests '*LapCaptureCoordinatorTest' --tests '*LapSampleBufferTest' --tests '*LapDetectorTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/capture \
        core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetector.kt \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/capture \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetectorTest.kt
git commit -m "feat: capture exactly one lap at a time"
```

---

## Task 4: Save captured laps and wire the coordinator into telemetry

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/capture/LapSaver.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryState.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/capture/LapSaverTest.kt`

**Interfaces:**
- Consumes: `LapSink`, `LapCaptureCoordinator`, `SessionWriter`, `LapSampleBuffer`, `LapDetector.currentLapStartNanos`, `LapDetector.currentLapStartObserved`.
- Produces: `LapSaver(sessionsDir: File, idFactory: () -> String = ..., clock: () -> Instant = ..., trackName: (List<Sample>) -> String? = { null })`; `TelemetryUiState.lapStartObserved: Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `saves one session with one lap and no video`() {
    val root = Files.createTempDirectory("laps").toFile()
    val saver = LapSaver(root, idFactory = { "lap-test" }, clock = { Instant.parse("2026-11-11T10:00:00Z") })
    val samples = (0..120).map { sample(atMs = it * 1_000L, lapCount = 1) }
    val metadata = saver.save(boundary(startMs = 0, endMs = 120_000, lap = 1), samples)

    assertEquals("lap-test", metadata.sessionId)
    assertEquals(1, metadata.laps.size)
    assertTrue(File(root, "lap-test/session.json").isFile)
    assertTrue(File(root, "lap-test/laps/lap-001.gt7lap").isFile)
    assertTrue(metadata.laps.single().videoFile.isEmpty())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LapSaverTest'`
Expected: FAIL — `LapSaver` does not exist.

- [ ] **Step 3: Implement and wire**

`LapSaver.save` builds `SessionMetadata` with `trackLabel = trackName(samples) ?: "Unknown"`, `carCode = samples.lastOrNull()?.carCode ?: 0`, `carName = "Car #$carCode"`, `rawFile = ""`, `rawDeleted = true`, `createdAt = clock().toString()`. Create a `SessionWriter`, add every sample, call `writeLap(boundary, videoFile = "", videoStartOffsetMs = 0)`, finalize, return `writer.metadata()`.

In `TelemetryService` companion, expose `val capture = MutableStateFlow<CaptureState>(CaptureState.Idle)`. In the service:

```kotlin
private lateinit var coordinator: LapCaptureCoordinator
private val lapDetector = LapDetector()
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

// in ACTION_START, after the source starts:
coordinator = LapCaptureCoordinator(sink = LapSaver(File(filesDir, "sessions")))
serviceScope.launch { coordinator.state.collect { capture.value = it } }
```

Pass the same `lapDetector` into `UdpTelemetrySource` and publish `lapStartObserved = lapDetector.currentLapStartObserved` in `TelemetryUiState`.

Feed `onSample = { coordinator.onSample(it); RecordingService.instance?.onTelemetry(it) }` and `onLap = { coordinator.onBoundary(it); handleLap(it) }`.

Add actions `ACTION_CAPTURE = "…action.CAPTURE_LAP"` and `ACTION_CANCEL_CAPTURE = "…action.CANCEL_CAPTURE"`. `ACTION_CAPTURE` reads `lapDetector.currentLapStartNanos` and calls `coordinator.requestCapture(start)` only when `::coordinator.isInitialized && state.value.connected && lapDetector.currentLapStartObserved && start != null`; otherwise set `capture.value = Rejected("Cross the start line first")`. `ACTION_CANCEL_CAPTURE` calls `coordinator.cancelCapture()`. Cancel `serviceScope` and reset `capture.value = Idle` in `onDestroy`.

Keep the existing `RecordingService` calls so the old path still compiles and works until Task 5.

- [ ] **Step 4: Run the tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests '*LapSaverTest'`
Expected: PASS.
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/capture \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/capture
git commit -m "feat: persist captured single laps"
```

---

## Task 5: Replace the Record screen and delete the capture stack

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LiveScreen.kt` (from `CaptureScreen.kt`)
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/settings/SettingsStore.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/StreamNotificationText.kt`
- Delete: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/` files `AudioCapture.kt`, `AudioEncoderConfig.kt`, `Recorder.kt`, `ScreenRecorder.kt`, `VideoEncoderConfig.kt`, `RecordingController.kt`, `RecordingEvent.kt`, `RecordingState.kt`, `RecordingService.kt`, `SessionFinalizer.kt`, `LapCutter.kt`, `LapRemuxer.kt`
- Delete: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/CaptureScreen.kt`
- Delete: their tests under `app/src/test/.../recording/` except `StreamNotificationTextTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LiveScreenTest.kt` (rename `CaptureScreenTest.kt`)
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ManifestComponentsTest.kt` (unchanged, must pass)
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/settings/SettingsStoreTest.kt` (remove video settings)

**Interfaces:**
- Consumes: `TelemetryService.capture`, `ACTION_CAPTURE`, `ACTION_CANCEL_CAPTURE`.
- Produces: `LiveScreen(state: CaptureUiState, onSaveIp, onConnect, onDisconnect, onCapture, onCancelCapture, onOpenLap: (String, Int) -> Unit)`, `CaptureUiState.capture: CaptureState`, `CaptureUiState.lapStartObserved: Boolean`.

- [ ] **Step 1: Update the UI tests to the new screen**

In `LiveScreenTest`, assert the three states:

```kotlin
@Test
fun `live screen shows capture this lap when idle`() {
    composeTestRule.setContent { LiveScreen(state = CaptureUiState(connected = true, lapStartObserved = true), ...) }
    composeTestRule.onNodeWithText("Capture this lap").assertIsEnabled()
}

@Test
fun `live screen shows progress while capturing`() {
    composeTestRule.setContent {
        LiveScreen(state = CaptureUiState(connected = true, capture = CaptureState.Capturing), ...)
    }
    composeTestRule.onNodeWithText("Capturing lap — finishes at the next line").assertExists()
}
```

Also add a `SettingsStoreTest` assertion that `SettingsStore` no longer exposes `quality`, `codec`, `bitrateMbps`, or `keepRaw` (compile-time removal is the check; delete those tests).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LiveScreenTest'`
Expected: FAIL — `LiveScreen` does not exist.

- [ ] **Step 3: Implement the Live screen and delete the capture path**

`LiveScreen` keeps the existing `StreamCard` (IP, Connect/Disconnect, health, pps/errors, diagnostics) and replaces the recording card with:

- `Capture this lap` button, disabled unless `state.connected && state.lapStartObserved`; when disabled after connecting, show `Cross the start line to begin a lap`.
- While `Capturing`: text `Capturing lap — finishes at the next line` and a `Cancel` button.
- `Saved`: `Saved 2:39.831` with a `View lap` action that calls `onOpenLap(sessionId, 1)`.
- `Rejected`: the reason in error color.
- Remove `Test 10 s`, audio warning, quality/bitrate, and recording instructions.

`MainActivity`: delete the MediaProjection and audio launchers, `CapturePurpose`, `ensureAudio`, `requestCapture`, `startRecording`, `stopRecording`, and the `RecordingService` imports. Add:

```kotlin
onCapture = {
    startService(Intent(this, TelemetryService::class.java).setAction(TelemetryService.ACTION_CAPTURE))
},
onCancelCapture = {
    startService(Intent(this, TelemetryService::class.java).setAction(TelemetryService.ACTION_CANCEL_CAPTURE))
},
```

Collect `TelemetryService.capture` and pass it into `CaptureUiState.capture`.

`App`: bottom bar tab label `Record` becomes `Live`, `Screen.Capture` becomes `Screen.Live`. Remove `onStartRecording`, `onTestRecord`, `onStop`; add `onCapture`, `onCancelCapture`; pass `onOpenLap = { id, index -> screen = Screen.Player(id, index) }` to `LiveScreen`.

`SettingsStore`: delete `VideoQuality`, `VideoCodec`, `keepRaw`, `quality`, `codec`, `bitrateMbps`, and their keys; replace `RecordingService.SETTINGS` with the literal `"settings"` (the existing prefs file name, so the saved PS5 IP survives). Update `SettingsScreen` to keep only PS5 IP (read-only display), units, diagnostics, update, and build rows.

`AndroidManifest`: remove `RECORD_AUDIO`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and the `RecordingService` entry. Keep `FOREGROUND_SERVICE_DATA_SYNC`.

`TelemetryService`: remove `RecordingService` imports and calls, toast/wakelock conditions, and use `capture.state` for the notification. Hold the wake lock while the service runs. Add a `Cancel` notification action when `Capturing`.

`LibraryScreen`: remove the `RecordingService.activeSessionDir` filter and load `store.groups()` directly. Task 6 replaces this screen with the flat list.

`StreamNotificationText.streamSummary(telemetry, capture)` returns `GT7 stream · ready`, `GT7 stream · capturing lap`, `GT7 stream · saved 2:39.831`, or the existing waiting text.

- [ ] **Step 4: Run tests, lint, and build**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: PASS with no manifest-component failures and no unused-permission warnings.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main app/src/test
git commit -m "refactor: remove screen capture and add live lap capture"
```

---

## Task 6: Flat lap library with filter and sort

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapSummary.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionStore.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapMetadata.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt`
- Delete: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SessionScreen.kt`
- Delete: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SessionScreenTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapSummaryTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `SessionMetadata`, `LapMetadata`.
- Produces:
  - `data class LapSummary(sessionId, lapIndex, lapTimeMs, createdAt, trackLabel, carName, hasReplay, replayPending)`
  - `enum class LapSort { Newest, Fastest, Track }`
  - `data class LapFilter(val hasReplay: Boolean? = null, val trackLabel: String? = null)`
  - `SessionStore.summaries(): List<LapSummary>`, `SessionStore.deleteLap(sessionId, lapIndex): Boolean`, `SessionStore.lap(sessionId, lapIndex): LapMetadata?`
  - `List<LapSummary>.applyFilter(filter): List<LapSummary>`, `List<LapSummary>.applySort(sort): List<LapSummary>`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `summaries flatten sessions into lap cards`() {
    val store = storeWithOneLap(videoFile = "")
    val summary = store.summaries().single()
    assertFalse(summary.hasReplay)
    assertEquals(1, summary.lapIndex)
}

@Test
fun `legacy lap without a clip stays visible`() {
    val store = storeWithOneLap(videoFile = "laps/lap-001.mp4", createFile = false)
    assertFalse(store.summaries().single().hasReplay)
}

@Test
fun `deleteLap removes the session when the last lap goes`() {
    val store = storeWithOneLap(videoFile = "")
    assertTrue(store.deleteLap("s1", 1))
    assertTrue(store.list().isEmpty())
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests '*LapSummaryTest'`
Expected: FAIL — `summaries` and `deleteLap` do not exist.

- [ ] **Step 3: Implement the model, store methods, and library UI**

`SessionStore.summaries()` maps each session and lap, sets `hasReplay = lap.videoFile.isNotBlank() && File(sessionDir, lap.videoFile).isFile`, sets `replayPending = !lap.sourceFile.isNullOrBlank()`, and sorts newest first.

Add `val sourceFile: String? = null` to `LapMetadata` so a pending import can be represented; older JSON still decodes because of the default.

`deleteLap` deletes `laps/lap-%03d.gt7lap`, `laps/lap-%03d.json`, `lap.videoFile`, `lap.videoFile + ".tmp"`, and `lap.sourceFile`; removes the lap from metadata; deletes the session directory when no laps remain; returns false if the lap is missing.

Rewrite `LibraryScreen` as a flat `LazyColumn` of `LapCard`s. Header: title `Laps`, filter chips `All`, `With replay`, `Without replay`, a track dropdown built from distinct `trackLabel`s, and a sort menu `Newest`, `Fastest`, `Track`. Each card shows lap time prominently, track, car, date, and a `Replay` badge; tapping opens the player. A long-press/menu offers `Rename track` (calls `store.rename`) and `Delete lap` (calls `store.deleteLap`).

Update `App`: `Screen.Session` is removed; `Back` from `Player` goes to `Library`; `Library` opens `Screen.Player(sessionId, lapIndex)` directly.

Delete `SessionGroup`, `SessionStore.groups()`, and the group tests in `SessionStoreTest`; the flat list replaces them.

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests '*LapSummaryTest' :app:testDebugUnitTest --tests '*LibraryScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapSummaryTest.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreenTest.kt
git rm app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SessionScreen.kt \
       app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SessionScreenTest.kt
git commit -m "feat: flat lap library with filters and sorting"
```

---

## Task 7: Identify the track from lap coordinates

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/track/TrackSignature.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/track/TrackIdentifier.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/track/TrackSignatureCodec.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/track/TrackCatalog.kt`
- Create: `app/src/main/assets/track-signatures.json`
- Create: `THIRD_PARTY_NOTICES.md`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/track/TrackIdentifierTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/track/TrackSignatureCodecTest.kt`

**Interfaces:**
- Consumes: `Sample.positionX`, `positionZ`.
- Produces:
  - `data class TrackSignature(officialId: String, name: String, lengthM: Double, minX: Double, maxX: Double, minZ: Double, maxZ: Double)`
  - `TrackIdentifier.signature(samples: List<Sample>): TrackSignature?`
  - `TrackIdentifier.match(lap: TrackSignature, candidates: List<TrackSignature>): TrackSignature?`
  - `TrackSignatureCodec.decode(json: String): List<TrackSignature>`
  - `TrackCatalog(context).identify(samples: List<Sample>): String?`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `square lap has a 400 metre signature`() {
    val signature = TrackIdentifier.signature(squareLap(side = 100.0))!!
    assertEquals(400.0, signature.lengthM, 1.0)
    assertEquals(0.0, signature.minX, 0.01)
    assertEquals(100.0, signature.maxZ, 0.01)
}

@Test
fun `exactly one matching candidate is returned`() {
    val lap = TrackSignature("", "", 400.0, 0.0, 100.0, 0.0, 100.0)
    val match = TrackIdentifier.match(lap, listOf(
        TrackSignature("a", "A", 405.0, 0.0, 101.0, 0.0, 101.0),
        TrackSignature("b", "B", 800.0, 0.0, 200.0, 0.0, 200.0),
    ))
    assertEquals("A", match?.name)
}

@Test
fun `two close candidates are ambiguous`() {
    val lap = TrackSignature("", "", 400.0, 0.0, 100.0, 0.0, 100.0)
    assertNull(TrackIdentifier.match(lap, listOf(
        TrackSignature("a", "A", 400.0, 0.0, 100.0, 0.0, 100.0),
        TrackSignature("b", "B", 401.0, 0.0, 100.5, 0.0, 100.5),
    )))
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests '*TrackIdentifierTest'`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Implement the matcher and bundle the data**

Signature: require at least 60 samples; sum `hypot(dx, dz)` for consecutive samples whose distance is ≤ 200 m; compute min/max X and Z; return null if length ≤ 0.

Match gates: `abs(lap.lengthM - candidate.lengthM) / candidate.lengthM <= 0.04`, centre distance ≤ 120 m on X and Z, and each extent within 20%. Collect matches; return the single match or null.

Codec: parse `{ "signatures": [ { "official_id", "official_name", "length_m", "min_x", "max_x", "min_z", "max_z" } ] }` with kotlinx.serialization and map to `TrackSignature`.

Bundle the data (CC0, `jbhoorasingh/gt7-datalogger-track-data`):

```bash
mkdir -p app/src/main/assets
curl -L https://raw.githubusercontent.com/jbhoorasingh/gt7-datalogger/main/backend/app/data/track-signatures.json \
  | jq '{signatures: [.signatures[] | {official_id, official_name, length_m, min_x, max_x, min_z, max_z}]}' \
  > app/src/main/assets/track-signatures.json
```

Add `THIRD_PARTY_NOTICES.md` with the CC0 attribution and the source URL. `TrackCatalog` loads the asset lazily and returns `TrackIdentifier.match(...)?.name`.

In `TelemetryService`, construct `LapSaver(..., trackName = TrackCatalog(this)::identify)`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests '*Track*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/track \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/track \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/track \
        app/src/main/assets/track-signatures.json \
        THIRD_PARTY_NOTICES.md \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt
git commit -m "feat: identify GT7 tracks from lap coordinates"
```

---

## Task 8: Core clip sync and track progress

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clip/ClipSync.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrackProgress.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/clip/ClipSyncTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrackProgressTest.kt`

**Interfaces:**
- Consumes: `Sample`.
- Produces:
  - `data class ClipSync(val videoAnchorMs: Long, val lapTimeMs: Int)` with `trimInMs`, `trimOutMs`, `telemetryMsAt(videoMs: Long): Int`, `videoMsAt(telemetryMs: Int): Long`.
  - `sealed interface SyncCheck { Ok; ClipTooShort(missingMs: Long); StartNotAtLine(trackPercent: Float) }`
  - `SyncValidator.check(sync: ClipSync, sourceDurationMs: Long, startTrackPercent: Float, tolerancePercent: Float = 2f): SyncCheck`
  - `TrackProgress.percentAt(samples: List<Sample>, lapStartNanos: Long, timeMs: Int): Float`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `anchor at the line gives a lap-length trim`() {
    val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
    assertEquals(5_269L, sync.trimInMs)
    assertEquals(165_100L, sync.trimOutMs)
    assertEquals(0, sync.telemetryMsAt(5_269L))
    assertEquals(159_831, sync.telemetryMsAt(165_100L))
}

@Test
fun `clip shorter than the lap is blocked`() {
    val sync = ClipSync(videoAnchorMs = 150_000, lapTimeMs = 159_831)
    assertEquals(SyncCheck.ClipTooShort(missingMs = 9_831L), SyncValidator.check(sync, 170_000, 0f))
}

@Test
fun `start frame away from the line is blocked`() {
    val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
    assertEquals(SyncCheck.StartNotAtLine(41f), SyncValidator.check(sync, 170_000, 41f))
    assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 0.4f))
    assertEquals(SyncCheck.Ok, SyncValidator.check(sync, 170_000, 99.6f))
}
```

For `TrackProgress`, build a square path and assert `percentAt` is 0 at the start, 50 at half the time, and 100 at the end.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests '*ClipSyncTest' --tests '*TrackProgressTest'`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Implement**

`ClipSync` math as tested. `SyncValidator` returns `ClipTooShort` when `trimInMs < 0` or `trimOutMs > sourceDurationMs`, and `StartNotAtLine` when `abs(startTrackPercent) > tolerance` and `abs(startTrackPercent - 100f) > tolerance`; otherwise `Ok`.

`TrackProgress.percentAt` computes cumulative planar distance between consecutive samples, clamps step distances above 200 m to 0, finds the sample nearest `lapStartNanos + timeMs * 1_000_000`, and returns `100 * cumulative / total` (0 when total is 0).

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests '*ClipSyncTest' --tests '*TrackProgressTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clip \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/clip
git commit -m "feat: one-anchor clip sync and track progress"
```

---

## Task 9: Receive replays through share and SAF

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/replay/ReplayStore.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportPickScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/replay/ReplayStoreTest.kt`

**Interfaces:**
- Consumes: `SessionStore.summaries()`.
- Produces:
  - `LapMetadata.sourceFile: String? = null`
  - `ReplayStore.sourceFile(sessionDir: File, lapIndex: Int): File`
  - `ReplayStore.copy(context: Context, uri: Uri, destination: File): Boolean`
  - `Screen.ImportPick(uri: String)`, `Screen.ImportSync(sessionId: String, lapIndex: Int, sourceUri: String?)`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `source file lives beside the lap clip`() {
    val dir = File("/tmp/session")
    assertEquals(File(dir, "laps/lap-007-source.mp4"), ReplayStore.sourceFile(dir, 7))
}

@Test
fun `copy returns false and leaves no file when the uri cannot be opened`() {
    val root = Files.createTempDirectory("replay").toFile()
    val destination = File(root, "lap-source.mp4")
    assertFalse(ReplayStore.copy(RuntimeEnvironment.getApplication(), Uri.parse("content://missing/1"), destination))
    assertFalse(destination.exists())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReplayStoreTest'`
Expected: FAIL — `sourceFile` and `ReplayStore` do not exist.

- [ ] **Step 3: Implement import plumbing**

`ReplayStore.copy` opens `context.contentResolver.openInputStream(uri)`, writes to `destination + ".tmp"`, renames only after a non-empty write, and deletes the temp on any exception.

`MainActivity`: add `singleTop` and a share intent filter on the main activity:

```xml
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="video/*" />
</intent-filter>
```

Read `Intent.EXTRA_STREAM` as a `Uri` in `onCreate` and `onNewIntent`; expose it as a `MutableStateFlow<String?>`. Pass it to `App` as `sharedVideoUri`. When non-null, navigate to `Screen.ImportPick(uri)`.

`ImportPickScreen` lists `SessionStore.summaries().filter { !it.hasReplay }`, newest first, with lap time and track. Selecting a lap calls `onPick(sessionId, lapIndex)`. If the list is empty, show `No laps without a replay` and a `Close` action.

In `App`, handle `Screen.ImportPick` and route `onPick` to `Screen.ImportSync(sessionId, lapIndex, uri)`.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests '*ReplayStoreTest' :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapMetadata.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/replay \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportPickScreen.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/replay/ReplayStoreTest.kt
git commit -m "feat: receive replay clips from share and file picker"
```

---

## Task 10: Trim an imported clip without re-encoding

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrimPlan.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/replay/ClipTrimmer.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrimPlanTest.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/RemuxPlannerTest.kt` (add offset case)

**Interfaces:**
- Consumes: `ClipSync`, `RemuxPlanner.alignToKeyframe`.
- Produces:
  - `data class TrimRange(val startMs: Long, val endMs: Long)`
  - `TrimPlan.forSync(sync: ClipSync, sourceDurationMs: Long): TrimRange?`
  - `data class TrimResult(val output: File, val startOffsetMs: Int)`
  - `ClipTrimmer.trim(source: File, output: File, range: TrimRange): TrimResult?`

- [ ] **Step 1: Write the failing test**

Create `TrimPlanTest`:

```kotlin
@Test
fun `plan rejects a clip shorter than the lap`() {
    val sync = ClipSync(videoAnchorMs = 150_000, lapTimeMs = 159_831)
    assertNull(TrimPlan.forSync(sync, sourceDurationMs = 170_000))
}

@Test
fun `plan returns the exact lap range`() {
    val sync = ClipSync(videoAnchorMs = 165_100, lapTimeMs = 159_831)
    assertEquals(TrimRange(5_269L, 165_100L), TrimPlan.forSync(sync, sourceDurationMs = 170_000))
}
```

Add to `RemuxPlannerTest`:

```kotlin
@Test
fun `offset is the distance from the keyframe to the line`() {
    val aligned = RemuxPlanner.alignToKeyframe(
        requestedStartUs = 5_269_000,
        keyframeUs = 4_000_000,
        lapStartUs = 5_269_000,
    )
    assertEquals(4_000_000, aligned.clipStartUs)
    assertEquals(1_269, aligned.videoStartOffsetMs)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:test --tests '*TrimPlanTest'`
Expected: FAIL — `TrimPlan` and `TrimRange` do not exist.

- [ ] **Step 3: Implement `TrimPlan` and `ClipTrimmer`**

`TrimPlan.forSync` returns null when `sync.trimInMs < 0`, `sync.trimOutMs > sourceDurationMs`, or `trimOutMs <= trimInMs`; otherwise `TrimRange(sync.trimInMs, sync.trimOutMs)`.

`ClipTrimmer.trim` opens the source with `MediaExtractor`, selects the first `video/` track and first `audio/` track. Seek to `range.startMs * 1000` with `SEEK_TO_PREVIOUS_SYNC`. Read the first video sample time as `keyframeUs`. Create a `MediaMuxer` at `output + ".tmp"`, add both tracks, start, and copy samples whose `sampleTime` is between `keyframeUs` and `range.endMs * 1000`, rebasing each PTS by `keyframeUs`. Stop, release, verify the temp is non-empty, rename to `output`, and return `TrimResult(output, ((range.startMs * 1000 - keyframeUs) / 1000).toInt())`. Return null on any failure and delete the temp.

- [ ] **Step 4: Run the unit tests, then verify on a real clip**

Run: `./gradlew :core:test --tests '*TrimPlanTest' --tests '*RemuxPlannerTest'`
Expected: PASS.
Use `adb push` of a short MP4 to `/sdcard/Download`, import it through the app once Task 11 lands, and confirm the output plays from the requested start.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrimPlan.kt \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/clip/TrimPlanTest.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/replay/ClipTrimmer.kt \
        core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/RemuxPlannerTest.kt
git commit -m "feat: lossless clip trimmer"
```

---

## Task 11: Frame-step sync screen

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncScreen.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncState.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionStore.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncStateTest.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncScreenTest.kt`

**Interfaces:**
- Consumes: `ClipSync`, `SyncValidator`, `TrackProgress`, `ClipTrimmer`, `ReplayStore`.
- Produces:
  - `data class ImportSyncState(val durationMs: Long, val positionMs: Long, val renderedMs: Long, val anchorMs: Long?, val frameStepMs: Long)`
  - `ImportSyncState.stepFrame(forward: Boolean): Long`, `ImportSyncState.nudge(deltaMs: Long): Long`
  - `SessionStore.setSource(sessionId, lapIndex, sourceFile: String?): SessionMetadata?`
  - `SessionStore.attachClip(sessionId, lapIndex, videoFile: String, videoStartOffsetMs: Int): SessionMetadata?`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `frame step moves by one frame and clamps`() {
    val state = ImportSyncState(durationMs = 10_000, positionMs = 0, renderedMs = 0, anchorMs = null, frameStepMs = 33)
    assertEquals(33L, state.stepFrame(forward = true))
    assertEquals(0L, state.stepFrame(forward = false))
}

@Test
fun `nudge never leaves the clip`() {
    val state = ImportSyncState(durationMs = 10_000, positionMs = 5_000, renderedMs = 5_000, anchorMs = null, frameStepMs = 33)
    assertEquals(9_900L, state.nudge(10_000))
    assertEquals(0L, state.nudge(-10_000))
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*ImportSyncStateTest'`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Implement the state and screen**

`ImportSyncState.stepFrame` returns `(positionMs ± frameStepMs).coerceIn(0, durationMs)`; `nudge` clamps the same way.

`ImportSyncScreen`:

- On first composition, if `sourceUri != null`, copy it to `ReplayStore.sourceFile(...)`, set `lap.sourceFile`, and delete the URI copy on failure.
- Build an ExoPlayer with `setSeekParameters(SeekParameters.EXACT)`; record the actual rendered PTS with `VideoFrameMetadataListener` into `renderedMs`.
- Detect `frameStepMs` from `MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE` (fallback `METADATA_KEY_VIDEO_FRAME_COUNT / duration`, fallback 33 ms).
- Controls: `−1 frame`, `−100 ms`, `−10 ms`, `−1 ms`, play/pause, `+1 ms`, `+10 ms`, `+100 ms`, `+1 frame`.
- `Set anchor at this frame` sets `anchorMs = renderedMs`.
- Readout: `video time · track % · speed`. Compute `telemetryMs = ClipSync(anchor, lap.lapTimeMs).telemetryMsAt(renderedMs)`, then `TrackProgress.percentAt(samples, lap.startNanos, telemetryMs)` and the nearest sample speed.
- Trim card: `in`, `out`, `start track %`, and `SyncValidator.check(...)`. Show the exact blocking message from `SyncCheck`.
- Preview: loop `trimIn..trimOut` using a 50 ms `LaunchedEffect` poll.
- `Save clip` enabled only when `check == Ok`; on tap compute `val range = TrimPlan.forSync(sync, durationMs)!!`, run `ClipTrimmer.trim(source, output, range)` on `Dispatchers.IO`, call `store.attachClip(...)`, delete the source, and navigate back.
- `Cancel`/back shows `Discard this replay?`; on confirm delete the source, clear `sourceFile`, and navigate back.

Add `SessionStore.setSource` and `attachClip` that update the lap JSON and session JSON, mirroring `updateLap`. `attachClip` also deletes the previous `sourceFile` from disk after the trimmed clip is recorded.

Update `App` to route `Screen.ImportSync`.

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest --tests '*ImportSync*' :app:assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncScreen.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncState.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt \
        core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionStore.kt \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncStateTest.kt \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ImportSyncScreenTest.kt
git commit -m "feat: frame-accurate replay sync screen"
```

---

## Task 12: Lap review with optional replay

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/Components.kt` (only if a shared button is needed)
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt`

**Interfaces:**
- Consumes: `Screen.ImportSync`, `ReplayStore`, `SessionStore.summaries`.
- Produces: `LapPlayerScreen(sessionId, lapIndex, onBack, onImportSync: (String) -> Unit)`.

- [ ] **Step 1: Update the tests**

```kotlin
@Test
fun `lap without a clip still shows charts and add replay`() {
    composeTestRule.setContent {
        LapPlayerScreen(sessionId = "s1", lapIndex = 1, onBack = {}, onImportSync = {})
    }
    composeTestRule.onNodeWithText("Add replay").assertExists()
    composeTestRule.onNodeWithText("Speed").assertExists()
}

@Test
fun `lap with a clip shows the player`() {
    composeTestRule.setContent {
        LapPlayerScreen(sessionId = "s1", lapIndex = 1, onBack = {}, onImportSync = {})
    }
    composeTestRule.onNodeWithText("Add replay").assertDoesNotExist()
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: FAIL — `onImportSync` and the no-video state do not exist.

- [ ] **Step 3: Implement**

`LapPlayerScreen` always loads samples and renders lap time, charts, and the track map. If `lap.videoFile` is blank:

- Show a `No replay attached` card and an `Add replay` button.
- `Add replay` launches `ActivityResultContracts.OpenDocument` with `arrayOf("video/*")`. On result, call `onImportSync(uri.toString())`.
- If `lap.sourceFile != null`, show `Replay imported — sync not finished` and `Resume sync` instead.

If the clip exists, keep the ExoPlayer, charts, map, and cursor. Remove the ±50/±500 ms sync nudge card; use `PlaybackCursor.telemetryMs(positionMs, lap.videoStartOffsetMs, lap.syncOffsetMs, lap.lapTimeMs)` unchanged so legacy clips keep their offset. Add `Share clip` (share the trimmed MP4) and `Export CSV`.

`App` passes `onImportSync = { screen = Screen.ImportSync(sessionId, lapIndex, it) }`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt \
        app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt \
        app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreenTest.kt
git commit -m "feat: review laps with or without a replay"
```

---

## Task 13: Update the spec, notices, and release checks

**Files:**
- Modify: `docs/specs/2026-11-10-pocket-sim-engineer.md`
- Modify: `AGENTS.md` (repo root; add new commands/conventions if present)
- Modify: `README.md` if it documents screen capture or Remote Play
- Verify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: everything above.
- Produces: spec text that matches the shipped behavior.

- [ ] **Step 1: Rewrite the spec sections**

Replace the screen-capture/Remote Play architecture with:

- Telemetry-only capture: connect, capture one lap, save.
- PS5 workflow: Time Trial → replay with HUD and race camera → Create recording → 1080p → manual PS App upload → Download/Share to PSE.
- One clip = one lap; one anchor at the lap end; GT7 lap time is the ruler.
- Import limits: PS App under 3 min, ≤1080p, 14 days; long laps use USB or broadcast MP4 through the same importer.
- Track identification from coordinates; manual rename.
- Packet formats A/B/~/C and the XOR constant per size.

Keep the existing local-only, GPLv3, update-channel, and signing constraints.

- [ ] **Step 2: Audit permissions and components**

Run:

```bash
rg -n 'RECORD_AUDIO|MEDIA_PROJECTION|RecordingService|MediaProjection' app/src core/src
```

Expected: no matches outside the spec history. `ManifestComponentsTest` must pass.

- [ ] **Step 3: Run the full verification**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: all tasks pass.

- [ ] **Step 4: Commit**

```bash
git add docs/specs/2026-11-10-pocket-sim-engineer.md AGENTS.md README.md
git commit -m "docs: specify telemetry-only replay workflow"
```

---

## Execution Notes

- Task 1 is the gate: real GT7 packets must decode before Task 4 can be exercised on a console.
- Task 4 keeps the old recording path alive for one commit so the app still builds and runs; Task 5 removes it.
- Task 8’s validator is the only authority on whether a cut is allowed. The UI must not reimplement the tolerance checks.
- Task 10 has no unit-testable Android surface; verify it with a real MP4 before shipping.
- Do not add a track-name prompt to the Live screen. Auto-ID plus manual rename is the workflow.
