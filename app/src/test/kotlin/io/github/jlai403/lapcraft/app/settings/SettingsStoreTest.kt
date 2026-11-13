package io.github.jlai403.lapcraft.app.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `saves a normalized address across instances`() {
        val store = SettingsStore(context)

        assertTrue(store.savePs5Ip(" 192.168.001.050 "))

        assertEquals("192.168.1.50", store.ps5Ip)
        assertEquals("192.168.1.50", SettingsStore(context).ps5Ip)
    }

    @Test
    fun `rejects an invalid address without overwriting the saved one`() {
        val store = SettingsStore(context)
        store.savePs5Ip("10.0.0.2")

        assertFalse(store.savePs5Ip("999.1.1.1"))
        assertEquals("10.0.0.2", store.ps5Ip)
    }

    @Test
    fun `developer tools are off until enabled and persist`() {
        val store = SettingsStore(context)
        assertFalse(store.developerTools)

        store.developerTools = true

        assertTrue(SettingsStore(context).developerTools)
    }

    @Test
    fun `new imports use frame keyframes by default and keep a saved choice`() {
        val store = SettingsStore(context)

        assertEquals(ReplayKeyframes.Frame, store.replayKeyframes)

        store.replayKeyframes = ReplayKeyframes.Sparse

        assertEquals(ReplayKeyframes.Sparse, SettingsStore(context).replayKeyframes)
    }
}
