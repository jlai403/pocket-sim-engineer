# LapCraft

Android telemetry and synchronized replay for Gran Turismo 7. Capture one lap of GT7 telemetry, attach a single-lap replay from the PS5, and review the lap with charts, a track map, and a telemetry cursor that follows the replay.

**Status:** v0.2 development. Telemetry-only capture, replay import, frame-step sync, and lap review are implemented. On-device verification is pending.

## Documents

- [Product and technical spec](docs/specs/2026-11-10-pocket-sim-engineer.md)
- [Design system](DESIGN.md)
- [Glossary](GLOSSARY.md)
- [Implementation plan](docs/superpowers/plans/2026-11-11-replay-import.md)

## Architecture

```text
lapcraft/
├── core/   # Kotlin JVM: protocol, lap detection, track ID, clip sync math, session codecs
└── app/    # Android: Compose UI, foreground telemetry service, replay trim, Media3
```

`core` has no Android imports, so a future desktop app can reuse it and a future Rust core can replace it behind the same interfaces.

## Workflow

0. Optionally add your GT profile in **Settings → Driver profile**; it becomes the default driver for new laps and the name and flag appear above the lap list.
1. **LapCraft → Record**: connect to GT7 and drive.
2. Tap **Capture this lap** after crossing the start line. The app saves exactly one lap at the next crossing.
3. In GT7, record the single-lap replay with HUD on and the race camera. Use the PS5 Create menu and keep a few seconds of lead-in.
4. Media Gallery → **Upload to PlayStation App**; in the PS App, **Download** or **Share** to LapCraft. The lap screen's replay source button opens Files or the PlayStation App (or its store listing).
5. Open the lap → **Add replay** → **Files**. The playhead starts at the clip's end with a suggested anchor there; drag the timeline or the frame ruler, or frame-step back to the lap-end line, then **Set anchor at this frame**.
6. **Check start frame**, preview, save, and review. The trimmed clip is one lap; the imported original is deleted.

The phone records no video and no audio. Telemetry uses UDP 33739/33740; the only other network calls are update checks and the optional profile lookup.

## Backup and restore

Settings → Library exports the laps you select to one `.zip` (`lapcraft-library-<date>.zip`). Each lap is a directory inside the archive holding `session.json`, `lap.json`, its `.gt7lap` telemetry, and any clip or pending source the lap references, so the file is inspectable with any zip tool. Clips are passed through uncompressed; telemetry and JSON are deflated.

Import merges: ids that already exist are skipped, so importing the same file twice is safe. A lap whose clip is missing still imports, without a replay. Settings and the driver profile are not part of the archive. The empty library also offers **Import a backup**.

## What works

- GT7 packet formats A, B, `~`, and C: Salsa20 decrypt, correct XOR constant per size, ~60 Hz parse.
- Decode-failure diagnostics with packet size and reason.
- Single-lap capture: line-to-line, one session per lap, no video required.
- Track identification from lap coordinates and bundled racing lines, including reverse layouts; ambiguous laps offer a candidate picker, and old `Unknown` laps are backfilled.
- Flat lap feed: no card boxes or rounded corners, lap time as the hero number, per-track `Fastest lap` marker or delta, the lap's driver with flag, swipe to edit or delete, hairline dividers, pull to refresh, and filters for replay state and track.
- Edit sheet per lap: track name (with candidates for `Unknown` laps), driver, and replay (`Add`, `Replace`, or `Resume sync`).
- Developer preview toggle renders the empty library without deleting laps; it only appears on preview builds.
- Driver book: new captures default to the owner; the lap screen's Driver card selects a cached driver, adds one by PSN (with manual name/country fallback), or clears it.
- Replay import through the Android share sheet and SAF file picker; one source button opens Files or the PlayStation App, and a clip can be replaced on an existing lap.
- Sync screen: end-of-clip start with a suggested anchor, timeline scrubber, frame ruler (6 dp per frame, optional haptics), play/±10 s, ±1 frame, track %, preview, hard block for short clips.
- Lossless MP4 trim with keyframe alignment and stored lead-in.
- Lap player: Media3 video with the same playback controls as sync, a lap-only slider and frame ruler, speed/throttle/brake/RPM/gear charts, and a track map.
- CSV export and MP4 sharing.
- Library backup: multi-select lap export to a zip and merge-only import, from Settings or the empty library.
- In-app preview/stable updater.

## Update channels

- **Preview** — every push to `main` replaces the `preview-latest` prerelease: `LapCraft-preview.apk` plus `latest.json`. Preview builds check this channel.
- **Stable** — a `v*` tag creates the latest release with `LapCraft-<tag>.apk` plus `latest.json`. Tag builds check `releases/latest`.

The app checks once a day (or from Settings → Updates), compares `versionCode`, downloads the APK, verifies its SHA-256, and hands it to Android's installer. The user always confirms the install. Checks are skipped when the app was installed by F-Droid or Play.

**Play Protect will likely warn about this app.** It flags APKs that download and install updates outside a store, which is exactly what the in-app updater does. This is a deliberate trade-off: keeping one-tap updates means accepting the warning (choose `Install anyway`, or turn off scanning for this app). If Play Protect blocks an install, [appeal it](https://support.google.com/googleplay/android-developer/contact/protectappeals); the verdict is server-side. Stripping `REQUEST_INSTALL_PACKAGES` is the only reliable way to stop the warning, at the cost of handing every update to the browser.

`versionCode` is `1000 + commit count`, so every commit sorts after the previous one.

```bash
# preview APK
gh release download preview-latest -p LapCraft-preview.apk -D dist --clobber
# stable APK
gh release download latest -p 'LapCraft-*.apk' -D dist --clobber
```

## Build

Requires JDK 17+ and the Android SDK (compileSdk 36).

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew :core:test :app:testDebugUnitTest
```

- `:core` — protocol/decrypt/decode, lap detection, capture coordination, track signatures, clip sync/trim planning, track progress, codecs, lap summaries, CSV, remux planning, playback cursor.
- `:app` (JVM) — lap saver, replay copy, decode-failure text, telemetry notification text, settings, frame-ruler stepping, Compose UI for Record, Library, lap player, and import sync.
- Not covered by JVM tests: real `MediaExtractor`/`MediaMuxer` trim output, real GT7 packets, actual video decode. Those stay device checks.

## Known gaps

- Wrong-lap detection is visual: a single anchor cannot prove which lap a clip contains. A clip shorter than the lap is blocked.
- PS App clips are limited to under 3 minutes and ≤1080p; long laps need USB or a broadcast MP4.
- The replay must be recorded with enough lead-in that the trimmed start is inside the clip.
- Car names are `Car #<code>`.
- Packet C extras (steering, live lap timer) are not parsed yet.
- The desktop/Rust core and cloud backup remain future work.

## Key decisions

- Android only for now: Kotlin + Jetpack Compose.
- Two modules: `:core` (plain Kotlin JVM) and `:app` (Android).
- Telemetry-only capture; no MediaProjection, audio capture, Remote Play, or Chiaki.
- One lap per record; video is optional and attached later.
- One frame anchor at the lap end; GT7 lap time is the ruler.
- Track name inferred from coordinates; manual rename overrides.
- Local-first: no accounts, cloud, ads, or tracking. The optional profile lookup sends only the PSN ID to gt-gridstats.com; manual driver entry stays offline.
- License: GPLv3 + contributor CLA.

## Target

- Pixel 10, Android 16.
- `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Package: `io.github.jlai403.lapcraft`.
