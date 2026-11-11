# Pocket Sim Engineer — Product & Technical Spec

**Status:** Draft for spike  
**Date:** 2026-11-10  
**Owner:** jlai403  
**Working title:** Pocket Sim Engineer  
**Package:** `io.github.jlai403.pocketsimengineer`

---

## 1. Summary

Pocket Sim Engineer is an Android app for Gran Turismo 7 telemetry and synchronized replay. It records the game screen while listening to GT7's UDP telemetry, ties each completed lap to its own video file, and plays the video back with charts, a track map, and a telemetry cursor that follow the playhead.

The app is local-first and free/open source. A future desktop app will reuse the same portable session files and a Rust core.

## 2. Problem

GT7 exposes rich telemetry but no usable replay-analysis workflow on a phone. Drivers can watch a replay, or read telemetry, but not both together. Existing tools are PC-first and often require a capture card or a desktop Remote Play window.

Pocket Sim Engineer targets the phone-only workflow:

1. Play GT7 through Chiaki-Up on Android.
2. Let the app record the screen and telemetry simultaneously.
3. Review a lap with video and traces in sync.
4. Share the clip or export the data.

## 3. Target user

- Sim racers who play GT7 through Chiaki-Up on an Android phone.
- Drivers who want to review braking, throttle, gear, RPM, and line without a PC.
- Race-engineering-minded users who care about data and replay together.
- FOSS users who install from F-Droid or GitHub releases.

Primary device: Pixel 10, Android 16.

## 4. Goals

- Capture GT7 telemetry at ~60 Hz with no practical packet loss.
- Record replay video that is actually usable, not a black DRM surface.
- Produce one video file and one telemetry file per completed lap.
- Play a lap with video and charts synchronized within a few frames.
- Keep the app local-only, lightweight, and testable.
- Keep the telemetry/session core portable so it can become a Rust core for desktop.

## 5. Non-goals for v0.1

- Cloud accounts, leaderboards, social features.
- OBS overlays.
- Coach, optimal lap, segment analysis.
- Reference-lap comparison (v0.2).
- iOS, desktop, or web implementation.
- Integrating chiaki-ng or bundling any Remote Play client.
- Play Store distribution initially.
- 4K, HDR, or 120 fps recording.

## 6. Decisions already made

### Product and distribution

- Public, free/open-source app.
- Distribution: F-Droid + GitHub releases. Play Store is optional later.
- Two GitHub channels: `main` pushes publish a `preview-latest` prerelease; `v*` tags publish the latest stable release. The app checks its own channel once a day, verifies SHA-256, and hands the APK to `PackageInstaller`; Android always asks before installing. Installs from F-Droid or Play skip the in-app check so the store owns updates.
- Accepted trade-off: `REQUEST_INSTALL_PACKAGES` and the self-install path make Play Protect likely to warn or block the app. The alternative — linking to the APK and letting the browser install it — was rejected in favour of one-tap updates. Keep this documented for users and expect appeals if the block spreads.
- `versionCode = 1000 + git commit count`, so builds sort monotonically without hand-editing.
- License: GPLv3 + contributor CLA. Relicense to AGPLv3 only if chiaki-ng is ever linked.
- Working title: Pocket Sim Engineer. Final name may change before the first F-Droid release.
- Package: `io.github.jlai403.pocketsimengineer`.
- Local-first. No accounts, tracking, analytics, or cloud in v0.1.
- Future S3-compatible backup/upload is a later feature; v0.1 stays local.

### Platform

- Primary device: Pixel 10, Android 16.
- `minSdk 29` (Android 10), `targetSdk 36`, `compileSdk 36`.
- Kotlin + Jetpack Compose.
- Two Gradle modules: `:core` (plain Kotlin JVM, no Android) and `:app` (Android).
- Future Rust core must remain compatible with 16 KB page alignment.

### Capture

