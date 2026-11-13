# Lap library export/import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lap-granular export/import of the whole library so sessions survive the upcoming change of `applicationId` from `io.github.jlai403.pocketsimengineer` to `io.github.jlai403.lapcraft`.

**Architecture:** A versioned zip (`library.json` + one self-contained directory per lap) written and read by a new pure-JVM `LibraryArchive` in `:core`. The app layer only moves bytes to/from a SAF `Uri` and renders progress; no storage permissions, no Android imports in `:core`.

**Tech Stack:** Kotlin, `java.util.zip`, kotlinx-serialization (existing `SessionJsonCodec`), Jetpack Compose (Material 3), Activity Result `CreateDocument`/`OpenDocument`, JUnit 5 (`:core`), Robolectric + Compose UI test (`:app`).

**Spec:** this plan (design agreed in conversation on 2026-11-13; there is no separate spec file).

## Global Constraints

- Telemetry-only, local-first: no network, no accounts, no analytics in this feature.
- `:core` must not import Android classes. Export/import logic goes there; SAF and UI stay in `:app`.
- Do not compress `.mp4` entries (already HEVC/H.264): deflate level `Deflater.NO_COMPRESSION`. Everything else uses the default level.
- Archive format is versioned: `"format": 1`; import rejects any other value with a user-readable message.
- Import is merge-only: never overwrite or delete an existing session.
- One lap per archive entry. Entry id is `sessionId` for single-lap sessions, `"$sessionId-l%03d".format(lapIndex)` otherwise; ids must match `[A-Za-z0-9._-]{1,64}`.
- Do not change the package name, `applicationId`, or app label in this plan — the rename is a follow-up plan.
- Existing conventions apply: `SessionJsonCodec` for metadata, `AtomicFiles.writeText` for file writes, `labelSmall`/`NumericText` typography, `SectionCard`/`GhostPill`/`PrimaryPill` components, test tag naming `settings-*` / `library-*`.

## Review Focus

- **Archive made on the current build, imported after the rename:** ids, filenames, and metadata must be bit-identical; a fresh install must end up with the same laps, telemetry, and clips.
- **Duplicate import:** pressing Import twice, or importing into a library that already holds that lap, must not duplicate or clobber data.
- **Hostile or damaged zip:** entries containing `..`, absolute paths, a wrong `format`, or truncated entries must be rejected or skipped without writing outside `sessions/` and without leaving staging garbage.
- **Large clips:** a 500 MB MP4 must stream (no whole-file reads, constant memory) and must not be deflated, so export stays near disk speed.
- **Partial selection:** exporting one lap of a legacy multi-lap session must not carry the other laps' files, and must not produce an id that collides with the original session.
- **Missing files:** if a referenced clip or telemetry file is absent from the archive, the lap must still import with usable metadata and no dangling references.

---

## File Structure

| File | Responsibility |
|---|---|
| `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt` | Format constants, `LapRef`/`ExportReport`/`ImportReport`/`LibraryManifest`, `export()`, `import()` |
| `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt` | Round trip, selection, compression, merge, zip-slip, format rejection |
| `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/share/LibraryTransfer.kt` | SAF `Uri` ⇄ stream glue, runs on `Dispatchers.IO` |
| `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheet.kt` | Modal sheet: lap multi-select, totals, export action |
| `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySection.kt` | Settings card: launchers, busy/progress/result state |
| `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt` | Hosts `LibrarySection()` (modify) |
| `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt` | `EmptyLibrary` gains an import pill (modify) |
| `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheetTest.kt` | Selection behaviour and totals |
| `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySectionTest.kt` | Card renders actions; busy state disables them |
| `README.md`, `AGENTS.md` | Backup/restore + migration ordering (modify) |

Archive layout (paths inside the zip):

```
library.json                          {"format":1,"exportedAt":"…","laps":12,"bytes":1503238553}
library/<id>/session.json             SessionMetadata with laps = [this lap]
library/<id>/lap.json                 LapMetadata verbatim
library/<id>/laps/lap-002.gt7lap      only files the lap references
library/<id>/laps/lap-002.mp4
library/<id>/raw.mp4                  only when the lap's clip is the raw capture
```

---

