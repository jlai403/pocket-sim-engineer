# Design

Pocket Sim Engineer uses a dark, high-contrast sports-app look: bold headlines, dense data cards, pill controls, and a persistent bottom tab bar. The reference points are sports score and fantasy apps — dark surfaces, vivid accent colors, and glanceable numbers — without copying any specific brand.

This document is the source of truth for visual tokens and recurring UI patterns. Change the tokens in `Theme.kt` first, then the screens.

## Principles

1. **Data first.** Lap times, speed, and state are the loudest things on screen.
2. **One accent per meaning.** Green is primary/active, blue is speed, amber is RPM and secondary data, red is error or brake.
3. **Dark only for v0.1.** No light theme until the data-viz palette is validated on track.
4. **Big targets, short labels.** Primary actions are 50 dp pills; secondary actions are 44 dp.
5. **Cards, not dividers.** Group related content into 20–22 dp rounded surfaces rather than dense tables.
6. **Calm motion.** Material defaults only; the one exception is `animateItem()` on library rows after an edit or filter.

## Color tokens

All tokens live in `ui/Theme.kt`. Screens must use `MaterialTheme.colorScheme.*`; do not hardcode hex values outside the theme and chart palette.

| Token | Hex | Use |
|---|---|---|
| `primary` | `#3DDC84` | active tab, primary buttons, best lap, live status, track-map dot |
| `onPrimary` | `#03130A` | text/icons on green |
| `primaryContainer` | `#123A24` | reserved for tonal fills |
| `secondary` | `#5AC8FA` | speed values and speed trace |
| `onSecondary` | `#00131C` | text/icons on blue |
| `tertiary` | `#FFC857` | RPM, gear, tertiary stat chips |
| `onTertiary` | `#241800` | text/icons on amber |
| `background` | `#080C11` | app background |
| `surface` | `#111821` | cards, bottom bar |
| `surfaceVariant` | `#1A2431` | chips, segmented tracks, back button, inputs |
| `onSurface` | `#EAF0F5` | primary text |
| `onSurfaceVariant` | `#93A4B5` | secondary text, icons, map line |
| `outline` | `#2B3846` | inactive dots, borders |
| `Hairline` | `#33414F` | library row dividers |
| `RowPressed` | `#10171F` | pressed lap row |
| `outlineVariant` | `#1F2A35` | dividers, chart axis |
| `error` | `#FF6B6B` | errors, brake trace, destructive actions |
| `errorContainer` | `#3A1414` | warning banners |
| `onErrorContainer` | `#FFD5D5` | warning text |
| `onBackground` | `#F4F7FA` | text over background |

System bars are transparent with light icons (`enableEdgeToEdge` + `SystemBarStyle.dark`).

## Typography

