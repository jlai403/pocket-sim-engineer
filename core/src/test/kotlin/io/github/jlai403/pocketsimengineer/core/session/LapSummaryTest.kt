package io.github.jlai403.pocketsimengineer.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LapSummaryTest {
    @TempDir
    lateinit var temp: File

    private fun install(
        id: String = "s1",
        videoFile: String = "",
        createFile: Boolean = false,
        lapTimeMs: Int = 90_000,
        createdAt: String = "2026-11-11T10:00:00Z",
        trackLabel: String = "Suzuka",
    ): SessionStore {
        val store = SessionStore(temp)
        val lap = LapMetadata(
            index = 1,
            lapTimeMs = lapTimeMs,
            startNanos = 0,
            endNanos = lapTimeMs * 1_000_000L,
            sampleCount = 1,
            videoFile = videoFile,
            videoStartOffsetMs = 0,
        )
        store.save(
            SessionMetadata(
                sessionId = id,
                createdAt = createdAt,
                trackLabel = trackLabel,
                carCode = 3474,
                carName = "Car #3474",
                rawFile = "",
                laps = listOf(lap),
            ),
        )
        if (createFile && videoFile.isNotBlank()) {
            File(store.sessionDir(id), videoFile).apply {
                parentFile?.mkdirs()
                writeText("x")
            }
        }
        return store
    }

    @Test
    fun `summaries flatten sessions into lap cards`() {
        val summary = install().summaries().single()
        assertFalse(summary.hasReplay)
        assertEquals(1, summary.lapIndex)
        assertEquals("Suzuka", summary.trackLabel)
    }

    @Test
    fun `a clip on disk marks the lap as having a replay`() {
        val summary = install(videoFile = "laps/lap-001.mp4", createFile = true).summaries().single()
        assertTrue(summary.hasReplay)
    }

    @Test
    fun `legacy lap without a clip stays visible`() {
        val summary = install(videoFile = "laps/lap-001.mp4", createFile = false).summaries().single()
        assertFalse(summary.hasReplay)
    }

    @Test
    fun `deleteLap removes the session when the last lap goes`() {
        val store = install()
        assertTrue(store.deleteLap("s1", 1))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `shared legacy raw is not a replay and survives deleting one lap`() {
        val store = SessionStore(temp)
        val raw = "raw.mp4"
        val laps = (1..2).map { index ->
            LapMetadata(
                index = index,
                lapTimeMs = 90_000 + index,
                startNanos = (index - 1) * 100_000_000_000L,
                endNanos = index * 100_000_000_000L,
                sampleCount = 1,
                videoFile = raw,
                videoStartOffsetMs = 0,
                needsCut = true,
            )
        }
        store.save(
            SessionMetadata(
                sessionId = "legacy",
                createdAt = "2026-11-10T10:00:00Z",
                trackLabel = "Suzuka",
                carCode = 3474,
                carName = "Car #3474",
                rawFile = raw,
                laps = laps,
            ),
        )
        File(store.sessionDir("legacy"), raw).writeText("raw")

        assertEquals(2, store.summaries().size)
        assertTrue(store.summaries().none { it.hasReplay })

        assertTrue(store.deleteLap("legacy", 1))
        assertTrue(File(store.sessionDir("legacy"), raw).isFile)
        assertEquals(1, store.summaries().size)

        assertTrue(store.deleteLap("legacy", 2))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `attachClip clears the source and sets the playback offset`() {
        val store = install()
        val source = "laps/lap-001-source.mp4"
        File(store.sessionDir("s1"), source).apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        store.setSource("s1", 1, source)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        val lap = store.lap("s1", 1)!!
        assertEquals("laps/lap-001.mp4", lap.videoFile)
        assertEquals(1_234, lap.videoStartOffsetMs)
        assertNull(lap.sourceFile)
        assertFalse(File(store.sessionDir("s1"), source).exists())
    }

    @Test
    fun `attachClip resets the sync offset from the previous clip`() {
        val store = install()
        store.setSyncOffset("s1", 250)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        assertEquals(0, store.lap("s1", 1)!!.syncOffsetMs)
    }

    @Test
    fun `attachClip deletes the clip it supersedes`() {
        val store = install(videoFile = "laps/lap-001-old.mp4", createFile = true)

        store.attachClip("s1", 1, "laps/lap-001.mp4", 1_234)

        assertFalse(File(store.sessionDir("s1"), "laps/lap-001-old.mp4").exists())
    }

    @Test
    fun `filters by replay and track`() {
        install(id = "s1", trackLabel = "Suzuka")
        install(
            id = "s2",
            trackLabel = "Nurburgring",
            videoFile = "laps/lap-001.mp4",
            createFile = true,
        )
        val laps = install().summaries()
        assertEquals(2, laps.size)

        val withReplay = laps.applyFilter(LapFilter(hasReplay = true))
        assertEquals(listOf("s2"), withReplay.map { it.sessionId })

        val suzuka = laps.applyFilter(LapFilter(trackLabel = "Suzuka"))
        assertEquals(listOf("s1"), suzuka.map { it.sessionId })
    }

    @Test
    fun `sorts newest fastest and by track`() {
        install(id = "old", lapTimeMs = 95_000, createdAt = "2026-11-10T10:00:00Z", trackLabel = "B")
        install(id = "fast", lapTimeMs = 80_000, createdAt = "2026-11-11T10:00:00Z", trackLabel = "A")

        val laps = SessionStore(temp).summaries()
        assertEquals(listOf("fast", "old"), laps.applySort(LapSort.Newest).map { it.sessionId })
        assertEquals(listOf("fast", "old"), laps.applySort(LapSort.Fastest).map { it.sessionId })
        assertEquals(listOf("fast", "old"), laps.applySort(LapSort.Track).map { it.sessionId })
    }
}