### Task 1: Export one lap per entry

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt`

**Interfaces:**
- Consumes: `SessionJsonCodec.decodeSession/encodeSession/encodeLap`, `LapMetadata`, `SessionMetadata`, `AtomicFiles` (tests only).
- Produces: `data class LapRef(val sessionId: String, val lapIndex: Int)`, `data class ExportReport(val laps: Int, val bytes: Long)`, `data class LibraryManifest(val format: Int, val exportedAt: String, val laps: Int, val bytes: Long)`, `object LibraryArchive { const val FORMAT = 1; fun export(rootDir: File, laps: List<LapRef>, out: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): ExportReport }`.

- [ ] **Step 1: Write the failing test**

Test helper mirrors `SessionStoreTest.writeSession` and adds the files an export must find: `laps/lap-001.json`, `laps/lap-001.gt7lap` (79-byte-header payload is not needed—the codec is not involved; write arbitrary bytes), `laps/lap-001.mp4`.

```kotlin
@Test
fun `exports one self-contained entry per lap`() {
    writeSession("s1", laps = listOf(1))
    val out = ByteArrayOutputStream()

    val report = LibraryArchive.export(temp, listOf(LapRef("s1", 1)), out)

    assertEquals(1, report.laps)
    ZipInputStream(out.toByteArray().inputStream()).use { zip ->
        val names = generateSequence { zip.nextEntry }.map { it.name }.toList()
        assertTrue("library.json" in names)
        assertTrue("library/s1/session.json" in names)
        assertTrue("library/s1/lap.json" in names)
        assertTrue("library/s1/laps/lap-001.gt7lap" in names)
        assertTrue("library/s1/laps/lap-001.mp4" in names)
        assertFalse(names.any { it.contains(".preview") || it.endsWith(".tmp") })
    }
    val manifest = Json.decodeFromString<LibraryManifest>(readEntry(out, "library.json"))
    assertEquals(LibraryArchive.FORMAT, manifest.format)
    assertEquals(1, manifest.laps)
    assertTrue(manifest.bytes > 0)
    assertEquals(1, readEntry(out, "library/s1/session.json").let {
        SessionJsonCodec.decodeSession(it).laps.size
    })
}

@Test
fun `skips refs that do not exist`() {
    writeSession("s1", laps = listOf(1))
    val report = LibraryArchive.export(temp, listOf(LapRef("s1", 9), LapRef("nope", 1)), ByteArrayOutputStream())
    assertEquals(0, report.laps)
}
```

Write `readEntry(out: ByteArrayOutputStream, name: String): String` as a private helper at the bottom of the test file using `ZipInputStream`; Task 2 adds `entryNames(out)` and the session-writing helpers there too, so later tasks only extend the helper block.

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: compile failure, unresolved `LibraryArchive`, `LapRef`.

- [ ] **Step 3: Implement export**

In `LibraryArchive.kt`:

- `@Serializable data class LibraryManifest(...)` (`kotlinx.serialization.Serializable`), plus `LapRef`, `ExportReport`, `ImportReport` as plain data classes.
- `LibraryArchive.export` opens `ZipOutputStream(BufferedOutputStream(out, 64 * 1024))`, sets UTF-8.
- Resolves each `LapRef` with `SessionStore`-free reads: `File(rootDir, "sessions/$sessionId/session.json")` → `SessionJsonCodec.decodeSession`; find `lap = session.laps.firstOrNull { it.index == lapIndex }`; on any failure skip the ref.
- Entry id via `private fun archiveId(session: SessionMetadata, lap: LapMetadata): String` — `session.laps.size == 1` → `session.sessionId`, else `"${session.sessionId}-l%03d".format(lap.index)`. Skip when it fails `Regex("[A-Za-z0-9._-]{1,64}")`.
- Builds the file list for that lap, relative to the session dir, in this order, each only `if (file.isFile)`: `"laps/lap-%03d.gt7lap".format(lap.index)`, `lap.videoFile`, `lap.sourceFile`, and `session.rawFile` when it equals `lap.videoFile` or `lap.sourceFile` (dedupe by name).
- Writes entry `session.json` = `SessionJsonCodec.encodeSession(session.copy(sessionId = id, laps = listOf(lap), audioFile = null, keepRaw = false, rawFile = if (files.contains(session.rawFile)) session.rawFile else "", rawDeleted = !files.contains(session.rawFile)))`, then `lap.json` = `SessionJsonCodec.encodeLap(lap)`, then the files.
- Resolve refs and file sizes **before** opening the zip, so the manifest can be the first entry with its final numbers: `exportedAt = Instant.now().toString()`, `laps = resolved.size`, `bytes = sum of every entry's uncompressed bytes` (metadata + files). Write `library.json`, then one entry per resolved lap.
- Serialize the manifest with `Json { encodeDefaults = true }` from `kotlinx.serialization.json` in this file; decode with the same instance in Task 3. `SessionJsonCodec` stays untouched.
- Progress: after each file copy, `onProgress(written, total)`; return `ExportReport(laps = resolved.size, bytes = total)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt
git commit -m "feat: export laps as a versioned library zip"
```

