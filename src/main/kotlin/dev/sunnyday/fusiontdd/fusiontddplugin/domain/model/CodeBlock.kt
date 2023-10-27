package dev.sunnyday.fusiontdd.fusiontddplugin.domain.model

@JvmInline
value class CodeBlock(val rawText: String) {

    companion object {
        const val GENERATE_HERE_TAG = "-GENERATE_HERE-"
    }
}