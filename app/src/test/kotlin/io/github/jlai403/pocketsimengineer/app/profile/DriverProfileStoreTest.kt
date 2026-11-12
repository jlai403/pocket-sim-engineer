package io.github.jlai403.pocketsimengineer.app.profile

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DriverProfileStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        DriverProfileStore(context).clear()
    }

    @Test
    fun `saves and loads the profile`() {
        val store = DriverProfileStore(context)
        store.save(DriverProfile("skino2024", "Skino", "gb"))

        assertEquals(DriverProfile("skino2024", "Skino", "GB"), store.load())
    }

    @Test
    fun `a blank nickname falls back to the psn id`() {
        val store = DriverProfileStore(context)
        store.save(DriverProfile("skino2024", "  ", null))

        assertEquals("skino2024", store.load()?.nickname)
        assertNull(store.load()?.countryCode)
    }

    @Test
    fun `remove clears the stored profile`() {
        val store = DriverProfileStore(context)
        store.save(DriverProfile("skino2024", "Skino", "GB"))
        store.clear()

        assertNull(store.load())
    }
}
