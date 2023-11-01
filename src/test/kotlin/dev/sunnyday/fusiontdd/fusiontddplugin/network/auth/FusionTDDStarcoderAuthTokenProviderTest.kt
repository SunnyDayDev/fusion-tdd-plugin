package dev.sunnyday.fusiontdd.fusiontddplugin.network.auth

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class FusionTDDStarcoderAuthTokenProviderTest {

    @Test
    fun `on getAuthToken, provide it from settings`() {
        val settings = mockk<FusionTDDSettings> {
            every { authToken } returns "x-auth-token"
        }
        val provider = FusionTDDStarcoderAuthTokenProvider(settings)

        val actualToken = provider.getAuthToken()

        assertThat(actualToken).isEqualTo("x-auth-token")
    }
}