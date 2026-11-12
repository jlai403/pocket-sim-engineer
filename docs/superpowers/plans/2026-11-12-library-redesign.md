# Library redesign implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the library as a scan surface with hero lap times, swipe actions, pull to refresh, an edit sheet, and a developer empty-state preview.

**Architecture:** `LibraryScreen` keeps the data load and screen chrome. The card moves to `LapCard.kt`, the edit sheet to `EditLapSheet.kt`, and the driver picker to `DriverPickerRow.kt` so the lap player and the sheet share it. Pure presentation math (fastest per track) lives in `core/session` and is unit tested without Compose.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2025.10.00), Material 3, Robolectric JVM UI tests, kotlinx.serialization.

**Spec:** `DESIGN.md` (Library, Components) and `docs/specs/2026-11-10-pocket-sim-engineer.md`. Update both in Task 9.

## Global constraints

- Dark theme only. Use `MaterialTheme.colorScheme.*`, no hex outside `Theme.kt`.
- Card radius 22 dp, card padding 14 dp, card gap 10 dp.
- Touch targets: primary 50 dp, ghost 44 dp, icon 40 dp.
- Numeric values use `NumericText`. Lap heroes use `displaySmall` in Geist Mono Black.
- Copy is sentence case, no exclamation marks. Exact strings are listed per task.
- Live becomes Record everywhere, including internal names.
- Tests run on the JVM: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest`.
- Build check: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:lintDebug :app:assembleDebug`.

## Review focus

1. A swipe that starts on the card must not open the lap. Tap opens, swipe reveals.
2. The developer preview flag must survive restart and be obviously reversible, or it looks like data loss.
3. Fastest badge must depend on all laps, not the filtered list, and must tolerate duplicate or mixed-case track labels.
4. Long track names must not push the badge out of the card.
5. Returning from sync must land in the library when opened from the sheet, and in the player when opened from a lap.

---

### Task 1: Rename Live to Record

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt:48-55`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Rename: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LiveScreen.kt` to `RecordScreen.kt`
- Rename: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LiveScreenTest.kt` to `RecordScreenTest.kt`
- Modify: `README.md`, `docs/specs/2026-11-10-pocket-sim-engineer.md`

**Interfaces:**
- Produces: `Screen.Record`, `RecordScreen(state, ...)` with the same parameters as today's `LiveScreen`.

- [ ] **Step 1: Rename the files and symbols**

```bash
git mv app/src/main/kotlin/.../ui/LiveScreen.kt app/src/main/kotlin/.../ui/RecordScreen.kt
git mv app/src/test/kotlin/.../ui/LiveScreenTest.kt app/src/test/kotlin/.../ui/RecordScreenTest.kt
```

Replace `Screen.Live` with `Screen.Record`, `LiveScreen` with `RecordScreen`, the header title `"Live"` with `"Record"`, and the tab label `"Live"` with `"Record"`.

- [ ] **Step 2: Update copy that names the tab**

`README.md` line 27 becomes `1. **PSE → Record**: connect to GT7 and drive.` The spec line 105 becomes `1. Open **Record**, connect to GT7.` Line 280 lists `Record` instead of `Live`.

- [ ] **Step 3: Run the app tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS, including `RecordScreenTest`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename the Live tab to Record"
```

---

### Task 2: Developer preview flag

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/settings/SettingsStore.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreenTest.kt`

**Interfaces:**
- Produces: `SettingsStore.previewEmptyLibrary: Boolean`, key `preview_empty_library`.
- Produces: Settings card with test tag `dev-preview-empty`.

- [ ] **Step 1: Write the failing test**

In `SettingsScreenTest`, add a test that opens Settings, scrolls to the Developer card, clicks the row tagged `dev-preview-empty`, and asserts `SettingsStore(context).previewEmptyLibrary` is true. Then toggle it back and assert false.

- [ ] **Step 2: Run it and watch it fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'`
Expected: FAIL, node `dev-preview-empty` not found.

- [ ] **Step 3: Add the flag and the card**

