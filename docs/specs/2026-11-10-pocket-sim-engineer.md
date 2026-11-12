# Pocket Sim Engineer — Product & Technical Spec

**Status:** v0.2 — telemetry-only capture plus PS App replay import
**Last updated:** 2026-11-11

## 1. Summary

Pocket Sim Engineer records Gran Turismo 7 telemetry for **one lap at a time**, then optionally attaches a single-lap replay clip and syncs it with one visible anchor. The phone never records video, never captures audio, and never runs Remote Play or Chiaki. Video comes from the PS5's own replay capture, retrieved through the PlayStation App (or any MP4 through the same importer).

## 2. Problem

Remote Play screen capture couples recording quality to the PS5's Wi-Fi uplink and the phone's encoder. The result stutters, heats the phone, and needs MediaProjection and audio-capture permissions. A telemetry-only app plus a console-side replay is cleaner, and GT7's lap time gives an exact ruler for synchronization.

## 3. Target user

A GT7 player reviewing their own hotlaps on a Pixel-class Android phone. They can enter the PS5's IPv4 address, drive a Time Trial, record a replay on the console, and download it through the PS App.

## 4. Goals

- Capture one complete lap of GT7 telemetry, from start/finish line to start/finish line.
- Keep telemetry useful without any video: charts, track map, lap time, CSV.
- Import a single-lap replay clip and trim it to the lap.
- Sync video to telemetry with one frame-accurate anchor.
- Stay local-first: no accounts, cloud, ads, tracking, or analytics.

## 5. Non-goals

- Live video, screen capture, Remote Play, Chiaki integration, or phone audio capture.
- Multi-lap session video or a continuous session VOD.
- Burning telemetry overlays into exported video.
- Automatic replay retrieval from YouTube, Twitch, or the PS App API.
- Multi-user accounts, cloud backup, or a desktop app in v0.2.

## 6. Decisions

### Product and distribution

- Public, free/open-source app; GPLv3 + contributor CLA.
- Distribution: F-Droid + GitHub releases. Play Store optional later.
- Preview channel on every `main` push; stable on `v*` tags. In-app update downloads the APK, verifies SHA-256, and asks Android to install. `REQUEST_INSTALL_PACKAGES` is deliberate; Play Protect warnings are accepted.
- `versionCode = 1000 + git commit count`.
- Package `io.github.jlai403.pocketsimengineer`; working title Pocket Sim Engineer.
- Local-first. No cloud in v0.2.

### Platform

- Pixel 10, Android 16; `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Kotlin + Jetpack Compose.
- Two modules: `:core` (plain Kotlin JVM) and `:app` (Android).

### Capture

- The app records **telemetry only**.
- A capture is one lap: the app saves the lap in progress from its last start/finish crossing to the next crossing.
- `Capture this lap` is enabled only when telemetry is connected and a start crossing has been observed. Connecting mid-lap disables it until the next line.
- Each saved lap is a standalone record. New captures use one session containing one lap; legacy multi-lap sessions remain readable.
- The foreground telemetry service holds a Wi-Fi lock and a partial wake lock while connected.

### Replay workflow

- GT7 Time Trial replays are single-lap. Record the replay with the PS5 Create menu, HUD on, race camera, around the lap with a few seconds of lead-in and run-out.
- PS5 capture resolution must be 1080p (not 4K) for PS App upload.
- Upload manually: Media Gallery → clip → **Upload to PlayStation App**.
- PS App: **Download** or **Share via** to Pocket Sim Engineer. The app copies the file into its own storage.
- PS App limits: videos under 3 minutes, ≤1920×1080, non-4K, available for 14 days. Laps longer than 3 minutes cannot use this path; use a USB capture or a broadcast MP4 through the same importer.
- The importer accepts any MP4 with a video track; H.264/AAC is the expected format. No re-encode.

### Sync

- One anchor per clip, at the **lap-end** start/finish line crossing.
- The GT7 lap time (`lastLapTimeMs`) is the ruler: `trimIn = anchor − lapTime`, `trimOut = anchor`.
- Video is the master clock during playback. The mapping is linear for the whole lap, so anchor error does not accumulate.
- Frame-step uses exact seeks and the rendered frame time; fine nudges are ±1/±10/±100 ms.
- A clip shorter than the lap is blocked with the missing duration.
- The computed start frame is shown with a **Jump to start** action; the user confirms it shows the line. With a single anchor the app cannot prove which lap the clip contains, so wrong-lap detection is visual.
- The imported original is kept only during sync and deleted after a successful trim or an explicit discard.

### Identity and library

- Track name is inferred from lap coordinates using a bundled signature table; manual rename overrides it. Ambiguous matches stay `Unknown`.
- Car name is `Car #<code>` until a bundled lookup exists.
- Library: flat lap cards, filters (all / with replay / without replay, track), sort (newest / fastest / track), lap time prominent.
- Track map and charts are generated from telemetry positions.
- Export: CSV telemetry and Android share of the trimmed MP4.