- v0.1 supports Chiaki-Up only. PXPlay is an untested fallback and is not a dependency.
- No chiaki-ng integration until after the MVP.
- The app records the display, not Chiaki-Up's internals.
- Chiaki-Up's on-screen controls and in-stream bar are the user's to hide: swipe down from the top edge, turn `On-Screen Controls` off (Chiaki-Up persists it), and let the bar fade before driving. The app cannot hide another app's UI, so the Record steps document it. Lap video contains only the game.
- Recording control: `Connect` starts the UDP listener with the saved IP and nothing else; `Start recording` is enabled only while decoded packets are arriving (a packet within 2 s). Start grants screen capture, then recording auto-starts when telemetry shows the car on track. `Stop` ends the recording and keeps the connection; `Disconnect` stops both. The telemetry notification shows pps and offers `Disconnect` from the shade.
- GT7 streams telemetry only while a race, time trial, or replay is on screen, so connect-and-verify happens on track. The Record card says so when no packets arrive.
- The telemetry foreground service holds a Wi-Fi lock while connected and takes the partial wake lock only while recording.
- No floating overlay in v0.1.
- Nothing from this app is drawn while recording: a toast or banner is composited into the display and would land in the lap video. Toasts are limited to the pre-recording window; lap and error feedback lives in the silent, low-importance notification and the library.

### Recording

- Continuous raw MP4 underneath.
- No internal recording segments; a process crash may corrupt the raw file, but completed per-lap files remain.
- Per-lap video files are produced by remux **after recording stops**. `MediaMuxer` writes the MP4 index at finalize, so an in-progress raw file cannot be cut lap-by-lap. If immediate per-lap files are required later, use fragmented MP4 or a segmenting recorder.
- Each lap clip includes about 2 s pre-roll and 1 s post-roll, with `lapStartOffsetMs` stored so playback starts at the line.
- Raw file is deleted after all laps split successfully. Keep it automatically if a split failed. User-facing "Keep raw" toggle.
- Free-space guard: stop recording and warn below ~2 GB.
- Video default: 1080p60 H.264 at ~10 Mbps. HEVC optional. Automatic fallback to 720p60 on sustained encoder drops or thermal throttling.
- Audio: internal game audio via `AudioPlaybackCapture`, AAC-LC 48 kHz stereo ~160 kbps. If capture is blocked or silent, continue with silent video and show a warning.
- Session/lap boundaries:
  - Start a session when telemetry shows the car on track.
  - Stop recording and close the session after 5 s off-track or 5 s without packets.
  - Notification Stop closes immediately.
  - Car-code change closes the session immediately.
  - Only completed laps with a plausible lap time become lap files.
  - Out laps and aborted laps do not become lap files.
- Library visually groups consecutive sessions with the same car and track when the gap is under ~10 minutes, while keeping separate session records on disk.

### Sync

- Use one monotonic clock domain: `System.nanoTime()` for telemetry receipt and video PTS.
- Automatic offset from the lap boundary, plus a manual nudge control (±500 ms, frame-step) saved per session.
- Video is the master clock during playback.
- Store the offset in session metadata so playback and export share it.

### Identity and library

- Track label: manual.
- Car name: automatic from a bundled car-code lookup table, fallback `Car #<code>`.
- Track map: generated from telemetry X/Z positions.
- Library: local sessions, rename/delete, best lap, per-lap playback.
- Export: CSV telemetry and Android share for MP4s.

### Data format

- Per-lap telemetry: versioned binary columnar file, fixed little-endian schema, plus JSON metadata.
- CSV is an export format, not the working format.
- No `Parcelable`, Java serialization, Android `Uri`, or Android-specific schema in the core or persisted format.

## 7. User flows

### First run

1. Install the APK.
2. Grant notifications and audio recording when prompted.
3. Enter the PS5 IPv4 address.
4. See "Waiting for telemetry."

### Record a session

1. Tap **Arm**.
2. Grant screen capture when Android asks.
3. Open Chiaki-Up and connect to the PS5.
4. Start driving. Recording starts when telemetry reports the car on track.
5. Drive laps. The notification shows lap count and recording state.
6. Return to the menu or tap **Stop**. Recording stops after 5 s off-track or immediately on Stop.
7. The app remuxes completed laps into per-lap files.

### Review a lap

1. Open the session library.
2. Pick a session and a lap.
3. Video plays; charts and track-map cursor follow the playhead.
4. Scrub the playhead; the video and telemetry move together.
5. Adjust sync with the nudge control if needed.

### Share

1. Share a lap MP4 through the Android share sheet.
2. Export a lap's telemetry as CSV.

## 8. Functional requirements

### 8.1 Telemetry ingest

