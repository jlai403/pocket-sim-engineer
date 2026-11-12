package io.github.jlai403.pocketsimengineer.app.profile

import android.content.Context

/** Stores the one local driver profile; there is no account or server copy. */
class DriverProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DriverProfile? {
        val psnId = prefs.getString(KEY_PSN, null)?.trim().orEmpty()
        if (psnId.isEmpty()) return null
        val nickname = prefs.getString(KEY_NICKNAME, null)?.trim().orEmpty().ifEmpty { psnId }
        return DriverProfile(psnId, nickname, prefs.getString(KEY_COUNTRY, null))
    }

    fun save(profile: DriverProfile) {
        val psnId = profile.psnId.trim()
        prefs.edit()
            .putString(KEY_PSN, psnId)
            .putString(KEY_NICKNAME, profile.nickname.trim().ifEmpty { psnId })
            .putString(KEY_COUNTRY, profile.countryCode?.trim()?.uppercase())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "driver_profile"
        const val KEY_PSN = "psn_id"
        const val KEY_NICKNAME = "nickname"
        const val KEY_COUNTRY = "country_code"
    }
}
