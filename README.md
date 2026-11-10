# Pocket Sim Engineer

Android telemetry and synchronized replay for Gran Turismo 7. Record a race through Chiaki-Up, tie each completed lap to its own video and telemetry file, and review the lap with charts, a track map, and a playhead that follows the video.

**Status:** Planning. Spec and spike plan are written; no app code yet.

## Documents

- [Product and technical spec](docs/specs/2026-11-10-pocket-sim-engineer.md)
- [Spike implementation plan](docs/superpowers/plans/2026-11-10-pocket-sim-engineer-spike.md)

## Key decisions

- Android only for now: Kotlin + Jetpack Compose.
- Two modules: `:core` (plain Kotlin JVM) and `:app` (Android).
- Capture path: Chiaki-Up fullscreen; the app records the display and internal game audio.
- Per-lap files are produced by continuous recording plus immediate remux.
- Telemetry: GT7 UDP packet A for the spike; packet C later.
- Local-first: no accounts, cloud, ads, or tracking in v0.1.
- License: GPLv3 + contributor CLA.
- Future: Rust core and a desktop app that reads the same session files.

## Target

- Pixel 10, Android 16.
- `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Package: `io.github.jlai403.pocketsimengineer`.

## Next step

Execute the spike plan. The working directory is not initialized as a git repo yet; run `git init` before starting Task 1 so the commit steps work.
