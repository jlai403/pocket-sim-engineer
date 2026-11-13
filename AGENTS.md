# AGENTS.md — Pocket Sim Engineer

Android app for GT7 telemetry-only lap review. Kotlin + Jetpack Compose, `minSdk 29`,
`targetSdk 36`, package `io.github.jlai403.pocketsimengineer`.

## Commands

Full check before merging:

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused unit tests:

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapPlayerScreenTest'
```

## Modules

- `:core` — pure Kotlin: samples, sessions, playback cursor, clip trim math, track identification. No Android UI.
- `:app` — Compose UI, capture service, replay import/playback, settings, updater.

## Key conventions

- Telemetry-only: no screen capture, MediaProjection, phone audio, Remote Play, or burned-in overlays.
- Local-first: no accounts, analytics, or background cloud calls.
- Replay clips live under `sessions/<id>/laps/lap-NNN.mp4`; telemetry is `lap-NNN.gt7lap`.
- Library is a flat feed: no cards/radii, `Hairline` dividers, `RowPressed` for pressed rows.
- `DESIGN.md` is the visual/behaviour reference; `docs/specs/2026-11-10-pocket-sim-engineer.md` is the functional spec.

## Replay storage (added this session)

- New imports are trimmed, then compacted with the Settings replay profile before `attachClip`.
- Settings live in `SettingsStore`: `replayPreset` (default `Balanced`), `replayKeyframes` (`Fast`),
  `replayAudio` (`Stereo`), `replayResolution` (`FullHd`), `replayCodec` (`Hevc`).
- `app/.../settings/ReplayCompression.kt` defines the enums, resolved `ReplayProfile`, captions, and size estimates.
- `ReplayCompressionControls` (`app/.../ui/`) is shared by Settings (defaults) and the sync screen (per-lap override, session-only, with a live `≈ N MB` estimate).
- `app/.../replay/VideoTranscoder.kt` re-encodes with Media3 Transformer (`media3-transformer:1.5.1`).
  On failure the trimmed original is kept and the save shows a "Compaction failed" toast.
- `LapSummary` carries `videoBytes`, `telemetryBytes`, and `pendingBytes`, computed in
  `SessionStore.summaries()`. `EditLapSheet` shows them under a `Data` section.
- During ruler scrubbing the lap player uses `SeekParameters.CLOSEST_SYNC`; on release it restores
  `EXACT` and seeks to the requested frame. `ScrubSeeker` accepts a `frameWaitMs` override.

## Release

- Work in a branch, merge to `main`, push. Pushing `main` triggers CI and preview publish.
- `versionCode = 1000 + git commit count`; `v*` tags are stable builds.
