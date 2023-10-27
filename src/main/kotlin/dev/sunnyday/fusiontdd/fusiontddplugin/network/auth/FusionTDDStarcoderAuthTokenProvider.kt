package dev.sunnyday.fusiontdd.fusiontddplugin.network.auth

import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings

internal class FusionTDDStarcoderAuthTokenProvider(
    private val settings: FusionTDDSettings,
) : AuthTokenProvider {

    override fun getAuthToken(): String {
        return settings.authToken.orEmpty()
    }
}