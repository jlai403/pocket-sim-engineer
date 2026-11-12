package io.github.jlai403.pocketsimengineer.app.service

import io.github.jlai403.pocketsimengineer.core.lap.LapBoundary
import io.github.jlai403.pocketsimengineer.core.lap.LapDetector
import io.github.jlai403.pocketsimengineer.core.model.Sample
import io.github.jlai403.pocketsimengineer.app.Diagnostics
import io.github.jlai403.pocketsimengineer.core.protocol.DecodeResult
import io.github.jlai403.pocketsimengineer.core.protocol.DecodeFailureLog
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Decoder
import io.github.jlai403.pocketsimengineer.core.protocol.Gt7Protocol
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
    private val onStatus: (String) -> Unit = {},
) {
    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastPacketNanos: Long? = null
    private var lastSample: Sample? = null
    private var lastPublishNanos = 0L
    private var rawPackets = 0L
    private var foreignPackets = 0L
    private var lastForeignSource = ""
    private val decodeFailures = DecodeFailureLog()
    private var lastDecodeFailure = ""

    /** Returns null on success, or a message when the address or socket cannot be used. */
    fun start(): String? {
        stop()
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            ?: return "Could not resolve $host"
        val newSocket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(Gt7Protocol.PORT_RECEIVE))
                soTimeout = 500
            }
        }.getOrNull() ?: return "Could not open UDP ${Gt7Protocol.PORT_RECEIVE}. Another app may be using it."
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        socket = newSocket

        heartbeatJob = newScope.launch {
            while (isActive) {
                try {
                    val payload = Gt7Protocol.HEARTBEAT.toByteArray()
                    newSocket.send(DatagramPacket(payload, payload.size, address, Gt7Protocol.PORT_SEND))
                } catch (error: Exception) {
                    if (isActive) onStatus("Heartbeat failed: ${error.message}")
                }
                delay(100)
            }
        }
        receiveJob = newScope.launch {
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    newSocket.receive(packet)
                    val now = System.nanoTime()
                    rawPackets++
                    if (packet.address.hostAddress != host) {
                        foreignPackets++
                        lastForeignSource = packet.address.hostAddress
                        publish(now)
                        continue
                    }
                    when (val result = decoder.decode(packet.data.copyOf(packet.length), now)) {
                        is DecodeResult.Success -> {
                            stats.onPacket(now)
                            lastPacketNanos = now
                            lastSample = result.sample
                            runCatching { onSample(result.sample) }
                            runCatching { lapDetector.onSample(result.sample)?.let(onLap) }
                            publish(now)
                        }
                        is DecodeResult.Invalid -> {
                            stats.onDecodeError()
                            if (decodeFailures.record(packet.length, result.reason)) {
                                lastDecodeFailure = "${packet.length} ${result.reason}"
                                Diagnostics.log(
                                    "decode failed size=${packet.length} reason=${result.reason}",
                                )
                            }
                            publish(now)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    publish(System.nanoTime())
                } catch (error: Exception) {
                    if (isActive) onStatus("Telemetry socket error: ${error.message}")
                    break
                }
            }
        }
        return null
    }

    fun stop() {
        receiveJob?.cancel()
        heartbeatJob?.cancel()
        socket?.close()
        socket = null
        scope?.cancel()
        scope = null
    }

    private fun publish(nowNanos: Long) {
        if (nowNanos - lastPublishNanos < PUBLISH_INTERVAL_NANOS) return
        lastPublishNanos = nowNanos
        val sample = lastSample
        val lastPacket = lastPacketNanos
        val connected = lastPacket?.let { nowNanos - it < 2_000_000_000L } == true
        onState(
            TelemetryUiState(
                connected = connected,
                packetsPerSecond = stats.packetsPerSecond(nowNanos),
                decodeErrors = stats.decodeErrors(),
                acceptedPackets = stats.acceptedPackets(),
                rawPackets = rawPackets,
                foreignPackets = foreignPackets,
                lastForeignSource = lastForeignSource,
                lastDecodeFailure = lastDecodeFailure,
                lastPacketAgeMs = lastPacket?.let { (nowNanos - it) / 1_000_000 } ?: -1,
                lapCount = sample?.lapCount ?: 0,
                speedMps = sample?.speedMps ?: 0f,
                gear = sample?.gear ?: 0,
                throttle = sample?.throttle ?: 0,
                brake = sample?.brake ?: 0,
                rpm = sample?.rpm ?: 0f,
                packetId = sample?.packetId ?: 0,
                carCode = sample?.carCode ?: 0,
                flags = sample?.flags ?: 0,
                lastLapTimeMs = sample?.lastLapTimeMs ?: 0,
                status = if (connected) "Telemetry connected" else "Waiting for telemetry",
            ),
        )
    }

    private companion object {
        const val PUBLISH_INTERVAL_NANOS = 100_000_000L
    }
}