- Bind a UDP socket to `0.0.0.0:33740`.
- Send a heartbeat byte `A` (`0x41`) to `<PS5-IP>:33739` every 100 ms.
- Filter received packets by source IP when a console IP is configured.
- Ignore datagrams shorter than 68 bytes.
- Decrypt Salsa20 and validate magic `0x47375330`.
- Drop invalid packets and count decode errors.
- Timestamp each accepted packet with `System.nanoTime()`.
- Surface packets/sec, decode errors, lap count, speed, gear, and connection state to the UI.
- Stop heartbeats and close the socket when the service stops.

### 8.2 Packet A decode

Packet A is 296 bytes. The spike uses packet A; packet C is added later.

Decryption:

- Key: first 32 bytes of `Simulator Interface Packet GT7 ver 0.0` (ASCII).
- `iv1` = little-endian `uint32` at offset `0x40`.
- `iv2` = `iv1 XOR 0xDEADBEAF` for packet A.
- Nonce = `[iv2 LE][iv1 LE]` (8 bytes).
- Decrypt the full datagram with Salsa20.
- Magic at offset `0x00` must be `0x47375330`.

Fields used in v0.1:

| Field | Offset | Type | Notes |
|---|---:|---|---|
| `position_x` | `0x04` | f32 | metres |
| `position_y` | `0x08` | f32 | metres |
| `position_z` | `0x0C` | f32 | metres |
| `rpm` | `0x3C` | f32 | engine RPM |
| `fuel` | `0x44` | f32 | litres |
| `speed` | `0x4C` | f32 | m/s |
| `tyre_temp_FL` | `0x60` | f32 | °C |
| `tyre_temp_FR` | `0x64` | f32 | °C |
| `tyre_temp_RL` | `0x68` | f32 | °C |
| `tyre_temp_RR` | `0x6C` | f32 | °C |
| `packet_id` | `0x70` | i32 | sequence |
| `lap_count` | `0x74` | i16 | current lap |
| `best_lap_time` | `0x78` | i32 | ms |
| `last_lap_time` | `0x7C` | i32 | ms |
| `flags` | `0x8E` | u16 | bit 0 = car on track |
| `gear` | `0x90` | u8 | low nibble = current gear |
| `throttle` | `0x91` | u8 | 0–255 |
| `brake` | `0x92` | u8 | 0–255 |
| `car_code` | `0x124` | i32 | car id |

All fields little-endian.

### 8.3 Packet C (later)

- Packet C is 368 bytes, heartbeat `C`, XOR constant `0xDEADBEEF`.
- Adds surface type, live lap timer, front-wheel steering angles, wheelbase, and car category.
- Do not ship packet C until offsets are verified against captured packets. Community docs disagree on some C layout details.

### 8.4 Session and lap detection

- A session starts on the first packet with the car-on-track flag set.
- A session closes after 5 s without on-track telemetry, after 5 s without packets, on a car-code change, or on manual Stop.
- A completed lap exists when `lap_count` increases.
- The completed lap number is `new_lap_count - 1`.
- The lap end time is the receipt time of the first packet with the new lap count.
- The lap time comes from `last_lap_time` when it is positive and plausible; otherwise the app computes it from sample timestamps.
- Ignore duplicate `packet_id` values and out-of-order samples.
- Out laps, incomplete laps, and laps without a plausible time do not get lap files.

### 8.5 Recording

- The app uses `MediaProjection` with a foreground service of type `mediaProjection`.
- The user grants capture once per session. The token is not persisted.
- Start the foreground service while the activity is visible.
- Create a `VirtualDisplay` at 1920×1080 (default) and feed a `MediaCodec` H.264 encoder surface.
- Mux video and audio into one raw MP4.
- Audio uses `AudioPlaybackCaptureConfiguration` with game/media usage and `AudioRecord` PCM, encoded as AAC-LC.
- The encoder requests a keyframe at least every 1 s.
- If audio capture yields no frames for 2 s, disable audio and surface a warning.
- If the encoder cannot sustain 60 fps for 10 s or the device reports severe thermal throttling, recreate the encoder at 720p60 and notify the user.
- Stop cleanly on notification Stop, projection stop, service destruction, or free-space exhaustion.

### 8.6 Per-lap remux

