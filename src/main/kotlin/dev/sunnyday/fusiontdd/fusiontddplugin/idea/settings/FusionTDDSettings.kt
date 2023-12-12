package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import com.intellij.openapi.components.*


@Service(Service.Level.PROJECT)
@State(name = "FusionTDDSettingsState", storages = [Storage("dev.sunnyday.fusiontdd.settings.xml")])
internal class FusionTDDSettings : SimplePersistentStateComponent<FusionTDDSettingsState>(FusionTDDSettingsState()) {

    var authToken by state::authToken

    var projectPackage by state::projectPackage

    var isAddTestCommentsBeforeGeneration by state::isAddTestCommentsBeforeGeneration

    var starcoderModel by state::starcoderModel

    var starcoderMaxNewTokens by state::starcoderMaxNewTokens

    var starcoderTemperature by state::starcoderTemperature

    var starcoderDoSample by state::starcoderDoSample

    var starcoderUseCache by state::starcoderUseCache

    var starcoderWaitForModel by state::starcoderWaitForModel

    var isConfirmSourceBeforeGeneration by state::isConfirmSourceBeforeGeneration

    var isFixApplyGenerationResultError by state::isFixApplyGenerationResultError
}

internal class FusionTDDSettingsState : BaseState() {

    var authToken by string("")

    var projectPackage by string("")

    var isAddTestCommentsBeforeGeneration by property(false)

    var starcoderModel by string("starcoder")

    var starcoderMaxNewTokens by property(500)

    var starcoderTemperature by property(0.5f)

    var starcoderDoSample by property(true)

    var starcoderUseCache by property(false)

    var starcoderWaitForModel by property(true)

    var isConfirmSourceBeforeGeneration by property(false)

    var isFixApplyGenerationResultError by property(true)
}