`SettingsStore` gets a boolean property next to `imperialUnits`. In `SettingsScreen`, after Display and before Updates, render:

```kotlin
if (BuildConfig.UPDATE_CHANNEL != "stable") {
    SectionCard(title = "Developer") {
        SettingToggle(
            label = "Preview empty library",
            checked = previewEmpty,
            modifier = Modifier.testTag("dev-preview-empty"),
        ) { previewEmpty = it; settings.previewEmptyLibrary = it }
        Text(
            "Shows the empty state while your laps stay saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

The toggle is fully clickable and the switch is display-only, matching the imperial row.

- [ ] **Step 4: Run the tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add a developer empty-library preview toggle"
```

---

### Task 3: Fastest lap per track

**Files:**
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapSummary.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapSummaryTest.kt`

**Interfaces:**
- Produces: `fun List<LapSummary>.fastestPerTrack(): Map<String, LapSummary>`, keyed by `trackLabel` exactly as stored.
- Produces: `fun LapSummary.sameLap(other: LapSummary): Boolean` comparing `sessionId` and `lapIndex`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `fastest per track ignores other tracks`()
@Test fun `fastest per track needs two laps to matter`()
@Test fun `fastest per track breaks ties by earlier capture`()
```

Assertions: two laps at `Suzuka` with 90_000 and 91_500 return the 90_000 lap; a single lap still returns itself but callers must check size; a tie returns the earlier `createdAt`.

- [ ] **Step 2: Run and watch them fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LapSummaryTest'`
Expected: FAIL, unresolved reference.

- [ ] **Step 3: Implement**

```kotlin
fun List<LapSummary>.fastestPerTrack(): Map<String, LapSummary> =
    groupBy { it.trackLabel }.mapValues { (_, laps) ->
        laps.minWith(compareBy({ it.lapTimeMs }, { parseInstant(it.createdAt) }))
    }

fun LapSummary.sameLap(other: LapSummary): Boolean =
    sessionId == other.sessionId && lapIndex == other.lapIndex
```

- [ ] **Step 4: Run the core tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat: add fastest-lap-per-track lookup"
```

---

### Task 4: Shared driver row with optional profile link

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/DriverPickerRow.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/DriverSection.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/DriverSectionTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable
internal fun DriverPickerRow(
    driverPsnId: String?,
    profile: DriverProfile?,
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
    onChange: (DriverProfile?) -> Unit,
    onAddDriver: () -> Unit,
    onOpenProfile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write the failing test**

In `DriverSectionTest`, render `DriverPickerRow` with `driverPsnId = "friend"`, `onOpenProfile = { opened = it }`, click `Friend ↗`, then click `GT GridStats`, and assert `opened == "friend"`. A second test renders it with `onOpenProfile = null` and asserts the menu item is absent.

- [ ] **Step 2: Run and watch it fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*DriverSectionTest'`
Expected: FAIL, unresolved reference.

- [ ] **Step 3: Extract the row**

Move the flag, name, PSN, Change menu, "Use my profile" pill, and the driver option list from `DriverSection` into `DriverPickerRow`. The name row is the profile trigger when `onOpenProfile != null` and `driverPsnId != null`: append a small `Icons.AutoMirrored.Filled.OpenInNew` at 14 dp and set `contentDescription = "Open GT profile"`. The menu has `GT GridStats` and `GTSH-Rank`, calling `onOpenProfile(psnId)` with `DriverProfileLinks.gridStats` and `.gtshRank` built by the caller. `DriverSection` keeps its `SectionCard`, store, assignment, and `AddDriverDialog` state.

`DriverSection` passes `onOpenProfile` only when `driverPsnId != null`; the edit sheet passes `null`.

- [ ] **Step 4: Run the tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*DriverSectionTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: share the driver picker and link it to public profiles"
```

---

### Task 5: Shared replay source menu

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ReplaySource.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapPlayerScreen.kt:420-480`

