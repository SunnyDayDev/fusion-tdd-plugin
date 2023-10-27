package dev.sunnyday.fusiontdd.fusiontddplugin.data

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.StarcoderOptions

internal fun interface StarcoderOptionsProvider {

    fun getStarcoderOptions(): StarcoderOptions
}