- After the recording stops, a background worker remuxes the relevant range of the finalized raw MP4 into `lap-XXX.mp4`.
- Include 2 s before the lap start and 1 s after the lap end.
- Store `lapStartOffsetMs` = 2000 ms (plus any keyframe alignment adjustment).
- Copy both video and audio tracks without re-encoding.
- If the start is not a keyframe, seek back to the preceding keyframe and increase `lapStartOffsetMs`.
- After all completed laps are remuxed successfully, delete the raw file unless "Keep raw" is enabled.
- If a remux fails, keep the raw file and mark the lap as "needs cut."

### 8.7 Playback

- Video is the master clock.
- Use Media3/ExoPlayer with per-lap clipping.
- Use a video-frame metadata listener or playback position to drive the telemetry cursor.
- Charts: speed, brake, throttle, gear, RPM.
- Track map: X/Z polyline with a position arrow.
- Scrub bar controls video and charts together.
- Sync nudge: ±500 ms, frame-step, saved per session.
- Show lap time, best lap, and session metadata.

### 8.8 Library

- List sessions newest first.
- Show track label, car name, lap count, best lap, and recording date.
- Rename a session.
- Delete a session and its files.
- Open a session; open a lap.
- Visually group sessions with the same car/track when the gap is under 10 minutes.
- Keep separate session records on disk.

### 8.9 Export

- Share a lap MP4 via Android share sheet.
- Export a lap's telemetry as CSV with a header row.
- No cloud upload in v0.1.

### 8.10 Settings

- PS5 IP address.
- Video resolution: 1080p60 default, 720p60 option.
- Codec: H.264 default, HEVC option.
- Bitrate: default 10 Mbps, adjustable 4–20 Mbps.
- Keep raw recording: off by default.
- Units: metric/imperial.
- Sync offset per session (not global).

## 9. Data model

### 9.1 Sample

In-memory sample fields:

```text
timeNanos: Long
speedMps: Float
throttle: Int        // 0..255
brake: Int           // 0..255
gear: Int            // -1 = neutral, 0 = reverse, 1..n
rpm: Float
positionX: Float
positionY: Float
positionZ: Float
lapCount: Int
lastLapTimeMs: Int
flags: Int
carCode: Int
fuel: Float
tyreTempFL/FR/RL/RR: Float
packetId: Int
steering: Float      // 0 for packet A; populated from C later
currentLapTimeMs: Int // 0 for packet A; populated from C later
```

### 9.2 Session metadata (`session.json`)

```json
{
  "schemaVersion": 1,
  "sessionId": "2026-11-10T18-42-11Z",
  "createdAt": "2026-11-10T18:42:11Z",
  "trackLabel": "Suzuka",
  "recordingStartNanos": 123456789,
  "carCode": 3474,
  "carName": "BRZ GT300 '21",
  "syncOffsetMs": 0,
  "rawFile": "raw.mp4",
  "audioFile": "audio.m4a",
  "rawDeleted": false,
  "keepRaw": false,
  "laps": [
    {
      "index": 1,
      "lapTimeMs": 123456,
      "startNanos": 123456789,
      "endNanos": 123456789,
      "sampleCount": 5400,
      "videoFile": "laps/lap-001.mp4",
      "videoStartOffsetMs": 2000,
      "syncOffsetMs": 0,
      "needsCut": false
    }
  ]
}
```

`recordingStartNanos` is the `System.nanoTime()` value immediately before the first captured frame. Remux uses it to map lap bounds into each file's PTS domain, including files whose muxer rebased timestamps to zero.

### 9.3 Lap metadata (`lap-001.json`)

```json
{
  "schemaVersion": 1,
  "index": 1,
  "lapTimeMs": 123456,
  "startNanos": 123456789,
  "endNanos": 123456789,
  "sampleCount": 5400,
  "videoFile": "laps/lap-001.mp4",
  "videoStartOffsetMs": 2000,
  "syncOffsetMs": 0,
  "needsCut": false
}
```

### 9.4 Binary telemetry (`lap-001.gt7lap`)

Header:

