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
- The playhead opens at the clip's last frame, with the anchor suggested there; the user verifies the line or rewinds and re-anchors.
- A timeline scrubber covers the whole clip; a frame ruler (about 6 dp per frame, one haptic tick per frame) and ±1 frame buttons provide precision.
- Fine steps use exact seeks; transport is play/pause and ±10 s. Paused seeks keep the requested position rather than polling the player.
- A clip shorter than the lap is blocked with the missing duration.
- The computed start frame is shown with a **Check start frame** action; the user confirms it shows the line. With a single anchor the app cannot prove which lap the clip contains, so wrong-lap detection is visual.
- The imported original is kept only during sync and deleted after a successful trim or an explicit discard.

### Identity and library

- Track name is inferred from lap coordinates and the bundled racing lines; manual rename overrides it. Ambiguous matches stay `Unknown` until the user picks from the ranked candidates.
- Car name is `Car #<code>` until a bundled lookup exists.
- Library: flat lap cards with the lap time as the hero number, a per-track `Fastest lap` badge or delta, and a driver shown only when it is not the owner. Swipe right edits, swipe left confirms a delete, and pull to refresh reloads. Filters (all / with replay / without replay, track) and sort (newest / fastest / track) sit in one toolbar; the owner chip and links sit in the header. The edit sheet changes the track, the driver, and the replay.
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

1. Open **Record**, connect to GT7.
2. Drive; after crossing the start line, tap **Capture this lap**.
3. The app saves the lap at the next crossing and shows its time.
4. Optionally open the lap later and add a replay.

### Record and import the replay

1. In GT7 Time Trial, open the single-lap replay with HUD on and the race camera.
2. PS5 Create → Start Recording before the lap, Stop after the line. Keep a few seconds of lead-in.
3. Media Gallery → Upload to PlayStation App.
4. PS App → Download, or Share → Pocket Sim Engineer.
5. PSE: open the lap → the replay source button → **Files**, or pick the lap after a share.
6. The playhead opens at the clip end with a suggested anchor; drag the timeline or the frame ruler, or use ±1 frame to reach the lap-end crossing, then **Set anchor at this frame**.
7. **Check start frame**, preview, and **Save clip**.

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
- Gate candidates by length (4%) and box (centre 120 m, extents 20%).
- Rank survivors by the share of lap positions within 25 m of the bundled racing line.
- A lap running a line backwards is named after the reverse configuration when one exists.
- Apply a name only when the best line covers at least 90% of the lap and leads the runner-up by 0.5 points; collisions that share a line stay `Unknown`.
- Offer the ranked candidates in the library, with free-text naming as the fallback.
- Backfill `Unknown` laps from stored telemetry once per signature version.
- Manual rename always wins.

### 8.5 Replay import

- Accept `ACTION_SEND` with `video/*` and the SAF `OpenDocument` picker.
- The share picker lists every lap; a lap that already has a replay is marked and can be replaced.
- Copy into `sessions/<id>/laps/lap-<n>-source.mp4`.
- Record `sourceFile` in lap metadata.
- Reject unreadable URIs with a clear message; SAF remains available.
- An interrupted replacement import keeps the current clip playable and offers resume.
- One replay source button opens a menu: **Files** (the SAF picker) or **PlayStation App** (`com.scee.psxandroid`, or its store listing when it is not installed).
- The button reads **Add replay**, **Replace replay**, or **Choose another clip** to match the lap's state; a pending clip adds a separate **Resume sync** action.

### 8.6 Trim

- `TrimPlan` rejects a clip shorter than the lap or an anchor past the end.
- Seek to the previous video keyframe and copy samples into a new MP4.
- Store the keyframe-to-line lead-in in `leadInMs` (JSON key `videoStartOffsetMs`); playback subtracts it.
- Delete the source after `attachClip` succeeds.
- Replacing a clip swaps the new file in only after the trim succeeds; the old clip is restored if the swap fails.
- `attachClip` resets `syncOffsetMs` and deletes a superseded clip that no other lap shares.
- Discard deletes the source and clears `sourceFile`; an existing clip keeps playing.

### 8.7 Playback

- Media3 ExoPlayer; exact seeks for frame stepping, and the rendered frame drives the cursor.
- `PlaybackCursor.telemetryMs(positionMs, leadInMs, syncOffsetMs, lapTimeMs)` maps video to lap time; `clipMs(lapMs, leadInMs, syncOffsetMs)` is its inverse.
- The sync screen's slider and frame ruler span the whole clip.
- The lap screen's slider and frame ruler span only the lap; play/pause, ±10 s, the ruler, and ±1 frame control the replay, and the charts and track map follow the rendered frame while paused.
- Charts and track map follow the cursor.
- Legacy screen-recorded clips keep their stored offsets.

### 8.8 Library