### Data format

- Per-lap telemetry: versioned binary columnar file plus JSON metadata.
- CSV is an export format, not the working format.
- No Android types in `:core` or in persisted files.

## 7. User flows

### First run

1. Install the APK.
2. Grant notifications.
3. Enter the PS5 IPv4 address.
4. Connect and see the live stream card.

### Capture a lap

1. Open **Live**, connect to GT7.
2. Drive; after crossing the start line, tap **Capture this lap**.
3. The app saves the lap at the next crossing and shows its time.
4. Optionally open the lap later and add a replay.

### Record and import the replay

1. In GT7 Time Trial, open the single-lap replay with HUD on and the race camera.
2. PS5 Create → Start Recording before the lap, Stop after the line. Keep a few seconds of lead-in.
3. Media Gallery → Upload to PlayStation App.
4. PS App → Download, or Share → Pocket Sim Engineer.
5. PSE: open the lap → **Add replay**, or pick the lap after a share.
6. Frame-step to the lap-end crossing, **Set anchor at this frame**.
7. Confirm the computed start frame shows the line, preview, and **Save clip**.

### Review

1. Open a lap from the library.
2. Charts and track map are always available.
3. With a replay, the video plays and the cursor follows it.
4. Share the trimmed MP4 or export CSV.

## 8. Functional requirements

### 8.1 Telemetry ingest

- Bind UDP `0.0.0.0:33740`; heartbeat to `<PS5-IP>:33739` every 100 ms.
- Heartbeat byte `C`; accept A, B, `~`, and C packets.
- Filter by source IP when configured; count foreign packets.
- Ignore datagrams shorter than 68 bytes.
- Decrypt Salsa20 and validate magic `0x47375330`.
- Timestamp accepted packets with `System.nanoTime()`.
- Surface pps, decode errors, last decode failure, lap count, speed, gear, flags, and connection state.
- Stop heartbeats and close the socket when the service stops.

### 8.2 Packet formats

- A: 296 bytes, XOR `0xDEADBEAF`.
- B: 316 bytes, XOR `0xDEADBEEF`.
- `~`: 344 bytes, XOR `0x55FABB4F`.
- C: 368 bytes, XOR `0xDEADBEEF`.
- All formats are strict supersets of A; the base fields keep their offsets. Parse the A fields from every format.

### 8.3 Capture

- Buffer samples for the lap in progress (15-minute cap).
- `Capture this lap` records the expected lap start; the next boundary with that start is saved.
- Reject a boundary whose start was not observed or does not match.
- Save exactly one session with one lap; the lap has no video.
- Disconnect or service stop cancels a pending capture.

### 8.4 Track identification

- Compute lap length and X/Z bounding box from positions; skip steps over 200 m.
- Match against bundled signatures: length within 4%, box centre within 120 m, extents within 20%.
- Apply only a unique match; leave ambiguous laps `Unknown`.
- Manual rename always wins.

### 8.5 Replay import

- Accept `ACTION_SEND` with `video/*` and the SAF `OpenDocument` picker.
- Copy into `sessions/<id>/laps/lap-<n>-source.mp4`.
- Record `sourceFile` in lap metadata.
- Reject unreadable URIs with a clear message; SAF remains available.

### 8.6 Trim

- `TrimPlan` rejects a clip shorter than the lap or an anchor past the end.
- Seek to the previous video keyframe and copy samples into a new MP4.
- Store the keyframe-to-line offset in `videoStartOffsetMs`; playback subtracts it.
- Delete the source after `attachClip` succeeds.
- Discard deletes the source and clears `sourceFile`.

### 8.7 Playback

- Media3 ExoPlayer; exact seeks for frame stepping.
- `PlaybackCursor.telemetryMs(positionMs, videoStartOffsetMs, syncOffsetMs, lapTimeMs)` maps video to lap time.
- Charts and track map follow the cursor.
- Legacy screen-recorded clips keep their stored offsets.

### 8.8 Library

- `SessionStore.summaries()` flattens sessions into lap cards with `hasReplay` and `replayPending`.
- Filters and sorts are pure functions in `:core`.
- Delete a lap; delete the session when its last lap goes.

### 8.9 Export

- Share the trimmed MP4 through the Android share sheet.
- Export CSV from the lap telemetry.

### 8.10 Settings

- PS5 IPv4 address, imperial units, update channel, build identity, diagnostics.
- No video quality, codec, bitrate, or keep-raw settings.

## 9. Data model

### 9.1 Sample

`timeNanos`, speed m/s, throttle/brake 0–255, gear, RPM, X/Y/Z position, lap count, best/last lap time, flags, car code, fuel, tyre temperatures, packet id, steering, current lap time.

### 9.2 Session metadata (`session.json`)

`schemaVersion`, `sessionId`, `createdAt`, `trackLabel`, `recordingStartNanos`, `carCode`, `carName`, `syncOffsetMs`, `rawFile`, `audioFile`, `rawDeleted`, `keepRaw`, `laps`.

