# Design

Pocket Sim Engineer uses a dark, high-contrast sports-app look: bold headlines, dense data cards, pill controls, and a persistent bottom tab bar. The reference points are sports score and fantasy apps — dark surfaces, vivid accent colors, and glanceable numbers — without copying any specific brand.

This document is the source of truth for visual tokens and recurring UI patterns. Change the tokens in `Theme.kt` first, then the screens.

## Principles

1. **Data first.** Lap times, speed, and state are the loudest things on screen.
2. **One accent per meaning.** Green is primary/active, blue is speed, amber is RPM and secondary data, red is error or brake.
3. **Dark only for v0.1.** No light theme until the data-viz palette is validated on track.
4. **Big targets, short labels.** Primary actions are 50 dp pills; secondary actions are 44 dp.
5. **Cards, not dividers.** Group related content into 20–22 dp rounded surfaces rather than dense tables.
6. **Calm motion.** No custom animations in v0.1; Material defaults only.

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
- Card padding: **16 dp**; lap cards use **14 dp**.
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
| `FrameRuler` | 64 dp surface strip with a fixed green needle; frame ticks, half-second labels, and horizontal drag at 6 dp per frame with one haptic tick per frame |
| `Replay source menu` | pill with a trailing chevron; dropdown rows for Files and PlayStation App, each with a one-line subtitle |
| `AppBottomBar` (App.kt) | Library / Record / Settings tabs, green indicator, no elevation |

Dialogs use Material 3 `AlertDialog`. Destructive confirmation labels are red; cancel stays neutral.

## Screens

### Library

- Header: “Sessions”, subtitle “Your laps, ready to review”, `Refresh` ghost pill.
- Empty state: card with “No sessions yet” and one explanatory paragraph.
- Group card: track label + car name + session-count pill; then one row per session.
- Session row: date, `N laps · best M:SS.mmm` in one text node, delete action, chevron.
- The whole row opens the session.

### Record

- Header: “Record”, subtitle “Telemetry and screen capture”.
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

- Header: `Lap N · M:SS.mmm`, track subtitle, back button, `Share` / `CSV` ghost pills.
- Video: 16:9, black, clipped to 22 dp; the stock Media3 controller is hidden.
- Replay source: one pill (`Add replay`, `Replace replay`, or `Choose another clip`) with a chevron, opening Files or PlayStation App; a pending clip adds `Resume sync`.
- Missing video: `Replay` card with a one-sentence explanation and the source pill.
- Cursor line: `current / lap` in mono.
- Playback: lap-only slider with 0 / lap labels, `FrameRuler`, `TransportRow`, then `FrameStepRow` (−1 / +1 frame). The rendered frame drives the charts and map, including while paused.
- Driver card below the playback block; telemetry card contains the chart stack; track map is its own card.

### Settings

- Header: “Settings”, subtitle “Capture, video, and units”.
- Console card: IP field.
- Video card: resolution and codec segmented controls, bitrate label + slider.
- Recording card: `Keep raw recording` toggle row.
- Display card: `Imperial units` toggle row.
- Updates card: channel-aware status line (`Checks the rolling debug build once a day.`, `You are on the latest build.`, `Version X (build N) is available.`), then one action: `Check for updates`, `Download X`, `Downloading N%`, `Install update`, or `Installing…`. Failures use `error` color and name the next action.
- Build card: version name, version code, update channel (`preview`/`stable`), CI build number (or `local`), and short commit SHA.
- When an update is found, a launch dialog offers `Download` / `Later`; Settings remains the place to retry or install.
- Toggle rows are fully clickable; the switch is display-only.

## Charts and track map

`ui/TelemetryCharts.kt`.

- Speed: blue trace, max scaled to session peak.
- Throttle: green; brake: red; both 0–255.
- RPM: amber, scaled to peak.
- Gear: amber step trace.
- Axis: 1 px `outlineVariant`; cursor: 2 px `onSurface` at 75%.
- Trace stroke: 2 px. Chart heights: speed/throttle/brake/RPM 52 dp, gear 40 dp.
- Map: 3 px `onSurfaceVariant` polyline, 7 px green dot at the cursor, 240 dp tall.
- Downsample traces to roughly one point per horizontal pixel; map to about 400 points.
- Current values above the charts: speed with unit, gear, throttle %, brake %, RPM.

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
- Keep UI copy that tests assert on: “Sessions”, “Open”, “Share MP4”, “Export CSV”, “Speed”, “Track map”, “Lap N · time”, “Video not cut yet”, “Keep raw recording”, “720p60”.
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
