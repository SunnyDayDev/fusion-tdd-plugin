package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SettingsStarcoderOptionsProviderTest {

    @Test
    fun `provide StarcoderOptions by settings`() {
        val settings = mockk<FusionTDDSettings> {
            every { starcoderMaxNewTokens } returns 77
            every { starcoderTemperature } returns 0.777f
            every { starcoderDoSample } returns false
            every { starcoderUseCache } returns true
            every { starcoderWaitForModel } returns false
        }
        val provider = SettingsStarcoderOptionsProvider(settings)

        val actualOptions = provider.getStarcoderOptions()

        assertThat(actualOptions).isEqualTo(
            StarcoderOptions(
                maxNewTokens = 77,
                temperature = 0.777f,
                doSample = false,
                useCache = true,
                waitForModel = false,
            )
        )
    }
}