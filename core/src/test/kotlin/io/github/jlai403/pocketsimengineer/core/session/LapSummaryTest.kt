package io.github.jlai403.pocketsimengineer.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