---

### Task 2: Partial selection, derived ids, no compression for clips

**Files:**
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt`

**Interfaces:**
- Consumes: Task 1's `LibraryArchive.export`.
- Produces: same signature; behaviour pinned by tests.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `partial selection of a multi-lap session uses a derived id and omits siblings`() {
    writeSession("s1", laps = listOf(1, 2))
    val out = ByteArrayOutputStream()

    LibraryArchive.export(temp, listOf(LapRef("s1", 2)), out)

    val names = entryNames(out)
    assertTrue("library/s1-l002/session.json" in names)
    assertTrue("library/s1-l002/laps/lap-002.gt7lap" in names)
    assertFalse(names.any { it.contains("lap-001") })
    assertEquals(2, SessionJsonCodec.decodeLap(readEntry(out, "library/s1-l002/lap.json")).index)
}

@Test
fun `stored clip is not deflated but telemetry is`() {
    writeSession("s1", laps = listOf(1), videoBytes = ByteArray(256 * 1024) { 7 })
    writeSessionFile("s1", "laps/lap-001.gt7lap", ByteArray(256 * 1024) { 1 })
    val file = File(temp, "library.zip")
    LibraryArchive.export(temp, listOf(LapRef("s1", 1)), file.outputStream())

    ZipFile(file).use { zip ->
        val clip = zip.getEntry("library/s1/laps/lap-001.mp4")
        val telemetry = zip.getEntry("library/s1/laps/lap-001.gt7lap")
        assertEquals(ZipEntry.DEFLATED, clip.method)
        assertTrue(clip.compressedSize <= clip.size + 64)
        assertTrue(telemetry.compressedSize < telemetry.size / 2)
    }
}
```

Extend the helper: `writeSession(id, laps, videoBytes: ByteArray = ByteArray(0))` writes a clip per lap and sets `videoFile`; `writeSessionFile(id, relative, bytes)`; `entryNames(out)`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: FAIL — `library/s1-l002/...` absent, clip compressed size above the bound.

- [ ] **Step 3: Implement**

- Before each `putNextEntry`, choose the level by suffix: `.mp4` → `setLevel(Deflater.NO_COMPRESSION)`, otherwise `setLevel(Deflater.DEFAULT_COMPRESSION)`. Set it for every entry, not once, so the switch is symmetric.
- Derived id rule is already in `archiveId`; make sure a single-lap session keeps the raw `sessionId` even when it is the only selected lap of a multi-lap session? No — rule is session-based: `session.laps.size == 1`. Do not change.
- Selection must not read sibling lap files: file list is built only from the selected `LapMetadata` and `session.rawFile` references.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A core/src
git commit -m "feat: export selected laps only and skip deflating clips"
```

---

### Task 3: Import with merge-by-id

**Files:**
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt`

**Interfaces:**
- Produces: `data class ImportReport(val laps: Int, val skipped: Int, val failed: Int)`, `fun LibraryArchive.import(rootDir: File, input: InputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): ImportReport`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `round trip restores sessions and telemetry`() {
    writeSession("s1", laps = listOf(1), videoBytes = ByteArray(1024) { 3 })
    writeSessionFile("s1", "laps/lap-001.gt7lap", byteArrayOf(9, 9, 9))
    val zip = File(temp, "library.zip")
    LibraryArchive.export(temp, listOf(LapRef("s1", 1)), zip.outputStream())

    val target = File(temp, "target").apply { mkdirs() }
    val report = LibraryArchive.import(target, zip.inputStream())

    assertEquals(ImportReport(laps = 1, skipped = 0, failed = 0), report)
    val restored = File(target, "sessions/s1")
    assertTrue(File(restored, "session.json").isFile)
    assertTrue(File(restored, "laps/lap-001.json").isFile)
    assertArrayEquals(byteArrayOf(9, 9, 9), File(restored, "laps/lap-001.gt7lap").readBytes())
    assertArrayEquals(ByteArray(1024) { 3 }, File(restored, "laps/lap-001.mp4").readBytes())
    assertEquals(1, SessionStore(target).summaries().size)
}

