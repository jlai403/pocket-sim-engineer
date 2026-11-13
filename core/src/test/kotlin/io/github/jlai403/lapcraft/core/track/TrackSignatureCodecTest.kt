package io.github.jlai403.lapcraft.core.track

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackSignatureCodecTest {
    @Test
    fun `decodes the bundled signature shape`() {
        val json = """
            {
              "signatures": [
                {
                  "official_id": "a",
                  "official_name": "A Circuit",
                  "length_m": 400.0,
                  "min_x": 0.0,
                  "max_x": 100.0,
                  "min_z": 0.0,
                  "max_z": 100.0,
                  "path": [[0.0, 0.0], [100.0, 0.0]],
                  "reverse": {"official_id": "b", "official_name": "A Circuit Reverse"}
                }
              ]
            }
        """.trimIndent()

        val signature = TrackSignatureCodec.decode(json).single()
        assertEquals("a", signature.officialId)
        assertEquals("A Circuit", signature.name)
        assertEquals(400.0, signature.lengthM, 0.01)
        assertEquals(listOf(TrackPoint(0.0, 0.0), TrackPoint(100.0, 0.0)), signature.path)
        assertEquals("A Circuit Reverse", signature.reverseName)
    }
}
