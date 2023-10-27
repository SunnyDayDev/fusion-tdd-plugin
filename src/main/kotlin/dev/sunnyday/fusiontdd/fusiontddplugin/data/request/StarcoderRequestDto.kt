package dev.sunnyday.fusiontdd.fusiontddplugin.data.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StarcoderRequestDto(
    @SerialName("parameters")
    val parameters: Parameters = Parameters(),
    @SerialName("options")
    val options: Options = Options(),
    @SerialName("inputs")
    val inputs: String,
) {

    @Serializable
    data class Parameters(
        @SerialName("max_new_tokens")
        val maxNewTokens: Int = 500,
        @SerialName("temperature")
        val temperature: Float = 0.5f,
        @SerialName("return_full_text")
        val returnFullText: Boolean = false,
        @SerialName("num_return_sequences")
        val numReturnSequences: Int = 3,
        @SerialName("do_sample")
        val doSample: Boolean = true,
    )

    @Serializable
    data class Options(
        @SerialName("use_cache")
        val useCache: Boolean = false,
        @SerialName("wait_for_model")
        val waitForModel: Boolean = false,
    )
}