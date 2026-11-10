# Pocket Sim Engineer Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Android app that receives and decodes GT7 telemetry, records Chiaki-Up's screen and internal audio, and writes per-lap markers so we can prove telemetry/video synchronization.

**Architecture:** Two Gradle modules: a plain Kotlin `:core` for protocol, samples, lap detection, and session files; an Android `:app` for Compose UI, UDP service, MediaProjection recording, and notifications. The core has no Android imports so it can later become a Rust/desktop core behind the same interfaces.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX, Kotlinx Serialization, BouncyCastle Salsa20, MediaCodec/MediaMuxer, Media3, JUnit.

**Spec:** `docs/specs/2026-11-10-pocket-sim-engineer.md`

## Global Constraints

- Package: `io.github.jlai403.pocketsimengineer`.
- Working title: Pocket Sim Engineer.
- `minSdk 29`, `targetSdk 36`, `compileSdk 36`.
- Primary device: Pixel 10, Android 16.
- `:core` is a Kotlin JVM module with no Android imports.
- v0.1 capture target: Chiaki-Up only; PXPlay is undocumented fallback.
- Default video: 1080p60 H.264, ~10 Mbps. Fallback: 720p60.
- Audio: AAC-LC 48 kHz stereo, ~160 kbps; silent fallback if capture is blocked.
- Session closes and recording stops after 5 s off-track or 5 s without packets.
- Only completed laps with a plausible lap time become lap files.
- No cloud, accounts, overlays, or Play Store distribution in this spike.
- License: GPLv3 + CLA.
- Future Rust native libraries must be 16 KB page aligned.

## Review Focus

These are the inputs/failure modes most likely to bite a user, and the spec does not fully pin them down. Each is tested in the task that owns it.

1. **Real GT7 packets differ from synthetic test packets.** A synthetic Salsa20 round-trip can pass while the real IV placement, XOR constant, or packet size is wrong. Task 11 must verify against a live console.
2. **Chiaki-Up may opt out of audio playback capture.** The app can record silence without error. Task 9 must detect silence and Task 10 must surface the fallback.
3. **Lap boundaries fall between 60 Hz samples.** A marker can be one or more frames late. Task 11 must check the marker against the visible lap line, not just against the code's own timestamp.
4. **MediaProjection/foreground-service lifecycle on Android 16.** Switching to Chiaki-Up, pulling the notification shade, locking the screen, or stopping projection can end recording. Tasks 8, 10, and 11 must exercise each transition.
5. **1080p60 encode plus Chiaki-Up decode thermal load.** The Pixel 10 may throttle after minutes. Task 11 must run a 10-minute test and confirm the 720p60 fallback.

---

## File Structure

```text
gt7-telemetry/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── README.md
├── core/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/io/github/jlai403/pocketsimengineer/core/
│       │   ├── clock/Clock.kt
│       │   ├── model/Sample.kt
│       │   ├── protocol/Gt7Protocol.kt
│       │   ├── protocol/Gt7Crypto.kt
│       │   ├── protocol/PacketAParser.kt
│       │   ├── protocol/Gt7Decoder.kt
│       │   ├── lap/LapDetector.kt
│       │   └── session/
│       │       ├── SessionMetadata.kt
│       │       ├── LapMetadata.kt
│       │       ├── SessionJsonCodec.kt
│       │       └── LapTelemetryCodec.kt
│       └── test/kotlin/io/github/jlai403/pocketsimengineer/core/
│           ├── protocol/Gt7ProtocolTest.kt
│           ├── protocol/Gt7CryptoTest.kt
│           ├── protocol/PacketAParserTest.kt
│           ├── protocol/TestPacketFactory.kt
│           ├── lap/LapDetectorTest.kt
│           └── session/SessionCodecTest.kt
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/AndroidManifest.xml
│       ├── main/kotlin/io/github/jlai403/pocketsimengineer/app/
│       │   ├── MainActivity.kt
│       │   ├── PocketSimEngineerApp.kt
│       │   ├── service/TelemetryService.kt
│       │   ├── service/TelemetryStats.kt
│       │   ├── service/UdpTelemetrySource.kt
│       │   ├── recording/ScreenRecorder.kt
│       │   ├── recording/AudioCapture.kt
│       │   ├── recording/Recorder.kt
│       │   ├── recording/RecordingController.kt
│       │   ├── recording/RecordingEvent.kt
│       │   ├── recording/RecordingState.kt
│       │   ├── recording/RecordingNotification.kt
│       │   ├── storage/SessionStorage.kt
│       │   └── ui/App.kt
│       └── test/kotlin/io/github/jlai403/pocketsimengineer/app/
│           ├── recording/RecordingControllerTest.kt
│           └── service/TelemetryStatsTest.kt
└── docs/
    ├── specs/2026-11-10-pocket-sim-engineer.md
    └── superpowers/plans/2026-11-10-pocket-sim-engineer-spike.md
```

---

