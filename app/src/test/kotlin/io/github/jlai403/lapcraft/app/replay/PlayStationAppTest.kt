package io.github.jlai403.lapcraft.app.replay

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayStationAppTest {
    @Test
    fun `falls back to the store listing when the app is not installed`() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(PlayStationApp.open(context))

        val intent = shadowOf(context.applicationContext as Application).nextStartedActivity
        assertEquals(
            "market://details?id=com.scee.psxandroid",
            intent.data.toString(),
        )
    }
}
