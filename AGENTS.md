# AGENTS.md — LapCraft

Android app for GT7 telemetry-only lap review. Kotlin + Jetpack Compose, `minSdk 29`,
`targetSdk 36`, package `io.github.jlai403.lapcraft`.

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
- `PacketAParser` reads the steering wheel angle from packet B/C at `0x128` and stores it in `Sample.steering` as `-1..1` (+ = left); packet A has none.
- The lap HUD traces the last 2 s before the cursor. The F1 wheel under the tire tiles is hidden for laps with no steering samples.
- Replay clips live under `sessions/<id>/laps/lap-NNN.mp4`; telemetry is `lap-NNN.gt7lap`.
- The frame ruler jumps to the timestamp under the finger on press, then steps frames from
  there while dragging; there is no separate "tap to seek" path.
- Library is a flat feed: no cards/radii, `Hairline` dividers, `RowPressed` for pressed rows.
- `DESIGN.md` is the visual/behaviour reference; `docs/specs/2026-11-10-pocket-sim-engineer.md` is the functional spec.

## Replay storage

- New imports are trimmed, then compacted with the Settings replay profile before `attachClip`.
- Settings live in `SettingsStore`: `replayPreset` (default `Balanced`), `replayKeyframes` (`Frame`,
  0.1 s), `replayAudio` (`Stereo`), `replayResolution` (`FullHd`), `replayCodec` (`Hevc`). The 0.1 s
  GOP keeps exact seeks to six 60 fps frames at ~35 % larger files; `estimateReplayBytes` includes
  that premium.
- `app/.../settings/ReplayCompression.kt` defines the enums, resolved `ReplayProfile`, captions, and size estimates.
- `ReplayCompressionControls` (`app/.../ui/`) is shared by Settings (defaults) and the sync screen (per-lap override, session-only, with a live `≈ N MB` estimate).
- `app/.../replay/VideoTranscoder.kt` re-encodes with Media3 Transformer (`media3-transformer:1.5.1`).
  On failure the trimmed original is kept and the save shows a "Compaction failed" toast.
- `LapSummary` carries `videoBytes`, `telemetryBytes`, and `pendingBytes`, computed in
  `SessionStore.summaries()`. `EditLapSheet` shows them under a `Data` section.
- The lap player and the sync screen both use `SeekParameters.EXACT`. `ScrubSeeker` waits for a
  rendered frame (`SCRUB_EXACT_WAIT_MS` 120 ms while scrubbing, `EXACT_FRAME_WAIT_MS` 400 ms while
  anchoring) and keeps at most one seek in flight.
- The lap player uses a `TextureView`-backed `PlayerView` (`res/layout/view_lap_player.xml`) so the
  telemetry HUD can draw over the video; a `SurfaceView` hides Compose overlays.
- There is no scrub thumbnail cache; the ruler seeks the clip directly. `SettingsStore.developerTools`
  exposes `Re-encode clip` in `EditLapSheet`, which rewrites a lap at 0.1 s keyframes
  (`LapReencoder`, `ReplayKeyframes.Frame`) and deletes the old clip plus any `lap-NNN.preview/` leftovers.
- `SessionStore.deleteScrubArtifacts` removes a lap's `.tmp`/`.bak`/`.compact.mp4`/`.reencode.mp4`
  temporaries and preview directories; `deleteReplayArtifacts` also removes the clip.
- Typography: `labelSmall` is defined in `Theme.kt` (Geist Sans 11 sp); `NumericText` is mono with
  zero tracking. Do not use an undefined Material label style on Compose screens.

## Library transfer

- `LibraryArchive` (`core/.../session/LibraryArchive.kt`) writes a zip: `library.json` with `format`, `exportedAt`, `laps`, `bytes`, an optional `profiles.json` with the cached GT Grid profiles for the exported laps' drivers, then one `library/<id>/` per lap holding `session.json`, `lap.json`, the `.gt7lap`, and any clip or pending source the lap references.
- Entry id is the session id when that session holds one lap, otherwise `<sessionId>-l<NNN>`; ids must match `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` so an imported directory can never be mistaken for staging.
- Import is merge-only: existing ids are skipped, corrupt entries are counted and dropped, unsafe paths throw. Missing clip references are cleared and the lap still imports.
- `.mp4` entries are written at deflate level 0; telemetry and JSON use the default level.
- SAF plumbing lives in `app/.../share/LibraryTransfer.kt`; the UI is `LibrarySection` (Settings) and `ExportLapsSheet`; the empty library has its own import action.
- Changing `applicationId` loses `filesDir`: export from the old package before renaming, then import into the new one.

## Release

- Work in a branch, merge to `main`, push. Pushing `main` triggers CI and preview publish.
- `versionCode = 1000 + git commit count`; `v*` tags are stable builds.