Two bundled OFL families, both from [Geist](https://github.com/vercel/geist-font) v1.9.2:

| Family | Weights | Use |
|---|---|---|
| **Geist Sans** | Regular, Medium, SemiBold, Bold, Black | all interface text: titles, body, labels, buttons, tabs |
| **Geist Mono** | Regular, Medium, SemiBold | numeric values only: lap times, speed, gear, RPM, packets/s, sync offsets |

Rules:

- UI copy is Geist Sans. Never set body paragraphs in mono.
- Standalone numbers use `NumericText` (Components.kt), which applies `GeistMonoFamily`.
- Mono values are fixed-width, so digits do not shift as speed, RPM, or the cursor changes.
- Font files live in `res/font/`; the license is `licenses/Geist-OFL.txt`.

| Style | Family | Size | Weight | Use |
|---|---|---:|---|---|
| `displaySmall` | Sans | 34 sp | Black, -0.6 tracking | reserved for future hero numbers |
| `headlineLarge` | Sans | 30 sp | Black, -0.4 | rare, large statements |
| `headlineMedium` | Sans | 25 sp | Black, -0.3 | screen titles, `ScreenHeader` |
| `titleLarge` | Sans | 20 sp | Bold | card titles, section numbers |
| `titleMedium` | Sans | 16 sp | SemiBold | row titles, supporting emphasis |
| `bodyLarge` | Sans | 16 sp | Normal | long copy |
| `bodyMedium` | Sans | 14 sp | Normal | standard copy, dates |
| `bodySmall` | Sans | 12 sp | Normal | metadata, subtitles, hints |
| `labelLarge` | Sans | 14 sp | SemiBold | buttons, tabs |
| `labelMedium` | Sans | 12 sp | SemiBold, 0.4 tracking | uppercase section labels, chips |
| `labelSmall` | Sans | 11 sp | Medium, 0.5 tracking | compact labels, legends, ruler ticks |
| `NumericText` | Mono | inherits | inherits | lap times, stat values, cursor, telemetry row |

Line heights are set in `Theme.kt` to Geist metrics (for example 25/30 for `headlineMedium`, 20/26 for `titleLarge`). Adjust type sizes and line heights together.

Rules:

- Screen titles are `headlineMedium`, one line, ellipsized.
- Numeric emphasis uses `titleLarge` or `titleMedium` plus an accent color.
- Section labels are uppercase `labelMedium` in `onSurfaceVariant`; helper `SectionLabel` does this.
- Body copy is sentence case. No exclamation marks.

## Shape and spacing

| Token | Radius | Use |
|---|---:|---|
| `extraSmall` | 8 dp | small inline blocks |
| `small` | 12 dp | inputs, toggle rows |
| `medium` | 16 dp | chips, stat blocks |
| `large` | 22 dp | cards, video frame |
| `extraLarge` | 28 dp | reserved |

Layout:

- Screen padding: **20 dp horizontal, 8 dp vertical**.
- Section gap: **14 dp** (Library, Record, Settings, Session); **12 dp** inside the player.
- Card padding: **16 dp**; library lap rows use **14 dp vertical and 20 dp horizontal**, with no card surface.
- Row gap: **8–10 dp**; chip padding **14 × 12 dp**.
- Primary pill: **50 dp** tall, 24 dp horizontal padding, `CircleShape`.
- Secondary/ghost pill: **44 dp** tall, 18 dp horizontal padding.
- Back button: **40 dp** circle on `surfaceVariant`.

Containers:

- Root is `Surface(background)` → `windowInsetsPadding(WindowInsets.safeDrawing)` → `Column`.
- Top-level screens get the bottom tab bar; detail screens do not.
- The tab bar uses `NavigationBar` with zero window insets, because the root already handles safe areas. Never add per-screen status-bar padding.
- Lists use `LazyColumn` with 14 dp gaps (Library) or 10 dp (laps).

## Components

Defined in `ui/Components.kt` unless noted.

| Component | Contract |
|---|---|
| `ScreenHeader` | optional 40 dp back button, one-line title, optional subtitle, trailing action row |
| `PrimaryPill` | one main action per screen; green fill, dark text, 50 dp |
| `GhostPill` | secondary actions; outlined, 44 dp; pass `contentColor = error` for destructive |
| `StatChip` | compact metric block: accent value over muted label, 16 dp radius |
| `SegmentedChoice` | equal-width pill segments inside a `CircleShape` track; selected segment is green |
| `SectionCard` | 22 dp surface card with 16 dp padding, an optional uppercase title, and an optional trailing action |
| `SectionLabel` | uppercase muted label for groups inside a card |
| `StatusDot` | 10 dp circle: green when live, outline when idle |
| `FrameRuler` | 64 dp surface strip with a fixed green needle; frame ticks, half-second labels, and horizontal drag at 6 dp per frame. Pressing jumps to the timestamp under the finger, then dragging steps frames from there. The strip follows the finger, so drag right to rewind and left to move forward. The drag reports total frames from the playhead it started on; the ruler and time readout jump to the requested frame at once while seeks are coalesced to one in flight, and one haptic tick fires per step unless disabled |
| `LapCard` | flat feed row, no radius or surface box: leading 32 dp flag column, name and short date, track, hero lap time, car and replay state; tap opens, swipe edits or deletes, long press and custom actions repeat both |
| Swipe actions | full-height action background under the card, Gmail-style; horizontal drag clamped to 60% of the card width; Edit pinned left on a right swipe, Delete pinned right on a left swipe; below the 35% threshold the card springs back to zero, and it always returns to the default state; never dismisses the card |
| `PullToRefreshBox` | library list gesture; the spinner follows the refresh state |
| `DriverPickerRow` | flag, nickname, PSN, Change menu; pass `onOpenLink` to add GT GridStats and GTSH-Rank |
| `EditLapSheet` | modal bottom sheet for track, driver, and replay |
| `ReplaySourcePill` | one pill that opens Files or the PlayStation App |
| `Replay source menu` | pill with a trailing chevron; dropdown rows for Files and PlayStation App, each with a one-line subtitle |
| `AppBottomBar` (App.kt) | Library / Record / Settings tabs, green indicator, no elevation |

Dialogs use Material 3 `AlertDialog`. Destructive confirmation labels are red; cancel stays neutral.

## Screens

### Library

- Header: “Library”, subtitle `N laps · M tracks · Best M:SS.mmm` (hidden when the library is empty or previewing), owner chip (`🇬🇧 Nickname ▾`) opening GT GridStats, GTSH-Rank, and Edit profile. A profile is optional; without one the chip reads `Add GT profile`.
- Toolbar: `All tracks ▾`, the sort pill, `Clear` while a filter is active, then an All / Replay / No replay segmented control. The toolbar hides when the library is empty or previewing.
- Pull to refresh replaces the refresh button. The spinner follows the gesture and the manual reload only, not the automatic reload after an edit.
- Card: flat feed row with no surface box or radius. Leading flag column, then the driver name and short date; the track; the 34 sp mono lap time with the `Fastest lap` label or a `+0.412` delta; the car with `Replay` or `Sync pending`. The whole row opens the lap.
- Rows are separated by a 1 dp full-bleed `Hairline` (#33414F) divider; there is no divider after the last lap. Row backgrounds stay on `background`, so the feed is one flat surface.
- Fastest lap is computed per track across every lap, ignores the active filter, and only appears when the track has two or more laps. The other laps on that track show the delta.
- Every card shows the lap's driver in the hero row: flag and nickname, the PSN when the profile is unknown, or `Driver not set` in muted text.
- Swipe right opens Edit. Swipe left opens the delete confirmation, and the card springs back. Long press and the TalkBack custom actions offer the same two.
- Edit sheet: track field with candidate rows for `Unknown` laps, the shared driver picker without profile links, a replay row (`Add a replay`, `Replace replay`, `Resume sync`), a data row (`N MB total` with replay/pending/telemetry breakdown), and Save.
- Empty state: `No laps yet`, the Record instruction, and `Go to Record`. A filtered empty list adds `Clear filters`.
- Developer preview: Settings → Developer → `Preview empty library` renders the empty state over a banner with `Turn off`; the laps stay on disk. Preview channel only.

### Record

- Header: “Record”, subtitle “Telemetry only · replay comes later”.
- Status card: live dot, status text, UDP hint, then three stat chips (packets/s, lap, speed m/s).
- “GT7 stream” card: a health line (Streaming / Waiting / stale / low rate) with pps, errors, and packet age. While not connected it adds one hint: `Tap Connect to listen for GT7 telemetry.` or, when running, `GT7 sends telemetry only while a race or time trial is on screen.` `Show details` reveals packet ID, car code, flags, inputs, accepted packets, lap-event count, the last five events, and `Copy diagnostics`; copy includes version, build number, commit, and the current metrics.
- Warnings render in an `errorContainer` banner.
- “Console” card with the PS5 IP field, `Save IP`, and `Connect` / `Disconnect`. The field and Save are disabled while the listener runs; Disconnect is disabled while recording.
- Full-width `Start recording` primary pill, enabled only while the stream is connected. A helper line under it states the next action: `Connect before recording.`, `Waiting for the first packet.`, `Recording begins when the car is on track.`, or `Tap Stop to finish the session.`
- `Test 10 s` and `Stop` ghost pills; Stop is enabled only while recording.
- “How to record” card: collapsed by default with a one-line summary and a `Show steps` pill; expands to nine numbered steps, then a red “Keep in mind” block covering the Chiaki-Up bar, screen lock, blocked audio, and the test-recording caveat. The steps are: hide Chiaki-Up's controls, park on track, Connect, wait for ~60 pps, Start recording.

### Session

- Header: track name, car subtitle, back button, `Rename` and red `Delete` ghost pills.
- Summary line: `N laps · best M:SS.mmm` in green.
- Recorded date in muted body small.
- Lap card: circular “Lap N” badge, time as the dominant number, sample count, uncut warning, `Open` primary pill.
- Secondary row: `Share MP4`, `Export CSV`.

### Player

- Header: `Lap N · M:SS.mmm` with a chevron. Collapsed shows only the title; tapping expands track, driver flag/name, car, lap stats (top/min/avg), and the vs-ghost comparison. One overflow menu: `Share video`, `Share screenshot`, `Export CSV`, `Edit lap`.
- Video: 16:9, black, clipped to 22 dp; the stock Media3 controller is hidden. The lap player uses a `TextureView`-backed `PlayerView` so the telemetry HUD and scrub preview can draw over the video. A telemetry HUD overlays the top centre with the last 2 s of throttle/brake traces when enabled in Settings.
- Replay management lives in the `Edit lap` sheet: track, driver, add/replace replay, resume a pending sync, and the lap's data volume (replay + telemetry). The main screen keeps only `Add replay` when no clip exists.
- Scrub preview: each trimmed clip can have `laps/lap-NNN.preview/` with one 480×270 JPEG per keyframe and an `index.json` of keyframe times (~3–4 MB per 90 s lap). It is warmed after import and generated on first open; a small `Preparing scrub preview…` label shows while it builds. It is deleted when the clip is replaced or the lap is deleted. Missing or half-built caches fall back to keyframe seeks.
- New imports are trimmed and then compacted with the Settings replay profile (default 1080p HEVC, ~5 Mbps, 0.5 s keyframes, stereo). If compaction fails the trimmed clip is kept and a `Compaction failed` toast appears.
- Missing video: `Replay` card with a one-sentence explanation and the source pill.
- Playback footer (sticky): `current / lap` in mono, the frame ruler, then centred compact transport (skip-previous/next jump to the previous/next brake point, with a Loop toggle and a `− / +` speed stepper at the right). The ruler is the only scrubber and shows a progress track, event dots, and pinch zoom. A press jumps to the timestamp under the finger; the release snaps to the nearest keyframe. While the ruler is held, the cached keyframe image (480×270 JPEG) is shown over the video; release seeks the player to that keyframe.
- Scrolling content above the footer: video, telemetry card with trace toggles, track-map card. The rendered frame drives the charts and map, including while paused.
- Speed changes last until you leave the lap screen; the default comes from Settings.

### Settings

- Header: “Settings”, subtitle “Console, profile, units, and updates”.
- Console card: IP field.
- Replay compression card: `Preset` (Original / Balanced / Compact), and while not Original, `Resolution` (1080p/720p), `Codec` (HEVC/H.264), `Keyframes` (Fast 0.5 s / Standard 1 s / Sparse 2 s), and `Audio` (Stereo 96 kbps / Mono 64 kbps / Remove). Defaults: Balanced, 1080p, HEVC, Fast, Stereo. Each control shows a caption describing its effect; a non-default choice is prefixed `Changed:` and uses the primary colour. These are defaults; the import screen can override them per lap.
- Display card: `Imperial units`, `Frame ruler haptics`, and `Telemetry HUD` toggle rows, plus a `Default playback speed` dropdown (0.5×/1×/1.5×/2×); haptics are on by default, HUD is on by default.
- Updates card: channel-aware status line (`Checks the rolling debug build once a day.`, `You are on the latest build.`, `Version X (build N) is available.`), then one action: `Check for updates`, `Download X`, `Downloading N%`, `Install update`, or `Installing…`. Failures use `error` color and name the next action.
- Build card: version name, version code, update channel (`preview`/`stable`), CI build number (or `local`), and short commit SHA.
- When an update is found, a launch dialog offers `Download` / `Later`; Settings remains the place to retry or install.
- Toggle rows are fully clickable; the switch is display-only.

## Charts and track map

`ui/TelemetryCharts.kt`.

- Throttle/brake: green and red on one 0–255 chart (top).
- Speed: blue trace with gear overlaid as an amber step trace, each scaled to its own peak (second).
- RPM chart removed; RPM remains a value bar.
- Axis: 1 px `outlineVariant`; cursor: 3 px `onSurface`.
- Trace stroke: 2 px. Chart heights: 52 dp.
- Map: coloured 3 px segments by pedal state, intensity lerped from muted grey to green (throttle) or red (brake) by pedal strength; 12 px blue dot at the cursor, 200 dp tall. A dashed grey ghost lap overlays the fastest lap on the same track.
- Turn markers: the ruler marks brake, throttle, apex, and exit events. The map colours segments by pedal strength but draws no turn-in dots.
- Map chips: `Follow` is on by default and centres on the car, puts the heading up, anchors the car near the bottom, squashes the vertical axis 0.35× for a driver's-perspective tilt, and zooms to the next ~2 s; `Ghost` toggles the reference lap; `Reset` returns to fit-all. The legend is a vertical list of brake, throttle, and ghost under the title, above the map.
- Downsample traces to roughly one point per horizontal pixel; map to about 400 points.
- Readout: three 44 dp bar rows (THR green, BRK red, RPM amber) with labels above and values right, no speed/gear text and no per-bar sparklines; the 2×2 tire heatmap (32 dp tiles, blue cold → green optimal → red hot) sits to the right, vertically centered, with a 40 dp F1 steering wheel under it that turns with the recorded wheel angle (laps recorded before steering was parsed show no wheel). Labels use `labelSmall`; values use Geist Mono.
- Values and the map dot interpolate between the two samples that bracket the cursor time, including tyre temperatures and fuel; gear snaps to the nearest sample. The cursor line stays on the video frame.

## Recording feedback outside the app

While Chiaki-Up is fullscreen the Record tab is not visible, and v0.1 has no overlay.

- Capture hygiene: Chiaki-Up's in-stream bar (On-Screen Controls, Touchpad only, display mode) and its touch layout are part of the captured display. The record steps tell the driver to swipe from the top edge, turn On-Screen Controls off, and let the bar fade before driving. The app cannot hide another app's controls, so this stays a documented setup step, not a feature.

- The telemetry notification is the pre-recording surface: `GT7 stream · 59 pps` or `GT7 stream · waiting`, with a `Disconnect` action.
- The recording notification is the live surface once armed: collapsed shows `Recording lap 3 · 59 pps · 0 err`; expanded (`BigTextStyle`) shows packet age, packet ID, car code, flags, lap, last lap, and the last event. Updates once per second, silent, `setOnlyAlertOnce`.
- Both notifications are `IMPORTANCE_LOW`; two persistent entries while recording is accepted for v0.1.
- Short text toasts are limited to transitions before the MP4 rolls: `GT7 stream OK · 60 pps` and `GT7 telemetry lost`. Rate-limited to one every 3 seconds.
- Nothing is drawn while recording: a toast is a window on the captured display, so it would appear in the lap video. Lap confirmation lives in the notification and the library. Errors during recording surface in the notification only.
- The notification is `IMPORTANCE_LOW` and silent, so it never heads-up over Chiaki-Up; it appears only when the driver pulls the shade.
- The Record card remains the full diagnostics view before arming and after stopping.

## Copy and tone

- Terse, factual, sentence case: “Arm recording”, “Your laps, ready to review”, “Video not cut yet”.
- Avoid jargon the driver does not need; say “packets/s” and “lap”, not decoder internals.
- Errors name the next action (“Could not open UDP 33740. Another app may be using it.”).

## Accessibility

- Minimum touch targets: 44 dp for ghost pills, 50 dp for primary, 40 dp for the icon-only back button.
- Every icon-only control needs a `contentDescription` (“Back” today).
- Do not rely on color alone: pair accents with labels or numbers.
- Keep text contrast at or above Material 3 defaults; muted text is for metadata only.
- The app is edge-to-edge; all content must stay inside `safeDrawing`.

## Implementation rules

- Tokens: add colors, type, and shapes in `Theme.kt`; screens consume `MaterialTheme`.
- Reuse `Components.kt` before creating a one-off card or button.
- New top-level destination: add a tab in `App.kt` and a `Screen` case; new detail screen: no tab, include a back action.
- Keep UI copy that tests assert on: “Library”, “No laps yet”, “Go to Record”, “Fastest lap”, “Driver not set”, “Edit lap”, “Open”, “Share MP4”, “Export CSV”, “Speed”, “Track map”, “Lap N · time”, “Video not cut yet”, “Keep raw recording”, “720p60”.
- Compose UI tests run on the JVM via Robolectric (`:app:testDebugUnitTest`), so visual changes should keep semantics stable.

## File map

| File | Purpose |
|---|---|
| `ui/Theme.kt` | color, type, shape tokens |
| `ui/Components.kt` | shared header, pills, chips, cards, segmented control |
| `ui/App.kt` | tab state, safe-area root, bottom navigation |
| `ui/LibraryScreen.kt` | session groups |
| `ui/CaptureScreen.kt` | telemetry + arm controls |
| `ui/SessionScreen.kt` | lap list and actions |
| `ui/LapPlayerScreen.kt` | video, sync, charts, map |
| `ui/SettingsScreen.kt` | capture and unit settings |
| `ui/TelemetryCharts.kt` | traces and track map |
| `res/font/` | bundled Geist Sans and Geist Mono TTF files |
| `licenses/Geist-OFL.txt` | Geist OFL-1.1 license |
| `MainActivity.kt` | edge-to-edge system bars, permissions, service wiring |

## Not in v0.1

- Light theme or dynamic color.
- Custom icon set beyond Material Icons Core.
- Motion/transition choreography.
- Tablet-specific layouts.
