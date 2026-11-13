package io.github.jlai403.lapcraft.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TelemetryServiceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `starting with a valid address reports running`() {
        val intent = Intent(context, TelemetryService::class.java)
            .setAction(TelemetryService.ACTION_START)
            .putExtra(TelemetryService.EXTRA_IP, "192.168.001.050")
        val controller = Robolectric.buildService(TelemetryService::class.java, intent).create()

        controller.startCommand(0, 0)

        assertTrue(TelemetryService.running.value)
        assertNull(TelemetryService.lastError.value)
        controller.destroy()
        assertFalse(TelemetryService.running.value)
    }

    @Test
    fun `an invalid address reports an error and does not run`() {
        val intent = Intent(context, TelemetryService::class.java)
            .setAction(TelemetryService.ACTION_START)
            .putExtra(TelemetryService.EXTRA_IP, "not-an-ip")
        val controller = Robolectric.buildService(TelemetryService::class.java, intent).create()

        controller.startCommand(0, 0)

        assertFalse(TelemetryService.running.value)
        assertTrue(TelemetryService.lastError.value.orEmpty().contains("valid PS5 IPv4"))
        controller.destroy()
    }
}
