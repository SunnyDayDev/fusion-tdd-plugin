package dev.sunnyday.fusiontdd.fusiontddplugin.domain.service

import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.CodeBlock
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.GenerateCodeBlockResult

interface CodeGenerationService {

    suspend fun generate(input: CodeBlock): GenerateCodeBlockResult
}