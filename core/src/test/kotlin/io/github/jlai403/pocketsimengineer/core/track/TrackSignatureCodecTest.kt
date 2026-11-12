package io.github.jlai403.pocketsimengineer.core.track

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
                  "max_z": 100.0
                }
              ]
            }
        """.trimIndent()

        val signatures = TrackSignatureCodec.decode(json)
        assertEquals(1, signatures.size)
        assertEquals("a", signatures.single().officialId)
        assertEquals("A Circuit", signatures.single().name)
        assertEquals(400.0, signatures.single().lengthM, 0.01)
    }
}