- `SessionStore.summaries()` flattens sessions into lap cards with `hasReplay` and `replayPending`.
- Filters and sorts are pure functions in `:core`; `fastestPerTrack()` marks one quickest lap per track label.
- The fastest badge needs two or more laps on that track and is computed from all laps, never the filtered list.
- Pull to refresh reloads summaries; the spinner does not appear for automatic reloads after an edit.
- Swipe right opens the edit sheet; swipe left opens the delete confirmation after the card springs back.
- The edit sheet writes the track label, the per-lap driver, and the replay source. `Add a replay` and `Replace replay` open the same source menu as the lap screen, and `Resume sync` reopens the pending clip.
- Delete a lap; delete the session when its last lap goes.

### 8.9 Export

- Share the trimmed MP4 through the Android share sheet.
- Export CSV from the lap telemetry.

### 8.10 Settings

- PS5 IPv4 address, driver profile, imperial units, update channel, build identity, diagnostics.
- No video quality, codec, bitrate, or keep-raw settings.

### 8.11 Driver profiles

- One owner profile is the default for new captures: PSN ID, GT nickname, and ISO country code. No avatar.
- A local profile book keeps the owner and every driver fetched or entered for a lap.
- Fetch from the public GT GridStats player page only when the user taps **Fetch profile**; one request per tap, no crawling, retries, or background refresh.
- Manual name and 2-letter country entry is available when GT GridStats has no profile or the network fails.
- Each lap stores `driverPsnId`. New captures use the owner at capture time; existing laps stay unassigned until changed.
- The lap screen's **Driver** card selects a cached driver, adds one, or clears it. Changing the driver does not touch video or telemetry.
- Library cards show the lap's driver only when it differs from the owner; the header shows the owner and opens GT GridStats or GTSH-Rank. The lap screen's Driver card opens the same links for the assigned driver.
- Without an owner, the library shows **Add GT profile**.

## 9. Data model

### 9.1 Sample

`timeNanos`, speed m/s, throttle/brake 0–255, gear, RPM, X/Y/Z position, lap count, best/last lap time, flags, car code, fuel, tyre temperatures, packet id, steering, current lap time.

### 9.2 Session metadata (`session.json`)

`schemaVersion`, `sessionId`, `createdAt`, `trackLabel`, `recordingStartNanos`, `carCode`, `carName`, `syncOffsetMs`, `rawFile`, `audioFile`, `rawDeleted`, `keepRaw`, `laps`.

New captures use `rawFile = ""`, `rawDeleted = true`, no audio file, and one lap. Legacy fields stay for old sessions.

### 9.3 Lap metadata (`lap-001.json`)

`schemaVersion`, `index`, `lapTimeMs`, `startNanos`, `endNanos`, `sampleCount`, `videoFile`, `leadInMs`, `syncOffsetMs`, `needsCut`, `sourceFile`, `driverPsnId`.

`leadInMs` serializes as `videoStartOffsetMs` so existing sessions keep loading. `videoFile` is blank until a replay is attached. `sourceFile` is set while an imported clip awaits sync. `driverPsnId` is null for legacy laps and for new laps captured without an owner.

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
- `ui/` — Record, Library, Lap player, Import picker, Import sync, Settings.
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
- Ambiguous track: the lap stays `Unknown` and the library offers the ranked candidates.
- Laps captured before track identification existed are named once from stored telemetry.
- Clip shorter than the lap: Save is blocked with the missing duration.
- Unreadable share URI: clear message; use the file picker.
- Process death during sync: `sourceFile` remains; the lap offers **Resume sync**.
- Replacing a clip: the current replay plays until **Save clip** succeeds; discard or a failed trim leaves it attached.
- Legacy lap with no clip: shown as **No replay**, charts still work.
- Delete a lap removes its telemetry, trimmed clip, and pending source.

## 13. Performance and thermal budget

- Telemetry only: one UDP socket, 60 packets/s, no video decode during capture.
- Video work happens only in the import screen and playback.
- Trim copies compressed samples; no re-encode.
- Imported originals are deleted after sync to bound storage.

## 14. Privacy and security

- Local-only. PS5 IP stays in app preferences.
- No analytics, accounts, or background cloud calls.
- The optional driver-profile lookup sends only the PSN ID to gt-gridstats.com when the user taps Fetch; profile data stays on-device.
- Imported videos stay in app-private storage until deleted.
- In-app updates verify SHA-256 and always ask the user before installing.

## 15. Acceptance criteria

- A real GT7 session decodes at ~60 pps with no decode errors, including when another telemetry app is running.
- A fetched owner profile shows the name and flag in Settings and above the library; both external links open the matching profile page.
- A new capture inherits the owner as its driver; an existing lap stays unassigned until the lap screen's Driver card sets a cached or manual driver, and the library card reflects it.
- `Capture this lap` saves exactly one complete lap and rejects a mid-lap connect.
- A lap without a replay opens with charts and map.
- A PS App clip imports through share and SAF, copies into app storage, and can be trimmed.
- The sync screen opens at the clip end with a suggested anchor, and frame-stepping reaches the lap-end frame; the trim is one lap long.
- A clip shorter than the lap is blocked.
- After save, the trimmed clip plays with the cursor aligned to the lap.
- The lap screen's frame ruler and ±1 frame move the playhead one frame and the charts and map follow while paused.
- The replay source button offers Files and the PlayStation App in every lap state.
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
