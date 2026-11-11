package io.github.jlai403.pocketsimengineer.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateManifestTest {
    @Test
    fun `decodes the release manifest`() {
        val manifest = UpdateJsonCodec.decode(
            """
            {
              "versionCode": 1031,
              "versionName": "0.1.0",
              "apk": "PocketSimEngineer-debug.apk",
              "sha256": "abc123",
              "notes": "Build 31"
            }
            """.trimIndent(),
        )

        assertEquals(1031, manifest?.versionCode)
        assertEquals("0.1.0", manifest?.versionName)
        assertEquals("PocketSimEngineer-debug.apk", manifest?.apk)
        assertEquals("abc123", manifest?.sha256)
        assertEquals("Build 31", manifest?.notes)
    }

    @Test
    fun `ignores unknown fields and defaults optional ones`() {
        val manifest = UpdateJsonCodec.decode(
            """{"versionCode": 7, "versionName": "0.2.0", "channel": "debug"}""",
        )

        assertEquals(7, manifest?.versionCode)
        assertEquals("PocketSimEngineer-debug.apk", manifest?.apk)
        assertEquals("", manifest?.sha256)
    }

    @Test
    fun `rejects malformed manifests`() {
        assertNull(UpdateJsonCodec.decode("not json"))
        assertNull(UpdateJsonCodec.decode("""{"versionName": "0.1.0"}"""))
    }

    @Test
    fun `only a higher version code is an update`() {
        val manifest = UpdateManifest(versionCode = 12, versionName = "0.1.0")

        assertTrue(manifest.isNewerThan(11))
        assertFalse(manifest.isNewerThan(12))
        assertFalse(manifest.isNewerThan(13))
    }

    @Test
    fun `round trips`() {
        val manifest = UpdateManifest(versionCode = 42, versionName = "1.0.0", notes = "notes")
        assertEquals(manifest, UpdateJsonCodec.decode(UpdateJsonCodec.encode(manifest)))
    }
}
