package dev.sunnyday.fusiontdd.fusiontddplugin.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StarcoderResultDto(
    @SerialName("generated_text")
    val generatedText: String? = null,
    @SerialName("error")
    val error: String? = null,
)