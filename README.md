# Pocket Sim Engineer

Android telemetry and synchronized replay for Gran Turismo 7. Record a race through Chiaki-Up, tie each completed lap to its own video and telemetry file, and review the lap with charts, a track map, and a playhead that follows the video.

**Status:** v0.1 feature work in progress. Core protocol, capture spike, per-lap remux, session library, and the synchronized player are implemented. On-device verification is still pending.

## Documents

- [Product and technical spec](docs/specs/2026-11-10-pocket-sim-engineer.md)
- [Implementation plan](docs/superpowers/plans/2026-11-10-pocket-sim-engineer-spike.md)

## Architecture

```text
gt7-telemetry/
├── core/   # Kotlin JVM: protocol, lap detection, session codecs, CSV, remux math
└── app/    # Android: Compose UI, foreground services, MediaProjection, Media3
```

`core` has no Android imports, so a future desktop app can reuse it and a future Rust core can replace it behind the same interfaces.

## What works

- GT7 packet A Salsa20 decrypt and parse at ~60 Hz.
- Lap detection with plausible-time filtering.
- Foreground UDP telemetry service with Wi-Fi and wake locks.
- Arm-once recording that auto-starts when GT7 reports the car on track.
- 1080p60/720p60 H.264/HEVC screen capture and internal audio capture.
- Session and per-lap telemetry persistence under `files/sessions/<id>/`.
- Post-stop per-lap MP4 remux with 2 s pre-roll, 1 s post-roll, keyframe alignment, and audio muxing.
- Session library with grouping, rename, delete, and CSV/MP4 sharing.
- Lap player: Media3 video, speed/throttle/brake/RPM/gear charts, track map, sync nudge.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 36).

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Known gaps

- Thermal fallback from 1080p60 to 720p60 is not implemented yet.
- Audio silence is reported after the session stops; it is not cut off mid-recording.
- Packet C (steering, live lap timer) is not parsed yet.
- The desktop/Rust core and cloud backup remain future work.

## Remaining verification (needs a device)

Task 12 in the plan is blocked without an attached Pixel 10:

- Real GT7 packets at 60 packets/s for 30 s.
- Clean video (not black) and real game audio, or the silent-video warning.
- Two lap markers aligned with the visible line within a few frames.
- Notification, shade, lock/unlock, projection-stop, and service-lifecycle behavior.
- 10-minute 1080p60 thermal run and the 720p60 fallback.

## Key decisions

- Android only for now: Kotlin + Jetpack Compose.
- Two modules: `:core` (plain Kotlin JVM) and `:app` (Android).
- Capture path: Chiaki-Up fullscreen; the app records the display and internal game audio.
- Per-lap files are cut after the raw recording is finalized, because `MediaMuxer` writes the MP4 index only on stop.
- Telemetry: GT7 UDP packet A; packet C later.
- Local-first: no accounts, cloud, ads, or tracking in v0.1.
- License: GPLv3 + contributor CLA.
- Future: Rust core and a desktop app that reads the same session files.

## Target

- Pixel 10, Android 16.
- `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Package: `io.github.jlai403.pocketsimengineer`.