### Task 1: Project scaffold and protocol constants

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `core/build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/PocketSimEngineerApp.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Protocol.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/clock/Clock.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7ProtocolTest.kt`
- Create: `README.md`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object Gt7Protocol` with `KEY: ByteArray`, `MAGIC: Int`, `PACKET_A_SIZE: Int`, `PORT_RECEIVE: Int`, `PORT_SEND: Int`.
  - `interface Clock { fun nanoTime(): Long }`.
  - Android application ID `io.github.jlai403.pocketsimengineer`.

- [ ] **Step 1: Write the failing protocol constants test**

Create `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7ProtocolTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Gt7ProtocolTest {
    @Test
    fun `protocol constants match the GT7 wire format`() {
        assertEquals(0x47375330, Gt7Protocol.MAGIC)
        assertEquals(33740, Gt7Protocol.PORT_RECEIVE)
        assertEquals(33739, Gt7Protocol.PORT_SEND)
        assertEquals(296, Gt7Protocol.PACKET_A_SIZE)
        assertEquals(32, Gt7Protocol.KEY.size)
        assertEquals("Simulator Interface Packet GT7 v", Gt7Protocol.KEY.decodeToString())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests '*Gt7ProtocolTest*'`
Expected: FAIL because `Gt7Protocol` and the Gradle project do not exist.

- [ ] **Step 3: Create the Gradle scaffold and constants**

Create the root Gradle files, `:core` as `kotlin("jvm")`, `:app` as `com.android.application`, and the minimal Compose activity.

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PocketSimEngineer"
include(":core", ":app")
```

Root `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}
```

`core/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.jlai403.pocketsimengineer"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.jlai403.pocketsimengineer"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    testImplementation("junit:junit:4.13.2")
}
```

Create `app/src/main/AndroidManifest.xml` with the spike permissions and a single launcher activity. Include `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `INTERNET`, `ACCESS_NETWORK_STATE`.

Create a minimal `MainActivity` that renders text `Pocket Sim Engineer` and `Waiting for telemetry` in Compose.

Create `core/.../protocol/Gt7Protocol.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.protocol

object Gt7Protocol {
    val KEY = "Simulator Interface Packet GT7 v".encodeToByteArray()
    const val MAGIC = 0x47375330
    const val PACKET_A_SIZE = 296
    const val PORT_RECEIVE = 33740
    const val PORT_SEND = 33739
}
```

Create `core/.../clock/Clock.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.clock

interface Clock {
    fun nanoTime(): Long
}
```

Add a short `README.md` linking the spec and plan.

- [ ] **Step 4: Run the test and the Android build**

Run: `./gradlew :core:test --tests '*Gt7ProtocolTest*'`
Expected: PASS.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml core app README.md
git commit -m "chore: scaffold Pocket Sim Engineer Android and core modules"
```

---

### Task 2: Salsa20 decryption and IV handling

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Crypto.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/TestPacketFactory.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7CryptoTest.kt`

**Interfaces:**
- Consumes: `Gt7Protocol.KEY`, `Gt7Protocol.MAGIC`.
- Produces: `object Gt7Crypto { fun decrypt(datagram: ByteArray): ByteArray? }`.

- [ ] **Step 1: Write the failing crypto tests**

Create `TestPacketFactory.kt`. It builds a 296-byte packet A, encrypts it with Salsa20, then overwrites bytes `0x40..0x44` of the ciphertext with the IV seed to match GT7's wire format.

```kotlin
package io.github.jlai403.pocketsimengineer.core.protocol

import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TestPacketFactory {
    fun packetA(
        magic: Int = Gt7Protocol.MAGIC,
        speedMps: Float = 42.5f,
        lapCount: Short = 2,
        lastLapTimeMs: Int = 90_000,
        packetId: Int = 7,
        iv1: Int = 0x11223344,
    ): ByteArray {
        val plain = ByteArray(Gt7Protocol.PACKET_A_SIZE)
        val b = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, magic)
        b.putFloat(0x4C, speedMps)
        b.putInt(0x70, packetId)
        b.putShort(0x74, lapCount)
        b.putInt(0x7C, lastLapTimeMs)

        val iv2 = iv1 xor 0xDEADBEAF.toInt()
        val nonce = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(iv2).putInt(iv1).array()
        val engine = Salsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(Gt7Protocol.KEY), nonce))
        val encrypted = ByteArray(plain.size)
        engine.processBytes(plain, 0, plain.size, encrypted, 0)

        ByteBuffer.wrap(encrypted).order(ByteOrder.LITTLE_ENDIAN).putInt(0x40, iv1)
        return encrypted
    }
}
```

Create `Gt7CryptoTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Gt7CryptoTest {
    @Test
    fun `decrypts a valid packet`() {
        val encrypted = TestPacketFactory.packetA(speedMps = 42.5f)
        val plain = Gt7Crypto.decrypt(encrypted)!!
        assertEquals(Gt7Protocol.MAGIC, ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(42.5f, ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN).getFloat(0x4C))
    }

    @Test
    fun `rejects a short datagram`() {
        assertNull(Gt7Crypto.decrypt(ByteArray(10)))
    }

    @Test
    fun `rejects a bad magic`() {
        val encrypted = TestPacketFactory.packetA(magic = 0x12345678)
        assertNull(Gt7Crypto.decrypt(encrypted))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests '*Gt7CryptoTest*'`
Expected: FAIL because `Gt7Crypto.decrypt` does not exist.

- [ ] **Step 3: Implement `Gt7Crypto.decrypt`**

Implementation details:
- Reject datagrams shorter than 68 bytes.
- `iv1` = little-endian int at `0x40`.
- `iv2 = iv1 xor 0xDEADBEAF.toInt()`.
- Nonce = `iv2` then `iv1`, both little-endian.
- Decrypt the full datagram with BouncyCastle `Salsa20Engine`.
- Validate little-endian magic `0x47375330` at offset 0.
- Return the decrypted bytes or `null`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests '*Gt7CryptoTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main core/src/test
git commit -m "feat: decrypt GT7 Salsa20 telemetry packets"
```

---

### Task 3: Packet A parser and sample model

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/model/Sample.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/PacketAParser.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/Gt7Decoder.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/protocol/PacketAParserTest.kt`

**Interfaces:**
- Consumes: `Gt7Crypto.decrypt`.
- Produces:
  - `data class Sample(...)` with the fields listed in the spec.
  - `object PacketAParser { fun parse(decrypted: ByteArray, receivedAtNanos: Long): Sample }`.
  - `class Gt7Decoder : TelemetryDecoder`.
  - `sealed interface DecodeResult { data class Success(val sample: Sample); data class Invalid(val reason: String) }`.
  - `fun interface TelemetryDecoder { fun decode(datagram: ByteArray, receivedAtNanos: Long): DecodeResult }`.

- [ ] **Step 1: Write the failing parser test**

Create `PacketAParserTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketAParserTest {
    @Test
    fun `parses packet A fields at their documented offsets`() {
        val packet = ByteArray(Gt7Protocol.PACKET_A_SIZE)
        val b = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x00, Gt7Protocol.MAGIC)
        b.putFloat(0x04, 1.5f)
        b.putFloat(0x08, 2.5f)
        b.putFloat(0x0C, 3.5f)
        b.putFloat(0x3C, 7000f)
        b.putFloat(0x44, 45.5f)
        b.putFloat(0x4C, 42.5f)
        b.putFloat(0x60, 80f)
        b.putFloat(0x64, 81f)
        b.putFloat(0x68, 82f)
        b.putFloat(0x6C, 83f)
        b.putInt(0x70, 99)
        b.putShort(0x74, 3)
        b.putInt(0x78, 88_000)
        b.putInt(0x7C, 90_000)
        b.putShort(0x8E, 1)
        packet[0x90] = 4
        packet[0x91] = 200.toByte()
        packet[0x92] = 10
        b.putInt(0x124, 3474)

        val sample = PacketAParser.parse(packet, receivedAtNanos = 1234L)

        assertEquals(42.5f, sample.speedMps)
        assertEquals(1.5f, sample.positionX)
        assertEquals(2.5f, sample.positionY)
        assertEquals(3.5f, sample.positionZ)
        assertEquals(7000f, sample.rpm)
        assertEquals(45.5f, sample.fuel)
        assertEquals(80f, sample.tyreTempFL)
        assertEquals(83f, sample.tyreTempRR)
        assertEquals(99, sample.packetId)
        assertEquals(3, sample.lapCount)
        assertEquals(88_000, sample.bestLapTimeMs)
        assertEquals(90_000, sample.lastLapTimeMs)
        assertEquals(1, sample.flags and 1)
        assertEquals(4, sample.gear)
        assertEquals(200, sample.throttle)
        assertEquals(10, sample.brake)
        assertEquals(3474, sample.carCode)
        assertEquals(1234L, sample.timeNanos)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests '*PacketAParserTest*'`
Expected: FAIL because `Sample` and `PacketAParser` do not exist.

- [ ] **Step 3: Implement `Sample`, `PacketAParser`, and `Gt7Decoder`**

`Sample` fields: `timeNanos`, `speedMps`, `throttle`, `brake`, `gear`, `rpm`, `positionX`, `positionY`, `positionZ`, `lapCount`, `bestLapTimeMs`, `lastLapTimeMs`, `flags`, `carCode`, `fuel`, `tyreTempFL`, `tyreTempFR`, `tyreTempRL`, `tyreTempRR`, `packetId`, `steering`, `currentLapTimeMs`.

`PacketAParser.parse`:
- Read little-endian fields at the spec offsets.
- `gear = packet[0x90].toInt() and 0x0F` (current gear in low nibble).
- `throttle = packet[0x91].toInt() and 0xFF`.
- `brake = packet[0x92].toInt() and 0xFF`.
- `steering = 0f`, `currentLapTimeMs = 0` for packet A.
- Set `timeNanos = receivedAtNanos`.

`Gt7Decoder.decode`:
- Call `Gt7Crypto.decrypt`.
- If null, return `DecodeResult.Invalid("decrypt-or-magic")`.
- If decrypted size != 296, return `DecodeResult.Invalid("unsupported-size:<n>")`.
- Else `DecodeResult.Success(PacketAParser.parse(decrypted, receivedAtNanos))`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests '*PacketAParserTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main core/src/test
git commit -m "feat: parse GT7 packet A into telemetry samples"
```

---

### Task 4: Lap detection

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetector.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/lap/LapDetectorTest.kt`

**Interfaces:**
- Consumes: `Sample`.
- Produces:
  - `data class LapBoundary(val lapNumber: Int, val startNanos: Long, val endNanos: Long, val lapTimeMs: Int)`.
  - `class LapDetector { fun onSample(sample: Sample): LapBoundary? }`.

- [ ] **Step 1: Write the failing lap detector tests**

Create `LapDetectorTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.lap

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LapDetectorTest {
    private fun sample(
        timeNanos: Long,
        lapCount: Int,
        lastLapTimeMs: Int = 0,
        packetId: Int = 0,
    ) = Sample(
        timeNanos = timeNanos,
        speedMps = 0f,
        throttle = 0,
        brake = 0,
        gear = 1,
        rpm = 0f,
        positionX = 0f,
        positionY = 0f,
        positionZ = 0f,
        lapCount = lapCount,
        bestLapTimeMs = 0,
        lastLapTimeMs = lastLapTimeMs,
        flags = 1,
        carCode = 3474,
        fuel = 0f,
        tyreTempFL = 0f,
        tyreTempFR = 0f,
        tyreTempRL = 0f,
        tyreTempRR = 0f,
        packetId = packetId,
        steering = 0f,
        currentLapTimeMs = 0,
    )

    @Test
    fun `ignores the out lap`() {
        val detector = LapDetector()
        assertNull(detector.onSample(sample(0, 0)))
        assertNull(detector.onSample(sample(1_000_000_000, 1, lastLapTimeMs = 0)))
    }

    @Test
    fun `emits the first completed lap`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1))
        val boundary = detector.onSample(sample(90_000_000_000, 2, lastLapTimeMs = 90_000))
        assertEquals(1, boundary?.lapNumber)
        assertEquals(90_000, boundary?.lapTimeMs)
        assertEquals(0L, boundary?.startNanos)
        assertEquals(90_000_000_000L, boundary?.endNanos)
    }

    @Test
    fun `ignores duplicate packet ids`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1, packetId = 10))
        detector.onSample(sample(1_000_000_000, 1, packetId = 10))
        val boundary = detector.onSample(sample(90_000_000_000, 2, 90_000, packetId = 11))
        assertEquals(1, boundary?.lapNumber)
    }

    @Test
    fun `computes lap time when last lap time is missing`() {
        val detector = LapDetector()
        detector.onSample(sample(0, 1))
        val boundary = detector.onSample(sample(75_000_000_000, 2, lastLapTimeMs = 0))
        assertEquals(75_000, boundary?.lapTimeMs)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests '*LapDetectorTest*'`
Expected: FAIL because `LapDetector` does not exist.

- [ ] **Step 3: Implement `LapDetector`**

Behavior:
- Keep `lastPacketId`, `lastTimeNanos`, `previousLapCount`, `lapStartNanos`.
- On first sample, set `lapStartNanos = sample.timeNanos`.
- Ignore samples with `timeNanos <= lastTimeNanos` or `packetId == lastPacketId`.
- When `lapCount > previousLapCount`:
  - `lapNumber = lapCount - 1`.
  - If `lapNumber <= 0`, update state and return null (out lap).
  - `lapTimeMs = if (lastLapTimeMs > 0) lastLapTimeMs else ((timeNanos - lapStartNanos) / 1_000_000).toInt()`.
  - Emit `LapBoundary(lapNumber, lapStartNanos, timeNanos, lapTimeMs)`.
  - Set `lapStartNanos = timeNanos`.
- Update `previousLapCount`, `lastPacketId`, `lastTimeNanos`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests '*LapDetectorTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main core/src/test
git commit -m "feat: detect completed GT7 laps"
```

---

### Task 5: Session JSON and binary telemetry codec

**Files:**
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionMetadata.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapMetadata.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionJsonCodec.kt`
- Create: `core/src/main/kotlin/io/github/jlai403/pocketsimengineer/core/session/LapTelemetryCodec.kt`
- Create: `core/src/test/kotlin/io/github/jlai403/pocketsimengineer/core/session/SessionCodecTest.kt`

**Interfaces:**
- Consumes: `Sample`.
- Produces:
  - `@Serializable data class SessionMetadata(...)` and `@Serializable data class LapMetadata(...)`.
  - `object SessionJsonCodec { fun encodeSession(...): String; fun decodeSession(...): SessionMetadata; fun encodeLap(...): String; fun decodeLap(...): LapMetadata }`.
  - `object LapTelemetryCodec { fun encode(samples: List<Sample>): ByteArray; fun decode(bytes: ByteArray): List<Sample> }`.

- [ ] **Step 1: Write the failing codec tests**

Create `SessionCodecTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.core.session

import io.github.jlai403.pocketsimengineer.core.model.Sample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionCodecTest {
    private fun sample(t: Long, speed: Float) = Sample(
        timeNanos = t,
        speedMps = speed,
        throttle = 200,
        brake = 10,
        gear = 4,
        rpm = 7000f,
        positionX = 1f,
        positionY = 2f,
        positionZ = 3f,
        lapCount = 2,
        bestLapTimeMs = 88_000,
        lastLapTimeMs = 90_000,
        flags = 1,
        carCode = 3474,
        fuel = 45.5f,
        tyreTempFL = 80f,
        tyreTempFR = 81f,
        tyreTempRL = 82f,
        tyreTempRR = 83f,
        packetId = 99,
        steering = 0.1f,
        currentLapTimeMs = 12_345,
    )

    @Test
    fun `session json round trips`() {
        val metadata = SessionMetadata(
            schemaVersion = 1,
            sessionId = "2026-11-10T18-42-11Z",
            createdAt = "2026-11-10T18:42:11Z",
            trackLabel = "Suzuka",
            carCode = 3474,
            carName = "BRZ GT300 '21",
            syncOffsetMs = 0,
            rawFile = "raw.mp4",
            rawDeleted = false,
            keepRaw = false,
            laps = emptyList(),
        )
        assertEquals(metadata, SessionJsonCodec.decodeSession(SessionJsonCodec.encodeSession(metadata)))
    }

    @Test
    fun `lap json round trips`() {
        val lap = LapMetadata(
            schemaVersion = 1,
            index = 1,
            lapTimeMs = 90_000,
            startNanos = 0L,
            endNanos = 90_000_000_000L,
            sampleCount = 5400,
            videoFile = "lap-001.mp4",
            videoStartOffsetMs = 2000,
            syncOffsetMs = 0,
        )
        assertEquals(lap, SessionJsonCodec.decodeLap(SessionJsonCodec.encodeLap(lap)))
    }

    @Test
    fun `lap telemetry round trips`() {
        val samples = listOf(sample(0L, 42.5f), sample(16_666_667L, 43.0f))
        val bytes = LapTelemetryCodec.encode(samples)
        assertEquals("G7LP", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(2, LapTelemetryCodec.decode(bytes).size)
        assertEquals(samples, LapTelemetryCodec.decode(bytes))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:test --tests '*SessionCodecTest*'`
Expected: FAIL because the codecs do not exist.

- [ ] **Step 3: Implement the codecs**

Use `kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }` for JSON.

`SessionMetadata` fields: `schemaVersion: Int`, `sessionId: String`, `createdAt: String`, `trackLabel: String`, `carCode: Int`, `carName: String`, `syncOffsetMs: Int`, `rawFile: String`, `rawDeleted: Boolean`, `keepRaw: Boolean`, `laps: List<LapMetadata>`.

`LapMetadata` fields: `schemaVersion: Int`, `index: Int`, `lapTimeMs: Int`, `startNanos: Long`, `endNanos: Long`, `sampleCount: Int`, `videoFile: String`, `videoStartOffsetMs: Int`, `syncOffsetMs: Int`.

`SessionJsonCodec` signatures:

```kotlin
object SessionJsonCodec {
    fun encodeSession(metadata: SessionMetadata): String
    fun decodeSession(json: String): SessionMetadata
    fun encodeLap(lap: LapMetadata): String
    fun decodeLap(json: String): LapMetadata
}
```

`LapTelemetryCodec`:
- Header: `G7LP`, version `1u16`, header size `32u16`, sample count `u32`, sample rate `60u16`, reserved `0u16`, start nanos `i64`, end nanos `i64`.
- Then arrays in the spec's fixed order, little-endian.
- `decode` validates magic/version/header size and reads exactly `sampleCount` samples.
- No compression.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:test --tests '*SessionCodecTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main core/src/test
git commit -m "feat: add session JSON and columnar lap telemetry codecs"
```

---

### Task 6: Android shell and permission surface

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`

**Interfaces:**
- Consumes: `:core`.
- Produces: a launchable Compose activity with:
  - PS5 IP text field.
  - `Arm` button.
  - Status text: packets/s, lap, speed, recording state.
  - `Test record 10 s` button (used by Task 8).
  - `Stop` button.

- [ ] **Step 1: Write the UI smoke test (manual)**

The app must launch and show:
- `Pocket Sim Engineer`
- `Waiting for telemetry`
- A PS5 IP input
- `Arm`
- `Test record 10 s`

- [ ] **Step 2: Build and install to verify it fails**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL until the activity and manifest are complete.

- [ ] **Step 3: Implement the Compose shell**

Manifest additions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Activity: exported launcher, `android:screenOrientation="sensorLandscape"` not required in the shell; leave orientation free for now.

Use Material3 `Scaffold` with a dark theme. Request `POST_NOTIFICATIONS` and `RECORD_AUDIO` from the activity when the user taps `Arm`; for the shell, a button that requests them is enough.

- [ ] **Step 4: Build and verify launch**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
```
Expected: the activity opens and shows the shell text.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: add Android Compose shell and permissions"
```

---

### Task 7: UDP telemetry service and live stats

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryStats.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/UdpTelemetrySource.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Create: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryStatsTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`

**Interfaces:**
- Consumes: `Gt7Decoder`, `DecodeResult`, `Sample`, `LapDetector`.
- Produces:
  - `class TelemetryStats(val windowNanos: Long = 1_000_000_000) { fun onPacket(nowNanos: Long); fun onDecodeError(); fun packetsPerSecond(nowNanos: Long): Double; fun decodeErrors(): Long }`.
  - `data class TelemetryUiState(val connected: Boolean, val packetsPerSecond: Double, val decodeErrors: Long, val lapCount: Int, val speedMps: Float, val gear: Int, val lastLapTimeMs: Int)`.
  - `TelemetryService` with `StateFlow<TelemetryUiState>`.

- [ ] **Step 1: Write the failing stats test**

Create `TelemetryStatsTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryStatsTest {
    @Test
    fun `counts packets inside the one second window`() {
        val stats = TelemetryStats(windowNanos = 1_000_000_000)
        repeat(60) { stats.onPacket(0L) }
        assertEquals(60.0, stats.packetsPerSecond(0L), 0.001)
        assertEquals(0.0, stats.packetsPerSecond(2_000_000_000L), 0.001)
    }

    @Test
    fun `counts decode errors`() {
        val stats = TelemetryStats()
        repeat(3) { stats.onDecodeError() }
        assertEquals(3L, stats.decodeErrors())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TelemetryStatsTest*'`
Expected: FAIL because `TelemetryStats` does not exist.

- [ ] **Step 3: Implement the UDP service**

`UdpTelemetrySource`:
- Bind `DatagramSocket(null)` with `reuseAddress = true` to `0.0.0.0:33740`.
- Start a receive loop on `Dispatchers.IO`.
- Send `"A"` to `<host>:33739` every 100 ms on a separate coroutine.
- Filter by source address when `host` is a literal IP.
- Call `decoder.decode(bytes, System.nanoTime())`.
- On `Success`, call the sink and `LapDetector`.
- On `Invalid`, increment the error count.
- On socket failure, emit a user-visible error state.

`TelemetryService` manifest entry:

```xml
<service
    android:name=".service.TelemetryService"
    android:exported="false"
    android:foregroundServiceType="mediaProjection" />
```

`TelemetryService`:
- Foreground service with a low-importance notification while active.
- Start/stop from the activity.
- Expose `StateFlow<TelemetryUiState>` via a singleton `TelemetryBus` or binder; for the spike, a `companion object` `MutableStateFlow` is acceptable but mark it as spike-only.
- Use a `WifiLock` and a `PARTIAL_WAKE_LOCK` while active.
- Log `packets/s`, `decode errors`, `lap`, and `speed` every 2 s with tag `PocketSimEngineer`.

- [ ] **Step 4: Verify on device**

Run:
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
adb logcat -s PocketSimEngineer
```
Enter the PS5 IP, tap `Arm`, and start a GT7 session. Expected: log lines around `packets/s=55..60`, `lap` increments, `speed` changes.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: receive and decode GT7 telemetry in a foreground service"
```

---

### Task 8: Screen recording pipeline

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/Recorder.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/ScreenRecorder.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/VideoEncoderConfig.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/ui/App.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Android `MediaProjection`.
- Produces:
  - `interface Recorder { fun start(); fun stop(); val isRecording: Boolean }`.
  - `class ScreenRecorder(context, projection, outputFile, config)`.
  - `data class VideoEncoderConfig(val width: Int = 1920, val height: Int = 1080, val fps: Int = 60, val bitrate: Int = 10_000_000, val mime: String = "video/avc")`.
  - Debug button `Test record 10 s` that requests projection and records to `files/test-<timestamp>.mp4`.

- [ ] **Step 1: Write the manual acceptance check**

After tapping `Test record 10 s`, granting capture, and waiting 10 s:
- A file appears under the app's files directory.
- `ffprobe` reports one H.264 video stream, 1920×1080, ~60 fps.
- The video is not black.

- [ ] **Step 2: Build to verify it fails**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL until `ScreenRecorder` and the test button exist.

- [ ] **Step 3: Implement the recorder**

Implementation details:
- Request `MediaProjection` via `MediaProjectionManager.createScreenCaptureIntent()`.
- Start the foreground service before backgrounding.
- Register `MediaProjection.Callback` and stop on `onStop`.
- Create a `VirtualDisplay` with `DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`.
- Configure `MediaCodec` as a surface-input H.264 encoder.
- Create a `MediaMuxer` writing MPEG-4.
- Drain output buffers on a background thread; add the track on `INFO_OUTPUT_FORMAT_CHANGED`; write samples.
- On stop: signal end-of-stream, drain, stop/release codec, release muxer, virtual display, and projection.
- Request a keyframe every 1 s.
- Log encoder format and any `MediaCodec.CodecException`.

- [ ] **Step 4: Verify on device**

Run:
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
```
Tap `Test record 10 s`, grant capture, wait, then:
```bash
adb exec-out run-as io.github.jlai403.pocketsimengineer tar -C files -czf - . > spike-files.tgz
tar -xzf spike-files.tgz
ffprobe -v error -show_entries stream=codec_name,width,height,r_frame_rate -of default=nw=1 test-*.mp4
```
Expected: `codec_name=h264`, `width=1920`, `height=1080`, `r_frame_rate=60/1` (or close).

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: record the display to an MP4 with MediaProjection"
```

---

### Task 9: Internal audio capture

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/AudioCapture.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/ScreenRecorder.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the active `MediaProjection`.
- Produces:
  - `class AudioCapture(projection, sink)` with `start()`, `stop()`.
  - `data class AudioEncoderConfig(val sampleRate: Int = 48_000, val channels: Int = 2, val bitrate: Int = 160_000, val mime: String = "audio/mp4a-latm")`.
  - `ScreenRecorder` writes video and audio tracks into the same MP4.

- [ ] **Step 1: Write the manual acceptance check**

Record 10 s of Chiaki-Up with game audio playing.
- `ffprobe` reports two streams: H.264 video and AAC audio.
- Playing the file produces game audio, not silence.
- If audio is silent, the app shows `Audio unavailable — recording video only.`

- [ ] **Step 2: Build to verify it fails**

Run: `./gradlew :app:assembleDebug`
Expected: FAIL until audio capture is wired into the recorder.

- [ ] **Step 3: Implement audio capture**

Implementation details:
- Request `RECORD_AUDIO` before recording.
- Build `AudioPlaybackCaptureConfiguration.Builder(projection)`
  `.addMatchingUsage(AudioAttributes.USAGE_GAME)`
  `.addMatchingUsage(AudioAttributes.USAGE_MEDIA)`.
- Create `AudioRecord` with 48 kHz, stereo, PCM 16-bit.
- Feed PCM to a `MediaCodec` AAC-LC encoder.
- Mux the AAC track with the video track.
- Track the first non-silent PCM frame; if none within 2 s, stop the audio track and emit the fallback warning.
- Do not request audio focus; do not play the captured audio locally.

- [ ] **Step 4: Verify on device**

Run:
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
```
Open Chiaki-Up, play game audio, tap `Test record 10 s`, wait, then:
```bash
adb exec-out run-as io.github.jlai403.pocketsimengineer tar -C files -czf - . > spike-files.tgz
tar -xzf spike-files.tgz
ffprobe -v error -show_entries stream=codec_type,codec_name,sample_rate,channels -of default=nw=1 test-*.mp4
```
Expected: `codec_type=video`, `codec_type=audio`, `codec_name=aac`, `sample_rate=48000`, `channels=2`.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: capture internal game audio into the replay MP4"
```

---

### Task 10: Recording controller, arm/auto-start, and notification

**Files:**
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/RecordingState.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/RecordingEvent.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/RecordingController.kt`
- Create: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/recording/RecordingNotification.kt`
- Create: `app/src/test/kotlin/io/github/jlai403/pocketsimengineer/app/recording/RecordingControllerTest.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/service/TelemetryService.kt`
- Modify: `app/src/main/kotlin/io/github/jlai403/pocketsimengineer/app/MainActivity.kt`

**Interfaces:**
- Consumes: `Recorder`, `Sample`, `Clock`.
- Produces:
  - `sealed interface RecordingState { Idle; Armed; Recording(lapCount, carCode); Stopping; Error(message) }`.
  - `sealed interface RecordingEvent { Arm; PermissionGranted; PermissionDenied; CarOnTrack(lapCount, carCode); OffTrack; NoPackets; Stop; CarChanged; RecorderError(message) }`.
  - `class RecordingController(clock: Clock, recorder: Recorder) { val state: StateFlow<RecordingState>; fun onEvent(event: RecordingEvent); fun tick() }`.
  - Notification channel `recording` with a Stop action.

- [ ] **Step 1: Write the failing controller tests**

Create `RecordingControllerTest.kt`:

```kotlin
package io.github.jlai403.pocketsimengineer.app.recording

import io.github.jlai403.pocketsimengineer.core.clock.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingControllerTest {
    private class FakeClock(var now: Long = 0) : Clock {
        override fun nanoTime() = now
    }

    private class FakeRecorder : Recorder {
        var started = false
        var stopped = false
        override val isRecording get() = started && !stopped
        override fun start() { started = true; stopped = false }
        override fun stop() { stopped = true }
    }

    @Test
    fun `arms and starts on car on track`() {
        val controller = RecordingController(FakeClock(), FakeRecorder())
        controller.onEvent(RecordingEvent.Arm)
        assertEquals(RecordingState.Armed, controller.state.value)
        controller.onEvent(RecordingEvent.PermissionGranted)
        assertEquals(RecordingState.Armed, controller.state.value)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        assertEquals(RecordingState.Recording(1, 3474), controller.state.value)
    }

    @Test
    fun `stops after five seconds off track`() {
        val clock = FakeClock()
        val recorder = FakeRecorder()
        val controller = RecordingController(clock, recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionGranted)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        controller.onEvent(RecordingEvent.OffTrack)

        clock.now = 4_999_999_999
        controller.tick()
        assertEquals(RecordingState.Recording(1, 3474), controller.state.value)
        assertFalse(recorder.stopped)

        clock.now = 5_000_000_000
        controller.tick()
        assertEquals(RecordingState.Idle, controller.state.value)
        assertTrue(recorder.stopped)
    }

    @Test
    fun `car change and stop end recording immediately`() {
        val recorder = FakeRecorder()
        val controller = RecordingController(FakeClock(), recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionGranted)
        controller.onEvent(RecordingEvent.CarOnTrack(1, 3474))
        controller.onEvent(RecordingEvent.CarChanged)
        assertEquals(RecordingState.Idle, controller.state.value)
        assertTrue(recorder.stopped)
    }

    @Test
    fun `permission denial never starts the recorder`() {
        val recorder = FakeRecorder()
        val controller = RecordingController(FakeClock(), recorder)
        controller.onEvent(RecordingEvent.Arm)
        controller.onEvent(RecordingEvent.PermissionDenied)
        assertEquals(RecordingState.Error("permission-denied"), controller.state.value)
        assertFalse(recorder.started)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*RecordingControllerTest*'`
Expected: FAIL because the controller does not exist.

- [ ] **Step 3: Implement the state machine and notification**

State machine:
- `Idle` -> `Arm` -> `Armed`.
- `Armed` -> `CarOnTrack` -> `Recording`, call `recorder.start()`.
- `Recording` -> `OffTrack`/`NoPackets` -> start a 5 s timer; `tick()` past the deadline -> `Stopping` -> `Idle`, call `recorder.stop()`.
- `Recording` -> `CarChanged`/`Stop` -> `Stopping` -> `Idle`.
- `Armed`/`Recording` -> `PermissionDenied`/`RecorderError` -> `Error`.
- Reset the off-track timer if `CarOnTrack` arrives again before the deadline.

Notification:
- Channel `recording`, low importance.
- Content title `Pocket Sim Engineer`.
- Text `Armed` or `Recording lap N`.
- `Stop` action sends an intent to the service.
- Tapping the notification opens the app.

Service integration:
- The foreground service owns the controller.
- Telemetry samples feed `CarOnTrack`/`OffTrack`/`NoPackets` based on flags and packet gaps.
- Car code change sends `CarChanged`.
- The notification Stop action sends `Stop`.

- [ ] **Step 4: Verify on device**

Run:
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
```
Tap `Arm`, grant capture, open Chiaki-Up, start a race. Expected:
- Notification changes from `Armed` to `Recording lap N`.
- Pulling down the shade and tapping `Stop` stops recording.
- Returning to the menu for 5 s stops recording and finalizes the MP4.
- Changing the car closes the session and starts a new one on the next car-on-track.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: arm and auto-start recording from telemetry"
```

---

### Task 11: End-to-end spike verification

**Files:**
- Modify: `README.md` with the verification commands and known limitations.
- No production code changes unless a verification fails.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: a verified spike artifact and a list of defects to fix before v0.1.

- [ ] **Step 1: Prepare the device**

Run:
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n io.github.jlai403.pocketsimengineer/.app.MainActivity
```
Expected: one device listed, app opens.

- [ ] **Step 2: Run the 2-lap synchronization test**

1. Enter the PS5 IP.
2. Tap `Arm` and grant screen capture, notifications, and audio recording.
3. Open Chiaki-Up and connect.
4. Drive two clean laps.
5. Return to the menu for 5 s, or tap notification `Stop`.
6. Pull the session:

```bash
adb exec-out run-as io.github.jlai403.pocketsimengineer tar -C files -czf - sessions > session.tgz
tar -xzf session.tgz
find sessions -maxdepth 3 -type f
```

- [ ] **Step 3: Verify telemetry**

```bash
jq . sessions/*/session.json
xxd -l 32 sessions/*/laps/*.gt7lap
```
Expected:
- `packets/s` averaged 55–60 in logcat.
- Two lap entries in `session.json`.
- Each `.gt7lap` starts with `47 37 4c 50` (`G7LP`), version 1, sample count > 3000.
- Plausible lap times for the track.

- [ ] **Step 4: Verify video and audio**

```bash
ffprobe -v error -show_entries stream=codec_type,codec_name,width,height,r_frame_rate,sample_rate,channels -of default=nw=1 sessions/*/laps/*.mp4
```
Expected:
- H.264 video, 1920×1080, ~60 fps.
- AAC audio, 48 kHz, 2 channels.
- Video is not black.
- Audio is game audio, not silence. If silent, confirm the fallback warning appeared.

- [ ] **Step 5: Verify sync and lifecycle**

- Open a lap in the app.
- Pause at the start/finish line; compare the playhead to the visible line.
- Adjust the sync nudge until the chart cursor and the line agree.
- Confirm the marker lands within a few frames.
- While recording, pull down the notification shade, lock and unlock the screen, and stop projection from the system UI. Confirm the app stops cleanly and does not crash.
- Run 10 minutes at 1080p60. Confirm no unacceptable thermal throttling; if it throttles, confirm the app drops to 720p60 and notifies.

- [ ] **Step 6: Record results and commit**

Add a `## Spike results` section to `README.md` with:
- Device/build.
- Packets/s and decode errors.
- Lap count and times.
- Video/audio probe output.
- Sync error in frames.
- Thermal result.
- Defects found.

```bash
git add README.md
git commit -m "docs: record spike verification results"
```

---

## Self-Review

- **Spec coverage:** The spike covers telemetry ingest/decrypt/parse, lap detection, session JSON, binary telemetry, screen recording, internal audio, arm/auto-start, notification Stop, and on-device verification. v0.1 items (library, playback, charts, map, CSV, F-Droid) are intentionally out of scope for this plan and remain in the spec.
- **Type consistency:** `Sample`, `LapBoundary`, `DecodeResult`, `Recorder`, `RecordingState`, and `RecordingEvent` names are consistent across tasks.
- **Review Focus tests:** Real packet verification is Task 11 Step 3; audio silence is Task 9 Step 4 and Task 11 Step 4; lap boundary accuracy is Task 11 Step 5; projection/lifecycle is Task 10 Step 4 and Task 11 Step 5; thermal load is Task 11 Step 5.
- **Proportion:** The plan is longer than the spec because the spike has many platform-specific verification steps, but each task has a single deliverable and a check.