| Offset | Field | Type | Value |
|---:|---|---|---|
| 0 | magic | 4 bytes | `G7LP` |
| 4 | version | u16 | 1 |
| 6 | headerSize | u16 | 32 |
| 8 | sampleCount | u32 | n |
| 12 | sampleRateHz | u16 | 60 |
| 14 | reserved | 2 bytes | 0 |
| 16 | startNanos | i64 | first sample time |
| 24 | endNanos | i64 | last sample time |

After the header, fixed little-endian arrays in this order:

1. `timeNanos` i64
2. `speedMps` f32
3. `throttle` u8
4. `brake` u8
5. `gear` i8
6. `rpm` f32
7. `positionX` f32
8. `positionY` f32
9. `positionZ` f32
10. `lapCount` i16
11. `lastLapTimeMs` i32
12. `bestLapTimeMs` i32
13. `flags` u16
14. `carCode` i32
15. `fuel` f32
16. `tyreTempFL` f32
17. `tyreTempFR` f32
18. `tyreTempRL` f32
19. `tyreTempRR` f32
20. `packetId` i32
21. `steering` f32
22. `currentLapTimeMs` i32

No compression in v0.1.

### 9.5 Session directory

```text
<app files>/sessions/<sessionId>/
  session.json
  raw.mp4                 # removed after successful split unless kept
  laps/
    lap-001.mp4
    lap-001.gt7lap
    lap-001.json
    lap-002.mp4
    lap-002.gt7lap
    lap-002.json
```

## 10. Architecture

### 10.1 Modules

```text
pocket-sim-engineer/
├── core/     # Kotlin JVM, no Android dependencies
└── app/      # Android app, Compose UI, services, MediaProjection
```

### 10.2 `:core`

Responsibilities:

- GT7 protocol constants, decryption, packet parsing.
- Sample model and buffers.
- Lap detection.
- Session/lap metadata and binary codec.
- CSV export.
- Clock abstraction.

No Android imports. It must compile and test on the JVM. A future desktop app can depend on it immediately, and a future Rust core can replace it behind the same interfaces.

Key interfaces:

```kotlin
interface Clock {
    fun nanoTime(): Long
}

fun interface TelemetryDecoder {
    fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult
}

sealed interface DecodeResult {
    data class Success(val sample: Sample) : DecodeResult
    data class Invalid(val reason: String) : DecodeResult
}

fun interface LapDetector {
    fun onSample(sample: Sample): LapBoundary?
}

data class LapBoundary(
    val lapNumber: Int,
    val startNanos: Long,
    val endNanos: Long,
    val lapTimeMs: Int,
)
```

### 10.3 `:app`

Responsibilities:

- Compose UI.
- Foreground service and UDP socket.
- MediaProjection, MediaCodec, MediaMuxer, AudioRecord.
- Notification/recording control.
- Android storage and sharing.
- Session library and playback.

Video capture and Android lifecycle never enter `:core`.

### 10.4 Future Rust/desktop

- Rust core exposes coarse-grained functions:
  - `decode(datagram, received_at_nanos) -> DecodeResult`
  - `detect_lap(samples) -> Option<LapBoundary>`
  - `delta(lap, reference) -> Vec<DeltaPoint>`
- Kotlin interfaces stay stable and delegate to Rust via UniFFI/JNI.
- Shared golden packet fixtures and session files prove compatibility.
- Pixel 10 requires 16 KB page alignment for native libraries; use NDK r28+ / current `cargo-ndk`.

## 11. Android platform requirements