@Test
fun `importing twice skips existing sessions`() {
    writeSession("s1", laps = listOf(1))
    val zip = File(temp, "library.zip")
    LibraryArchive.export(temp, listOf(LapRef("s1", 1)), zip.outputStream())
    val target = File(temp, "target").apply { mkdirs() }

    LibraryArchive.import(target, zip.inputStream())
    val second = LibraryArchive.import(target, zip.inputStream())

    assertEquals(ImportReport(laps = 0, skipped = 1, failed = 0), second)
    assertEquals(1, SessionStore(target).summaries().size)
}

@Test
fun `missing referenced clip imports the lap without a replay`() {
    // build a zip by hand: session.json + lap.json referencing laps/lap-001.mp4, no mp4 entry
    val zip = File(temp, "broken.zip")
    val lap = LapMetadata(index = 1, lapTimeMs = 91_000, startNanos = 0, endNanos = 0,
        sampleCount = 0, videoFile = "laps/lap-001.mp4", leadInMs = 0)
    val session = SessionMetadata(sessionId = "s9", createdAt = "2026-11-13T10:00:00Z",
        trackLabel = "Monza", carCode = 3474, carName = "BRZ", rawFile = "", laps = listOf(lap))
    writeZip(zip, mapOf(
        "library.json" to Json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 0)),
        "library/s9/session.json" to SessionJsonCodec.encodeSession(session),
        "library/s9/lap.json" to SessionJsonCodec.encodeLap(lap),
    ))

    val target = File(temp, "target").apply { mkdirs() }
    val report = LibraryArchive.import(target, zip.inputStream())

    assertEquals(ImportReport(laps = 1, skipped = 0, failed = 0), report)
    val stored = SessionStore(target).lap("s9", 1)!!
    assertEquals("", stored.videoFile)
}
```

Helpers: `writeZip(file: File, entries: Map<String, ByteArray>)`; `Json` = `Json { encodeDefaults = true }`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: unresolved `import`, `ImportReport`.

- [ ] **Step 3: Implement import**

Order of operations inside `import`:

1. `ZipInputStream(BufferedInputStream(input, 64 * 1024))`; first entry must be `library.json` (order is guaranteed by export, but read it wherever it appears before extracting). Decode `LibraryManifest`; `require(manifest.format == FORMAT) { "Unsupported library format ${manifest.format}; expected $FORMAT" }`. Reject a missing manifest the same way.
2. Sweep stale staging: `File(rootDir, "sessions").listFiles()?.filter { it.name.startsWith(".import-") }?.forEach { it.deleteRecursively() }`.
3. Stage into `File(rootDir, "sessions/.import-<UUID>")`. For every entry under `library/`: normalize the name, reject absolute paths, `..` segments, and backslashes; write to the staging dir with `AtomicFiles.writeBytes` only for `.json` files and plain `mkdirs` + `FileOutputStream` for the rest (streaming copy, 64 KB buffer).
4. For each staged `library/<id>/`: require `id` matches `[A-Za-z0-9._-]{1,64}`; decode `session.json` and `lap.json` (any exception → `failed++`, delete that staged dir). Build `stored = session.copy(sessionId = id, laps = listOf(lap))`.
5. Missing-file handling: if `lap.videoFile` is non-blank and `File(staged, lap.videoFile)` is absent → `stored.laps = listOf(lap.copy(videoFile = ""))`; same for `sourceFile` → `null`; if `stored.rawFile` is non-blank and absent → `rawFile = ""`, `rawDeleted = true`.
6. Write `session.json` (`AtomicFiles.writeText`) and `laps/lap-%03d.json` (`AtomicFiles.writeText(…, SessionJsonCodec.encodeLap(storedLap))`) **inside the staged dir**, delete the entry-level `lap.json` (it is not part of a session dir), then move it: target `File(rootDir, "sessions/$id")`; if it exists → `skipped++` and delete the staged dir; else `check(stagedDir.renameTo(target))`.
7. `finally`: delete any remaining `.import-*` dirs. Progress: `onProgress(bytesRead, manifest.bytes)` after each entry.

Return `ImportReport(laps = imported, skipped, failed)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A core/src
git commit -m "feat: import a library zip by merging missing laps"
```

---

### Task 4: Import hardening

**Files:**
- Modify: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchive.kt`
- Test: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/LibraryArchiveTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `rejects path traversal entries`() {
    val zip = File(temp, "evil.zip")
    writeZip(zip, mapOf(
        "library.json" to Json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 1, 4)),
        "library/../evil/session.json" to "{}".toByteArray(),
    ))
    val target = File(temp, "target").apply { mkdirs() }

    val error = assertThrows<IllegalArgumentException> {
        LibraryArchive.import(target, zip.inputStream())
    }
    assertTrue(error.message!!.contains("unsafe"))
    assertFalse(File(temp, "evil").exists())
    assertTrue(File(target, "sessions").listFiles()!!.none { it.name.startsWith(".import-") })
}

