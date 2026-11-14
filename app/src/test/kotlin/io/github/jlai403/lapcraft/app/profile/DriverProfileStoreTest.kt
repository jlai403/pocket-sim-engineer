package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile

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
    fun `saves and loads the owner`() {
        val store = DriverProfileStore(context)
        store.setOwner(DriverProfile("skino2024", "Skino", "gb"))

        assertEquals(DriverProfile("skino2024", "Skino", "GB"), store.owner())
    }

    @Test
    fun `keeps other drivers for the picker`() {
        val store = DriverProfileStore(context)
        store.setOwner(DriverProfile("skino2024", "Skino", "GB"))
        store.save(DriverProfile("friend", "Friend", "us"))

        assertEquals(
            listOf("Friend", "Skino"),
            store.profiles().map { it.nickname },
        )
        assertEquals("Friend", store.profile("FRIEND")?.nickname)
    }

    @Test
    fun `removing the owner clears the default and the cached profile`() {
        val store = DriverProfileStore(context)
        store.setOwner(DriverProfile("skino2024", "Skino", "GB"))
        store.remove("skino2024")

        assertNull(store.owner())
        assertNull(store.profile("skino2024"))
    }

    @Test
    fun `a blank nickname falls back to the psn id`() {
        val store = DriverProfileStore(context)
        store.setOwner(DriverProfile("skino2024", "  ", null))

        assertEquals("skino2024", store.owner()?.nickname)
        assertNull(store.owner()?.countryCode)
    }

    @Test
    fun `migrates the old single profile`() {
        context.getSharedPreferences("driver_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("psn_id", "skino2024")
            .putString("nickname", "Skino")
            .putString("country_code", "gb")
            .apply()

        val store = DriverProfileStore(context)

        assertEquals(DriverProfile("skino2024", "Skino", "GB"), store.owner())
        assertEquals(listOf("Skino"), store.profiles().map { it.nickname })
    }

    @Test
    fun `an owner without a cached profile falls back to the psn id`() {
        context.getSharedPreferences("driver_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("owner_psn", "skino2024")
            .apply()

        assertEquals(
            DriverProfile("skino2024", "skino2024", null),
            DriverProfileStore(context).owner(),
        )
    }
}