**Interfaces:**
- Produces: `@Composable internal fun ReplaySourcePill(text: String, primary: Boolean, onFiles: () -> Unit, onPlayStation: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing test**

Add `ReplaySourceTest` rendering `ReplaySourcePill` with counters, click the pill, click `Files`, assert `files == 1`, reopen, click `PlayStation App`, assert `playStation == 1`.

- [ ] **Step 2: Run and watch it fail**

Expected: FAIL, unresolved reference.

- [ ] **Step 3: Move the component**

Move `ReplaySourceButton` out of `LapPlayerScreen` into `ReplaySource.kt`, rename it `ReplaySourcePill`, and keep the two menu rows exactly as they are: `Files` / `Pick a video from this phone` and `PlayStation App` / `Download or share from the PS App`.

- [ ] **Step 4: Run the tests and the lap player tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: share the replay source pill"
```

---

### Task 6: Lap card and swipe actions

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapCard.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LapCardTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable
internal fun LapCard(
    lap: LapSummary,
    driver: DriverProfile?,
    showDriver: Boolean,
    fastest: Boolean,
    deltaMs: Int?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write the failing tests**

Five tests on a single card:

1. `1:30.000` renders, `Suzuka` and `BRZ GT300 '21` render as separate nodes.
2. `fastest = true` shows `Fastest lap`; `deltaMs = 412` shows `+0.412`.
3. `showDriver = false` hides `Friend`.
4. `Replay` appears only when `hasReplay`; `Sync pending` appears when `replayPending`.
5. `performTouchInput { swipeRight() }` calls `onEdit`, and `swipeLeft()` calls `onDelete`, while a click calls `onOpen`.

- [ ] **Step 2: Run and watch them fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapCardTest'`
Expected: FAIL, unresolved reference.

- [ ] **Step 3: Build the card**

Three rows inside a `SwipeToDismissBox` with `confirmValueChange = { false }`:

- Row 1: `NumericText(lapTimeLabel(lap.lapTimeMs), style = MaterialTheme.typography.displaySmall)`, right-aligned flag and nickname when `showDriver`.
- Row 2: `trackLabel` as `titleMedium`, right side `Fastest lap` pill or `+0.412` in mono `bodyMedium`.
- Row 3: `carName · date` as `bodySmall` muted, right side `Replay` with an 8 dp green dot or `Sync pending` with an amber dot.

Whole card `.clickable(onClickLabel = "Open lap", onClick = onOpen)`. Backgrounds: start-to-end `surfaceVariant` with `Icons.Filled.Edit` and `Edit`; end-to-start `errorContainer` with `Icons.Filled.Delete` and `Delete`. Add `semantics { customActions = listOf(CustomAccessibilityAction("Edit lap", ...), CustomAccessibilityAction("Delete lap", ...)) }`, and `combinedClickable` long press opens a two-item `DropdownMenu`.

- [ ] **Step 4: Run the tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LapCardTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: rebuild the lap card with swipe actions"
```

---

### Task 7: Edit lap sheet

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/EditLapSheet.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/EditLapSheetTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable
internal fun EditLapSheet(
    lap: LapSummary,
    driver: DriverProfile?,
    owner: DriverProfile?,
    profiles: List<DriverProfile>,
    candidates: List<TrackCandidate>?,
    onDismiss: () -> Unit,
    onSaveTrack: (String) -> Unit,
    onAssignDriver: (DriverProfile?) -> Unit,
    onAddReplay: () -> Unit,
    onResumeSync: () -> Unit,
)
```

- [ ] **Step 1: Write the failing tests**

1. Title `Edit lap`, field prefilled with `Suzuka`, `Save` reports the trimmed text.
2. Driver row renders `Friend` and no `Open GT profile` action.
3. `No replay` shows `Add a replay`; `replayPending` shows `Resume sync`; `hasReplay` shows `Replace replay`.

- [ ] **Step 2: Run and watch them fail**

Expected: FAIL, unresolved reference.

- [ ] **Step 3: Build the sheet**

`ModalBottomSheet` with `SectionLabel("Track")`, an `OutlinedTextField`, candidate rows when `lap.trackLabel == TrackNamer.UNKNOWN`, `SectionLabel("Driver")` plus `DriverPickerRow(onOpenProfile = null)`, a replay row, and a full-width `Save` primary pill. Replay actions call `onDismiss` before navigating. `Add a replay` and `Replace replay` both call `onAddReplay`; `Resume sync` calls `onResumeSync`.

- [ ] **Step 4: Run the tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add the edit lap sheet"
```

---

### Task 8: Library screen rewrite and wiring

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/UiState.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `fastestPerTrack`, `sameLap`, `LapCard`, `EditLapSheet`, `DriverPickerRow`, `ReplaySourcePill`.
- Produces:

```kotlin
fun LibraryScreen(
    onOpenLap: (String, Int) -> Unit,
    onOpenRecord: () -> Unit = {},
    onOpenSync: (String, Int, String?) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
)
```

- Produces: `Screen.ImportSync(sessionId, lapIndex, sourceUri, returnToLibrary: Boolean = false)`.

- [ ] **Step 1: Write the failing tests**

Replace `LibraryScreenTest` with tests for:

1. header `Library`, subtitle `2 laps · 1 track · Best 1:30.000`.
2. card tap reports the lap (no `Open` button).
3. `Fastest lap` appears once per track with two laps, and `+1.500` on the slower lap.
4. `Without replay` filter hides the replay lap.
5. owner laps hide the driver name, another driver shows it.
6. empty library shows the exact copy and `Go to Record` calls `onOpenRecord`.
7. `SettingsStore.previewEmptyLibrary = true` with a real lap shows the preview banner and the empty card; `Turn off` clears the flag.
8. swipe right on the card opens `Edit lap`.
9. swipe left on the card opens `Delete lap?`.

- [ ] **Step 2: Run and watch them fail**

Expected: FAIL on the new strings and nodes.

- [ ] **Step 3: Implement the screen**

Header: `Library`, subtitle from `all` counts or hidden when empty or previewing. Owner chip stays in the header actions.

Toolbar: `All tracks ▾`, `Newest ▾`, segmented `All / Replay / No replay`, and `Clear` when a filter is active.

Content: `PullToRefreshBox(isRefreshing, onRefresh)` around one `LazyColumn`. Its first item is the banner when previewing, then the empty card when `all.isEmpty()` or previewing, otherwise the cards. Empty card copy: `No laps yet`, `Connect to GT7 on the Record tab to start capturing a lap. A replay can be added later to the recorded lap.`, `Go to Record`. Preview banner: `Previewing the empty library` and `Turn off`.

Fastest set comes from `all.fastestPerTrack()`; badge only when that track has two or more laps. Delta only for other laps on the same track.

- [ ] **Step 4: Wire navigation**

`App.kt` passes `onOpenRecord = { screen = Screen.Record }` and `onOpenSync`. `LibraryScreen` hosts an `OpenDocument` launcher for `video/*` and calls `onOpenSync(id, index, uri.toString())`, or `onOpenSync(id, index, null)` for Resume. `ImportSync` carries `returnToLibrary`, and `onDone` branches on it.

- [ ] **Step 5: Run the tests**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: rebuild the library screen"
```

---

### Task 9: Docs and verification

**Files:**
- Modify: `DESIGN.md`
- Modify: `docs/specs/2026-11-10-pocket-sim-engineer.md`
- Modify: `README.md`

- [ ] **Step 1: Rewrite the Library section**

Cover the header, toolbar, card rows, badge and delta rules, swipe actions with the long-press fallback, pull to refresh, empty states, edit sheet, and the developer preview. Add `Swipe actions`, `PullToRefreshBox`, and the Developer card to the component table. Amend principle 6 to allow list placement animation only.

- [ ] **Step 2: Update the spec**

Library behavior, the edit sheet, the Record rename, and the profile link on the lap screen.

- [ ] **Step 3: Full verification**

Run:

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: all pass, lint clean.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: describe the redesigned library"
```
