package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import dev.sunnyday.fusiontdd.fusiontddplugin.data.StarcoderOptionsProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions

internal class SettingsStarcoderOptionsProvider(
    private val settings: FusionTDDSettings,
) : StarcoderOptionsProvider {

    override fun getStarcoderOptions(): StarcoderOptions {
        return StarcoderOptions(
            maxNewTokens = settings.starcoderMaxNewTokens,
            temperature = settings.starcoderTemperature,
            doSample = settings.starcoderDoSample,
            useCache = settings.starcoderUseCache,
            waitForModel = settings.starcoderWaitForModel,
        )
    }
}