package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

internal data class StarcoderOptions(
    val maxNewTokens: Int = 500,
    val temperature: Float = 0.5f,
    val doSample: Boolean = true,
    val useCache: Boolean = false,
    val waitForModel: Boolean = true,
)