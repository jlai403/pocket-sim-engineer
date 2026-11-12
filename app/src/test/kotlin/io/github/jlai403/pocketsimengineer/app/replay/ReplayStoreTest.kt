package io.github.jlai403.pocketsimengineer.app.replay

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplayStoreTest {
    @Test
    fun `source file lives beside the lap clip`() {
        val dir = File("/tmp/session")
        assertTrue(ReplayStore.sourceFile(dir, 7).path.endsWith("laps/lap-007-source.mp4"))
    }

    @Test
    fun `copy returns false and leaves no file when the uri cannot be opened`() {
        val root = Files.createTempDirectory("replay").toFile()
        val destination = File(root, "lap-source.mp4")

        assertFalse(
            ReplayStore.copy(
                RuntimeEnvironment.getApplication(),
                Uri.parse("content://missing/1"),
                destination,
            ),
        )
        assertFalse(destination.exists())
    }
}
