package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import java.text.DecimalFormat

internal class FusionTTDSettingsConfigurableProvider(
    private val project: Project,
) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable {
        return FusionTTDSettingsConfigurable(project)
    }
}

internal class FusionTTDSettingsConfigurable(
    project: Project,
) : BoundConfigurable("Fusion TDD") {

    private val settings = project.service<FusionTDDSettings>()

    override fun createPanel(): DialogPanel {
        return panel {
            group("Project Config") {
                row {
                    passwordField()
                        .applyToComponent { name = settings::authToken.name }
                        .label("Starcoder auth token")
                        .align(Align.FILL)
                        .bindText(settings::authToken.orEmpty(), settings::authToken::set)
                }

                row {
                    textField()
                        .applyToComponent { name = settings::projectPackage.name }
                        .label("Project package")
                        .align(Align.FILL)
                        .bindText(settings::projectPackage.orEmpty(), settings::projectPackage::set)
                }
            }

            group("Starcoder Config") {
                row {
                    textField()
                        .applyToComponent { name = settings::starcoderModel.name }
                        .label("Model")
                        .align(Align.FILL)
                        .bindText(settings::starcoderModel.orEmpty(), settings::starcoderModel::set)
                }

                row {
                    slider(0, 500, 50, 100)
                        .applyToComponent { name = settings::starcoderMaxNewTokens.name }
                        .label("Max new tokens")
                        .showValueHint()
                        .comment(
                            comment = "Int. The amount of new tokens to be generated, " +
                                    "this does not include the input length it is a estimate of the size " +
                                    "of generated text you want. Each new tokens slows down the request, " +
                                    "so look for balance between response times and length of text generated.",
                        )
                        .align(Align.FILL)
                        .bindValue(settings::starcoderMaxNewTokens)
                }

                row {
                    val temperatureFormat = DecimalFormat("#.##")
                    textField()
                        .applyToComponent { name = settings::starcoderTemperature.name }
                        .label("Temperature")
                        .validationOnInput { field ->
                            val parseResult = runCatching { temperatureFormat.parse(field.text) }
                            when {
                                parseResult.isFailure -> error("Must be a float")
                                parseResult.getOrThrow().toFloat() < 0f -> error("Must be greater or equal to 0")
                                parseResult.getOrThrow().toFloat() > 100f -> error("Must be less or equal to 100")
                                else -> null
                            }
                        }
                        .comment(
                            comment = "Float. The temperature of the sampling operation. " +
                                    "1 means regular sampling, 0 means always take the highest score, " +
                                    "100.0 is getting closer to uniform probability.",
                        )
                        .align(Align.FILL)
                        .bindText(
                            getter = { temperatureFormat.format(settings.starcoderTemperature) },
                            setter = { settings.starcoderTemperature = temperatureFormat.parse(it).toFloat() }
                        )
                }

                row {
                    checkBox("Do sample")
                        .applyToComponent { name = settings::starcoderDoSample.name }
                        .comment(
                            comment = "Whether or not to use sampling, use greedy decoding otherwise.",
                        )
                        .bindSelected(settings::starcoderDoSample)
                }

                row {
                    checkBox("Use cache")
                        .applyToComponent { name = settings::starcoderUseCache.name }
                        .comment(
                            comment = "There is a cache layer on the inference API " +
                                    "to speedup requests we have already seen. " +
                                    "Most models can use those results as is as models are deterministic " +
                                    "(meaning the results will be the same anyway). " +
                                    "However if you use a non deterministic model, " +
                                    "you can set this parameter to prevent the caching mechanism " +
                                    "from being used resulting in a real new query.",
                        )
                        .bindSelected(settings::starcoderUseCache)
                }

                row {
                    checkBox("Wait for model")
                        .applyToComponent { name = settings::starcoderWaitForModel.name }
                        .comment(
                            comment = "If the model is not ready, wait for it instead of receiving 503. " +
                                    "It limits the number of requests required to get your inference done. " +
                                    "It is advised to only set this flag to true after receiving a 503 error " +
                                    "as it will limit hanging in your application to known places.",
                        )
                        .bindSelected(settings::starcoderWaitForModel)
                }
            }

            group("Prompts") {
                row {
                    checkBox("Add tests comments before generation")
                        .applyToComponent { name = settings::isAddTestCommentsBeforeGeneration.name }
                        .comment(
                            comment = "If enabled, all used tests names will be collected " +
                                    "and places as a comment before the generating function.",
                        )
                        .bindSelected(settings::isAddTestCommentsBeforeGeneration)
                }

                row {
                    textArea()
                        .applyToComponent { name = settings::globalAdditionalPrompt.name }
                        .label("Global additional prompt")
                        .rows(3)
                        .align(Align.FILL)
                        .comment(
                            comment = "This prompt will be added globally to the generation request.",
                        )
                        .bindText(settings::globalAdditionalPrompt.orEmpty(), settings::globalAdditionalPrompt::set)
                }

                row {
                    textArea()
                        .applyToComponent { name = settings::generationTargetAdditionalPrompt.name }
                        .label("Generation target additional prompt")
                        .rows(3)
                        .align(Align.FILL)
                        .comment(
                            comment = "This prompt will as an additional comment to the generation target.",
                        )
                        .bindText(settings::generationTargetAdditionalPrompt.orEmpty(), settings::generationTargetAdditionalPrompt::set)
                }
            }

            group("Developer Settings") {
                row {
                    checkBox("Confirm generation source before request")
                        .applyToComponent { name = settings::isConfirmSourceBeforeGeneration.name }
                        .comment(
                            comment = "If enabled, the source will be displayed for viewing and editing " +
                                    "before sending a request to generate the result.",
                        )
                        .bindSelected(settings::isConfirmSourceBeforeGeneration)
                }

                row {
                    checkBox("Handle apply generated suggestion error")
                        .applyToComponent { name = settings::isFixApplyGenerationResultError.name }
                        .comment(
                            comment = "If enabled, the dialog with failed suggestion will be shown " +
                                    "to allow fix/modify the suggestion and retry.",
                        )
                        .bindSelected(settings::isFixApplyGenerationResultError)
                }
            }
        }
    }

    private fun (() -> String?).orEmpty(): () -> String {
        return { invoke().orEmpty() }
    }
}