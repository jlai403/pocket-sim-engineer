package io.github.jlai403.pocketsimengineer.app.service

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.lap.LapDetector
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.core.protocol.DecodeResult
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Decoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class UdpTelemetrySource(
    private val host: String,
    private val decoder: Gt7Decoder,
    private val lapDetector: LapDetector,
    private val stats: TelemetryStats,
    private val onSample: (Sample) -> Unit,
    private val onLap: (LapBoundary) -> Unit,
    private val onState: (TelemetryUiState) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastPacketNanos: Long? = null
    private var lastSample: Sample? = null
    private var lastPublishNanos = 0L

    fun start() {
        stop()
        val address = InetAddress.getByName(host)
        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(33740))
            soTimeout = 500
        }
        socket = newSocket
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    val payload = "A".toByteArray()
                    newSocket.send(DatagramPacket(payload, payload.size, address, 33739))
                } catch (e: Exception) {
                    if (isActive) onState(TelemetryUiState(status = "Heartbeat failed: ${e.message}"))
                }
                delay(100)
            }
        }
        receiveJob = scope.launch {
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    newSocket.receive(packet)
                    if (packet.address.hostAddress != host) continue
                    val now = System.nanoTime()
                    when (val result = decoder.decode(packet.data.copyOf(packet.length), now)) {
                        is DecodeResult.Success -> {
                            stats.onPacket(now)
                            lastPacketNanos = now
                            lastSample = result.sample
                            onSample(result.sample)
                            lapDetector.onSample(result.sample)?.let(onLap)
                            publish(now)
                        }
                        is DecodeResult.Invalid -> {
                            stats.onDecodeError()
                            publish(now)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    publish(System.nanoTime())
                } catch (e: Exception) {
                    if (isActive) {
                        onState(TelemetryUiState(status = "Telemetry socket error: ${e.message}"))
                    }
                    break
                }
            }
        }
    }

    fun stop() {
        receiveJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close()
        socket = null
        scope.cancel()
    }

    private fun publish(nowNanos: Long) {
        if (nowNanos - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = nowNanos
        val sample = lastSample
        val connected = lastPacketNanos?.let { nowNanos - it < 2_000_000_000L } == true
        onState(
            TelemetryUiState(
                connected = connected,
                packetsPerSecond = stats.packetsPerSecond(nowNanos),
                decodeErrors = stats.decodeErrors(),
                lapCount = sample?.lapCount ?: 0,
                speedMps = sample?.speedMps ?: 0f,
                gear = sample?.gear ?: 0,
                lastLapTimeMs = sample?.lastLapTimeMs ?: 0,
                status = if (connected) "Telemetry connected" else "Waiting for telemetry",
            ),
        )
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 100_000_000L
    }
}