@Test
fun `rejects an unknown format`() {
    val zip = File(temp, "v2.zip")
    writeZip(zip, mapOf("library.json" to Json.encodeToString(LibraryManifest(2, "x", 0, 0))))
    val target = File(temp, "target").apply { mkdirs() }

    val error = assertThrows<IllegalArgumentException> {
        LibraryArchive.import(target, zip.inputStream())
    }
    assertTrue(error.message!!.contains("Unsupported library format 2"))
}

@Test
fun `corrupt session entry is counted as failed and does not block the rest`() {
    val zip = File(temp, "mixed.zip")
    val good = SessionMetadata(sessionId = "good", createdAt = "2026-11-13T10:00:00Z",
        trackLabel = "Monza", carCode = 1, carName = "x", rawFile = "",
        laps = listOf(LapMetadata(1, 90_000, 0, 0, 0, "", 0)))
    writeZip(zip, mapOf(
        "library.json" to Json.encodeToString(LibraryManifest(1, "2026-11-13T10:00:00Z", 2, 0)),
        "library/good/session.json" to SessionJsonCodec.encodeSession(good),
        "library/good/lap.json" to SessionJsonCodec.encodeLap(good.laps.first()),
        "library/bad/session.json" to "not json".toByteArray(),
        "library/bad/lap.json" to "not json".toByteArray(),
    ))

    val report = LibraryArchive.import(File(temp, "target").apply { mkdirs() }, zip.inputStream())

    assertEquals(ImportReport(laps = 1, skipped = 0, failed = 1), report)
}