Permissions:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS`
- `RECORD_AUDIO`
- `WAKE_LOCK`

Behavior:

- Start the foreground service while the activity is visible.
- Register `MediaProjection.Callback` and stop recording on `onStop`.
- Stop recording when the screen locks (Android 15+ behavior).
- Handle notification permission denial: recording may still run, but the user gets no notification controls until permission is granted.
- Handle `RECORD_AUDIO` denial: fall back to silent video.
- Acquire a Wi-Fi lock and a partial wake lock while telemetry is active.
- Use app-specific internal storage for v0.1; no broad storage permission. This keeps `adb exec-out run-as <pkg> tar` usable for spike verification.
- Use `FileProvider` for sharing.
- Do not request `SYSTEM_ALERT_WINDOW` in v0.1.

Network:

- Target SDK 36 keeps local network access implicit via `INTERNET`.
- When the app later targets SDK 37+, add `ACCESS_LOCAL_NETWORK` and request it at runtime.

## 12. Error handling and edge cases

| Condition | Behavior |
|---|---|
| Port 33740 in use | Show "another telemetry app is holding the port"; stop retrying until user retries. |
| No packets after connect | Show waiting state; keep sending heartbeats for 30 s, then stop and ask to check IP/network. |
| Wrong PS5 IP | Ignore packets from other sources if IP is set; show decode-error/source-mismatch counts. |
| Invalid magic / short packet | Drop; increment decode error counter; never crash. |
| Duplicate packet ID | Drop duplicate sample. |
| Lap count resets | Treat as a new session; do not create a negative lap. |
| Replay scrubbing | May create duplicate/gapped samples; mark the session `replay` and avoid lap files with implausible times. |
| Pause/menu >5 s | Stop recording and close the session. |
| Car change | Close the session immediately. |
| MediaProjection stopped | Stop recording and finalize the MP4. |
| Audio blocked/silent | Continue silent; show warning; keep video/telemetry. |
| Encoder failure | Stop recording; keep raw if possible; show error. |
| Thermal throttling | Drop to 720p60; notify user. |
| Free space <2 GB | Stop recording; keep existing files. |
| Process death | Raw MP4 may be unplayable; completed per-lap files remain; show a recovery warning on next launch. |
| Remux failure | Keep raw; mark lap "needs cut"; allow retry. |
| Sync offset wrong | Manual nudge; save per session. |

## 13. Performance and thermal budget

- Telemetry: 60 packets/s × 368 B ≈ 22 KB/s; negligible.
- Video: 1080p60 H.264 at 10 Mbps ≈ 4.5 GB/hour.
- Audio: AAC 160 kbps ≈ 72 MB/hour.
- Per 90 s lap: roughly 110 MB at 1080p60/10 Mbps.
- UI: update Compose state at 10–20 Hz; downsample traces to about one point per screen pixel.
- Charts: draw with `Canvas`/`Path`; avoid per-frame allocations.
- Service: one dedicated telemetry thread/coroutine; video encoding on a background thread.
- Thermal test: 10 minutes at 1080p60 with Chiaki-Up decoding. If it throttles, default to 720p60.

## 14. Privacy and security

- No analytics, tracking, accounts, or cloud in v0.1.
- Local network only for telemetry.
- Recordings are local app files.
- Audio capture may include game audio and party chat; warn the user before first recording.
- S3 credentials, when added later, must be stored in Android Keystore/EncryptedSharedPreferences and never logged.
- F-Droid build must have no proprietary dependencies, no ads, no tracking, and reproducible build metadata.
- GPLv3 + CLA; source available.

## 15. Acceptance criteria

### Spike

- Decodes real GT7 packet A at ~60 packets/s for 30 s.
- Records a clean 1080p60 MP4 of Chiaki-Up (not black, no client UI or controls).
- Records internal audio, or correctly falls back to silent video with a warning.
- Detects two consecutive laps and writes two lap markers with plausible lap times.
- Lap markers land within a few frames of the visible lap boundary after calibration.
- Service and recording survive switching from the app to Chiaki-Up.
- 10 minutes at 1080p60 without unacceptable thermal throttling.
- Raw MP4 finalizes cleanly on Stop.

### v0.1

- Session library, per-lap video + telemetry, synchronized playback, charts, track map.
- CSV export and Android share.
- F-Droid-compatible build.
- All v0.1 functional requirements above.

## 16. Future work

- Reference-lap comparison (v0.2).
- Segments, coach, optimal lap.
- Packet C parsing (steering, live lap timer).
- Replay-mode recording from GT7 replays.
- Video import (OBS/capture card).
- UVC capture-card support.
- S3-compatible backup/upload.
- Desktop app with Rust core, reading the same session files.
- Floating overlay controls.
- HEVC default if thermal tests allow.

## 17. Open questions

- Final app name (working title: Pocket Sim Engineer).
- Exact final package namespace (currently `io.github.jlai403.pocketsimengineer`).
- F-Droid metadata, privacy policy, anti-feature declarations, reproducible build setup.
- Exact binary schema/version header details before first release.
- S3 credentials UX and sync semantics.
- Whether an `outlap.mp4` file is ever wanted.
- Desktop stack and recording source (deferred).
