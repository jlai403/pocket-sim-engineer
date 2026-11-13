package io.github.jlai403.lapcraft.app.ui

import android.content.Context
import io.github.jlai403.lapcraft.core.model.Sample
import io.github.jlai403.lapcraft.core.session.LapMetadata
import io.github.jlai403.lapcraft.core.session.LapTelemetryCodec
import io.github.jlai403.lapcraft.core.session.SessionMetadata
import io.github.jlai403.lapcraft.core.session.SessionStore
import java.io.File

/** Builds on-disk sessions so UI tests exercise the real store and codecs. */
object SessionFixtures {

    fun session(
        id: String = "session-1",
        trackLabel: String = "Suzuka",
        carCode: Int = 3474,
        carName: String = "BRZ GT300 '21",
        createdAt: String = "2026-11-10T10:00:00Z",
        lapTimesMs: List<Int> = listOf(90_000, 91_500),
        needsCut: Boolean = false,
        withReplay: Boolean = true,
        pendingSource: Boolean = false,
        driverPsnId: String? = null,
    ): SessionMetadata {
        val laps = lapTimesMs.mapIndexed { index, lapTime ->
            LapMetadata(
                index = index + 1,
                lapTimeMs = lapTime,
                startNanos = index * 100_000_000_000L,
                endNanos = index * 100_000_000_000L + lapTime * 1_000_000L,
                sampleCount = 100,
                videoFile = when {
                    needsCut -> "raw.mp4"
                    withReplay -> "laps/lap-%03d.mp4".format(index + 1)
                    else -> ""
                },
                leadInMs = 2_000,
                needsCut = needsCut,
                sourceFile = if (pendingSource) "laps/lap-%03d-source.mp4".format(index + 1) else null,
                driverPsnId = driverPsnId,
            )
        }
        return SessionMetadata(
            sessionId = id,
            createdAt = createdAt,
            trackLabel = trackLabel,
            recordingStartNanos = 1_000_000_000L,
            carCode = carCode,
            carName = carName,
            rawFile = "raw.mp4",
            audioFile = "audio.m4a",
            laps = laps,
        )
    }

    fun install(context: Context, metadata: SessionMetadata) {
        val store = SessionStore(context.filesDir)
        store.save(metadata)
        val lapsDir = File(store.sessionDir(metadata.sessionId), "laps").apply { mkdirs() }
        for (lap in metadata.laps) {
            File(lapsDir, "lap-%03d.gt7lap".format(lap.index))
                .writeBytes(LapTelemetryCodec.encode(samplesFor(lap)))
            if (lap.videoFile.startsWith("laps/")) {
                File(store.sessionDir(metadata.sessionId), lap.videoFile).apply {
                    parentFile?.mkdirs()
                    writeText("video")
                }
            }
            lap.sourceFile?.let { source ->
                File(store.sessionDir(metadata.sessionId), source).apply {
                    parentFile?.mkdirs()
                    writeText("source")
                }
            }
        }
    }

    fun sample(
        timeNanos: Long,
        speedMps: Float = 50f,
        throttle: Int = 200,
        brake: Int = 10,
        gear: Int = 4,
        rpm: Float = 7_000f,
        x: Float = 0f,
        z: Float = 0f,
        steering: Float = 0f,
    ) = Sample(
        timeNanos = timeNanos,
        speedMps = speedMps,
        throttle = throttle,
        brake = brake,
        gear = gear,
        rpm = rpm,
        positionX = x,
        positionY = 0f,
        positionZ = z,
        lapCount = 1,
        bestLapTimeMs = 90_000,
        lastLapTimeMs = 90_000,
        flags = 1,
        carCode = 3474,
        fuel = 40f,
        tyreTempFL = 80f,
        tyreTempFR = 81f,
        tyreTempRL = 82f,
        tyreTempRR = 83f,
        packetId = 1,
        steering = steering,
    )

    private fun samplesFor(lap: LapMetadata): List<Sample> {
        val start = lap.startNanos
        val span = (lap.endNanos - start).coerceAtLeast(1_000_000L)
        return (0 until 100).map { index ->
            val fraction = index / 99f
            val angle = fraction * Math.PI.toFloat() * 2f
            sample(
                timeNanos = start + (span * index / 99),
                speedMps = 40f + 30f * fraction,
                brake = if (fraction in 0.2f..0.35f) 200 else 0,
                x = 100f * kotlin.math.cos(angle),
                z = 100f * kotlin.math.sin(angle),
            )
        }
    }
}