@Test
fun `stale staging directories are swept before import`() {
    val target = File(temp, "target/sessions/.import-stale").apply { mkdirs() }
    File(target, "junk.txt").writeText("x")
    val zip = File(temp, "empty.zip")
    writeZip(zip, mapOf("library.json" to Json.encodeToString(LibraryManifest(1, "x", 0, 0))))

    LibraryArchive.import(File(temp, "target"), zip.inputStream())

    assertFalse(target.exists())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: FAIL — traversal is written, or unknown format is accepted, or corrupt entry aborts the run.

- [ ] **Step 3: Implement**

- One `private fun safeEntryPath(name: String): String` that throws `IllegalArgumentException("unsafe archive entry: $name")` for absolute paths, `..` segments, backslashes, empty segments, and names not starting with `library/` (except the manifest).
- Wrap per-entry decoding in `runCatching`; count failures, never abort the whole import for one bad session.
- Sweep staging at start **and** in `finally`.
- Validate `manifest.bytes >= 0`; a zero total disables progress (call `onProgress(0, 0)` once).

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test --tests '*LibraryArchiveTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A core/src
git commit -m "fix: harden library import against unsafe and corrupt archives"
```

---

### Task 5: SAF glue in the app layer

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/share/LibraryTransfer.kt`

**Interfaces:**
- Consumes: `LibraryArchive.export/import`, `LapRef`, `ExportReport`, `ImportReport`.
- Produces: `object LibraryTransfer { suspend fun export(context: Context, uri: Uri, rootDir: File, laps: List<LapRef>, onProgress: (Long, Long) -> Unit): ExportReport; suspend fun import(context: Context, uri: Uri, rootDir: File, onProgress: (Long, Long) -> Unit): ImportReport }`.

- [ ] **Step 1: Implement**

```kotlin
object LibraryTransfer {
    suspend fun export(
        context: Context,
        uri: Uri,
        rootDir: File,
        laps: List<LapRef>,
        onProgress: (Long, Long) -> Unit,
    ): ExportReport = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open the chosen file")
        out.use { LibraryArchive.export(rootDir, laps, it, onProgress) }
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        rootDir: File,
        onProgress: (Long, Long) -> Unit,
    ): ImportReport = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the chosen file")
        input.use { LibraryArchive.import(rootDir, it, onProgress) }
    }
}
```

No test in this task; it is exercised through `LibrarySectionTest` presence checks and the manual verification below.

- [ ] **Step 2: Build to verify it compiles**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/share/LibraryTransfer.kt
git commit -m "feat: add SAF helpers for library export and import"
```

---

### Task 6: Export lap selection sheet

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheet.kt`
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheetTest.kt`

**Interfaces:**
- Consumes: `LapSummary`, `LapRef`, `SessionStore.summaries()`, `SessionFixtures` (test).
- Produces: `@Composable fun ExportLapsSheet(laps: List<LapSummary>, onDismiss: () -> Unit, onExport: (List<LapRef>) -> Unit)`, plus `internal fun exportBytes(laps: Collection<LapSummary>): Long`.

- [ ] **Step 1: Write the failing tests**

Follow the existing `EditLapSheetTest` wrapper (`PocketSimEngineerTheme { … }`, `createComposeRule`).

```kotlin
@Test
fun `all laps are selected by default and totals are summed`() {
    composeTestRule.setContent {
        PocketSimEngineerTheme {
            ExportLapsSheet(laps = listOf(lapOne, lapTwo), onDismiss = {}, onExport = {})
        }
    }
    composeTestRule.onNodeWithTag("export-laps-summary").assertTextContains("2 of 2 selected")
    composeTestRule.onNodeWithTag("export-laps-button").assertIsEnabled()
}

@Test
fun `deselecting every lap disables export`() {
    composeTestRule.setContent { … }
    composeTestRule.onNodeWithTag("export-lap-s1-1").performClick()
    composeTestRule.onNodeWithTag("export-lap-s2-1").performClick()
    composeTestRule.onNodeWithTag("export-laps-summary").assertTextContains("0 of 2 selected")
    composeTestRule.onNodeWithTag("export-laps-button").assertIsNotEnabled()
}

@Test
fun `export passes the selected refs`() {
    var exported: List<LapRef>? = null
    composeTestRule.setContent { … onExport = { exported = it } }
    composeTestRule.onNodeWithTag("export-lap-s1-1").performClick()
    composeTestRule.onNodeWithTag("export-laps-button").performClick()
    assertEquals(listOf(LapRef("s2", 1)), exported)
}

@Test
fun `select all toggles back to everything`() {
    … click select-all twice, assert "2 of 2" … 0 of 2 …
}
```

Fixtures: two `LapSummary` rows with distinct sizes via `SessionFixtures` (or construct directly with `videoBytes = 100, telemetryBytes = 20, pendingBytes = 0`).

- [ ] **Step 2: Run to verify it fails**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*ExportLapsSheetTest'`
Expected: compile failure, unresolved `ExportLapsSheet`.

- [ ] **Step 3: Implement**

- `LazyColumn` inside `ModalBottomSheet`, header `Text("Export laps")`, a `[Select all]`/`[Clear]` ghost pill with `testTag("export-laps-select-all")`, summary text `testTag("export-laps-summary")` reading `"${selected.size} of ${laps.size} selected · ≈ ${formatBytes(total)}"`.
- Row per lap: `Checkbox` + title `"${trackLabel} · ${lapTimeLabel(lapTimeMs)}"` + subtitle `"${shortDateLabel(createdAt)} · ${formatBytes(rowBytes)}"`; tag `"export-lap-${sessionId}-${lapIndex}"`; whole row clickable.
- Selection is `mutableStateOf(laps.map { LapRef(it.sessionId, it.lapIndex) }.toSet())`; rows toggle membership.
- Export button (`PrimaryPill`, tag `export-laps-button`) calls `onExport(selected.toList())`; `enabled = selected.isNotEmpty()`.
- `exportBytes` = `sumOf { it.videoBytes + it.telemetryBytes + it.pendingBytes }`. Reuse `formatBytes` (`LapStats.kt:84`), `lapTimeLabel` and `shortDateLabel` (`UiState.kt`) — all internal to `app.ui`, no new formatters.
- Sort input newest first by `createdAt` (already the order from `summaries()`); do not add sort controls.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*ExportLapsSheetTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheet.kt app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/ExportLapsSheetTest.kt
git commit -m "feat: add lap multi-select export sheet"
```

---

### Task 7: Settings library card and empty-library import

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySection.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt` (add `LibrarySection()` between the driver profile section and `SectionCard(title = "Console")`)
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt` (`EmptyLibrary` gains `onImport: () -> Unit`, plus the launcher/import call)
- Test: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySectionTest.kt`

**Interfaces:**
- Consumes: `LibraryTransfer`, `ExportLapsSheet`, `SessionStore`, `LapSummary`, `formatBytes`/`lapTimeLabel` helpers.
- Produces: `@Composable fun LibrarySection()`; `@Composable private fun EmptyLibrary(onOpenRecord: () -> Unit, onImport: () -> Unit)`; `internal fun importSummary(report: ImportReport): String` and `internal fun exportSummary(report: ExportReport): String`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `library card shows both actions and the lap count`() {
    composeTestRule.setContent { PocketSimEngineerTheme { LibrarySection() } }
    composeTestRule.onNodeWithTag("settings-library-export").assertExists()
    composeTestRule.onNodeWithTag("settings-library-import").assertExists()
    composeTestRule.onNodeWithTag("settings-library-summary").assertExists()
}

@Test
fun `result summaries read naturally`() {
    assertEquals("Imported 3 laps, skipped 2 already here.", importSummary(ImportReport(3, 2, 0)))
    assertEquals("Imported 3 laps, 1 could not be read.", importSummary(ImportReport(3, 0, 1)))
    assertEquals("Exported 3 laps.", exportSummary(ExportReport(3, 1_048_576)))
    assertEquals("Nothing to export.", exportSummary(ExportReport(0, 0)))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LibrarySectionTest'`
Expected: compile failure, unresolved `LibrarySection`, `importSummary`, `exportSummary`.

- [ ] **Step 3: Implement**

`LibrarySection()`:

- Private state type in this file:

```kotlin
private sealed interface Transfer {
    data object Idle : Transfer
    data class Busy(val label: String, val done: Long, val total: Long) : Transfer
    data class Done(val text: String) : Transfer
    data class Failed(val text: String) : Transfer
}
```

- `val context = LocalContext.current`, `val scope = rememberCoroutineScope()`, `val root = context.filesDir`, `val store = remember { SessionStore(root) }`, `var laps by remember { mutableStateOf(store.summaries()) }`, `var sheet by remember { mutableStateOf(false) }`, `var pending by remember { mutableStateOf<List<LapRef>>(emptyList()) }`, `var transfer by remember { mutableStateOf<Transfer>(Transfer.Idle) }`.
- Card content: title `"Library"`, summary row `testTag("settings-library-summary")` → `"${laps.size} laps · ${formatBytes(exportBytes(laps))}"`; `GhostPill("Export laps…", modifier = Modifier.testTag("settings-library-export"), enabled = laps.isNotEmpty() && transfer is Transfer.Idle)`; `GhostPill("Import library…", modifier = Modifier.testTag("settings-library-import"), enabled = transfer is Transfer.Idle)`.
- Export launcher: `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) startExport(uri) }`, invoked as `launcher.launch("lapcraft-library-${LocalDate.now()}.zip")` from the sheet's `onExport`, after storing its refs in `pending` and closing the sheet.
- Import launcher: `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` with `arrayOf("application/zip", "application/octet-stream")`.
- `startExport(uri)`: `transfer = Transfer.Busy("Exporting", 0, 0)`; `scope.launch { runCatching { LibraryTransfer.export(context, uri, root, pending) { done, total -> transfer = Transfer.Busy("Exporting", done, total) } }.onSuccess { transfer = Transfer.Done(exportSummary(it)) }.onFailure { transfer = Transfer.Failed(it.message ?: "Export failed") }; laps = store.summaries() }`.
- `startImport(uri)`: mirror it with `Transfer.Busy("Importing", …)` and `importSummary`, then `laps = store.summaries()`.
- Progress: `LinearProgressIndicator(progress = { done.toFloat() / total })` when `total > 0`, else the indeterminate overload alongside the label.
- Result line with `testTag("settings-library-status")` in `bodySmall`, error color on `Failed`; nothing rendered on `Idle`.
- `if (sheet) ExportLapsSheet(laps = laps, onDismiss = { sheet = false }, onExport = { refs -> pending = refs; sheet = false; launcher.launch(...) })`.
- Summary helpers:

```kotlin
internal fun importSummary(report: ImportReport): String = buildString {
    append("Imported ${report.laps} lap${if (report.laps == 1) "" else "s"}")
    if (report.skipped > 0) append(", skipped ${report.skipped} already here")
    if (report.failed > 0) append(", ${report.failed} could not be read")
    append(".")
}
internal fun exportSummary(report: ExportReport): String =
    if (report.laps == 0) "Nothing to export." else "Exported ${report.laps} laps."
```

`LibraryScreen.kt`:

- `EmptyLibrary(onOpenRecord, onImport)` adds `GhostPill("Import a backup", onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("library-import-backup"))` under "Go to Record".
- In `LibraryScreen`, add the `OpenDocument` launcher (same MIME array) and on result `scope.launch { runCatching { LibraryTransfer.import(context, uri, context.filesDir) { _, _ -> } }.onSuccess { message = importSummary(it); reload++ }.onFailure { message = "Import failed: ${it.message}" } }`.
- Surface the result through the existing inline `message` `Text`; no new component and no progress bar on this screen.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*LibrarySectionTest' --tests '*LibraryScreenTest' --tests '*SettingsScreenTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySection.kt app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/SettingsScreen.kt app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibraryScreen.kt app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/ui/LibrarySectionTest.kt
git commit -m "feat: add library export and import to settings"
```

---

### Task 8: Docs and full verification

**Files:**
- Modify: `README.md` (new "Backup and restore" section after "Workflow"; one line in "Update channels")
- Modify: `AGENTS.md` (archive format + migration order)
- Modify: `GLOSSARY.md` (entry for "library archive")

- [ ] **Step 1: Update docs**

README: state that Settings → Library exports selected laps to a `.zip` and imports them on any install; the archive holds one directory per lap with `session.json`, `lap.json`, telemetry, and clip; import merges and skips laps already present; today's build must be installed before the package rename because a new `applicationId` cannot read the old app's private storage.

AGENTS.md: under a new `## Library transfer` heading, list the archive layout, the derived-id rule, the merge-only import rule, and the ordering constraint (export from the old package, then rename, then import).

- [ ] **Step 2: Run the full check**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL, no lint errors.

- [ ] **Step 3: Commit**

```bash
git add README.md AGENTS.md GLOSSARY.md
git commit -m "docs: describe library export and import"
```

---

### Task 9: Ship the bridge build and prove the round trip

- [ ] **Step 1:** Merge to `main`, push, wait for the preview publish.
- [ ] **Step 2:** On the target device, install that preview build and open Settings → Library. Confirm `N laps · …` matches the library screen.
- [ ] **Step 3:** Select all laps → Export to `Downloads/lapcraft-library-<date>.zip`. Record the size and entry count with `unzip -l` (expect `library.json` plus one `library/<id>/` per lap).
- [ ] **Step 4:** Import the same file again immediately. Expected result line: `Imported 0 laps, skipped N already here.` and an unchanged lap count — this proves idempotence before you rely on it after the rename.
- [ ] **Step 5:** Keep the zip. It is the input for the rename plan's verification step.

---

## Follow-up plan (not part of this one): rename to LapCraft

Order matters: **this plan ships first and the user exports**, because a new `applicationId` cannot reach the old app's `filesDir`.

Inventory for the rename plan (measured 2026-11-13):

- `app/build.gradle.kts`: `namespace`, `applicationId`.
- `settings.gradle.kts`: `rootProject.name`.
- Move four source roots: `app/src/{main,test}/kotlin/io/github/jlai403/pocketsimengineer`, `core/src/{main,test}/kotlin/…` → `…/lapcraft`; rewrite 160 files' `package`/`import` lines.
- `AndroidManifest.xml`: `android:label`, `android:name` for `PocketSimEngineerApp`, all component FQNs.
- Classes: `PocketSimEngineerApp` → `LapCraftApp`, `PocketSimEngineerTheme` → `LapCraftTheme` (25+ test files).
- `UpdateManager`: release URL repo name, `ACTION_INSTALL_STATUS`.
- `TelemetryService` wakelock tags + `"PSE"` log tags, `GridStatsProfileClient` User-Agent, `RecordScreen` diagnostics header.
- `ManifestComponentsTest.NAMESPACE`, `UpdateManifest.apk` default.
- CI: APK names, artifact names, `gh release` asset names; then rename the GitHub repo (old URLs redirect).
- README/DESIGN/GLOSSARY; leave dated specs and plans as historical records.

Verification: full gradle check, install LapCraft beside the old app, import the zip from Task 9, confirm the same lap count and that a replay plays, then uninstall the old package.
