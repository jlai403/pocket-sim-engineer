package io.github.jlai403.pocketsimengineer.app.profile

import kotlinx.serialization.Serializable

/** The public GT identity for a driver. */
@Serializable
data class DriverProfile(
    val psnId: String,
    val nickname: String,
    val countryCode: String?,
)