New captures use `rawFile = ""`, `rawDeleted = true`, no audio file, and one lap. Legacy fields stay for old sessions.

### 9.3 Lap metadata (`lap-001.json`)

`schemaVersion`, `index`, `lapTimeMs`, `startNanos`, `endNanos`, `sampleCount`, `videoFile`, `videoStartOffsetMs`, `syncOffsetMs`, `needsCut`, `sourceFile`.

`videoFile` is blank until a replay is attached. `sourceFile` is set while an imported clip awaits sync.

### 9.4 Binary telemetry (`lap-001.gt7lap`)

Versioned little-endian columnar file; one row per 60 Hz sample.

### 9.5 Session directory

```text
files/sessions/<id>/
├── session.json
└── laps/
    ├── lap-001.gt7lap
    ├── lap-001.json
    ├── lap-001.mp4            # trimmed replay, once attached
    └── lap-001-source.mp4     # imported original, only during sync
```

## 10. Architecture

### 10.1 Modules

- `:core` — protocol/decrypt/decode, lap detection, capture buffering, track signatures, clip sync math, session codecs, CSV, remux planning, playback cursor. No Android imports.
- `:app` — Compose UI, foreground telemetry service, replay copy/trim, Media3 playback, updates.

### 10.2 `:core`

- `protocol/` — `Gt7Protocol`, `Gt7Crypto`, `Gt7Decoder`, `PacketAParser`, `DecodeFailureLog`.
- `lap/` — `LapDetector` with `currentLapStartNanos` / `currentLapStartObserved`.
- `capture/` — `LapSampleBuffer`, `CaptureState`, `LapCaptureCoordinator`, `LapSink`.
- `track/` — `TrackSignature`, `TrackIdentifier`, `TrackSignatureCodec`.
- `clip/` — `ClipSync`, `SyncValidator`, `TrackProgress`, `TrimPlan`.
- `session/` — `SessionMetadata`, `LapMetadata`, `LapSummary`, `SessionStore`, `SessionWriter`, codecs, `RemuxPlanner`.

### 10.3 `:app`

- `service/` — UDP listener, telemetry state, foreground service, capture actions.
- `capture/` — `LapSaver`.
- `track/` — `TrackCatalog` (loads the bundled asset).
- `replay/` — `ReplayStore`, `ClipTrimmer`.
- `ui/` — Live, Library, Lap player, Import picker, Import sync, Settings.
- `share/` — MP4 and CSV sharing.

### 10.4 Future Rust/desktop

The core is plain Kotlin and the session files are portable. A Rust core or desktop reader can replace it behind the same data model.

## 11. Android platform requirements

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `REQUEST_INSTALL_PACKAGES`.
- No `RECORD_AUDIO` and no `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
- `TelemetryService` is a `dataSync` foreground service.
- MainActivity accepts `ACTION_SEND` for `video/*` with `singleTop`.
- FileProvider shares exported files.

## 12. Error handling and edge cases

- Decode failures are counted and the first distinct size/reason is logged and shown in notification details.
- Connected mid-lap: capture is disabled until the next line.
- Clip shorter than the lap: Save is blocked with the missing duration.
- Unreadable share URI: clear message; use the file picker.
- Process death during sync: `sourceFile` remains; the lap offers **Resume sync**.
- Legacy lap with no clip: shown as **No replay**, charts still work.
- Delete a lap removes its telemetry, trimmed clip, and pending source.

## 13. Performance and thermal budget

- Telemetry only: one UDP socket, 60 packets/s, no video decode during capture.
- Video work happens only in the import screen and playback.
- Trim copies compressed samples; no re-encode.
- Imported originals are deleted after sync to bound storage.

## 14. Privacy and security

- Local-only. PS5 IP stays in app preferences.
- No analytics, accounts, or cloud calls.
- Imported videos stay in app-private storage until deleted.
- In-app updates verify SHA-256 and always ask the user before installing.

## 15. Acceptance criteria

- A real GT7 session decodes at ~60 pps with no decode errors, including when another telemetry app is running.
- `Capture this lap` saves exactly one complete lap and rejects a mid-lap connect.
- A lap without a replay opens with charts and map.
- A PS App clip imports through share and SAF, copies into app storage, and can be trimmed.
- Frame-step reaches the lap-end frame; the trim is one lap long.
- A clip shorter than the lap is blocked.
- After save, the trimmed clip plays with the cursor aligned to the lap.
- Discard deletes the imported copy and leaves the telemetry.
- `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passes.

## 16. Future work

- Two-anchor sync or HUD OCR for automatic wrong-lap detection.
- Burned-in telemetry overlays.
- Bundled car-name lookup.
- Track signature learning from user-named laps.
- USB/broadcast import helper.
- Cloud backup / desktop reader.

## 17. Open questions

- Should long laps (>3 min) get a guided split-recording workflow?
- Should the app request packet C explicitly for the live lap timer, or stay on A base fields?
- Should track signatures include reverse-layout direction detection?
