package io.github.jlai403.lapcraft.core.profile

import kotlinx.serialization.Serializable

/** The public GT identity for a driver. */
@Serializable
data class DriverProfile(
    val psnId: String,
    val nickname: String,
    val countryCode: String?,
)
