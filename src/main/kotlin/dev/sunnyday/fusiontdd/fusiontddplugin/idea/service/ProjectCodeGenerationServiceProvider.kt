package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.sunnyday.fusiontdd.fusiontddplugin.data.CodeGenerationServiceImpl
import dev.sunnyday.fusiontdd.fusiontddplugin.data.StarcoderApi
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.service.CodeGenerationService
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.SettingsStarcoderOptionsProvider
import dev.sunnyday.fusiontdd.fusiontddplugin.network.KtorClientFactory
import dev.sunnyday.fusiontdd.fusiontddplugin.network.auth.FusionTDDStarcoderAuthTokenProvider

@Service(Service.Level.PROJECT)
internal class ProjectCodeGenerationServiceProvider(
    private val project: Project,
) {

    private val instance by lazy {
        val settings = project.service<FusionTDDSettings>()

        val starcoderAuthProvider = FusionTDDStarcoderAuthTokenProvider(settings)
        val starcoderClient = KtorClientFactory.createClient(starcoderAuthProvider)
        val starcoderApi = StarcoderApi(starcoderClient) { requireNotNull(settings.starcoderModel) }
        val starcoderOptionsProvider = SettingsStarcoderOptionsProvider(settings)

        CodeGenerationServiceImpl(starcoderApi, starcoderOptionsProvider)
    }

    fun getCodeGenerationService(): CodeGenerationService {
        return instance
    }
}