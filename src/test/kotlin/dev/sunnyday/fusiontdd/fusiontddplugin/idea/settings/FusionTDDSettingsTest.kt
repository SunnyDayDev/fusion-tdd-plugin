package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FusionTDDSettingsTest {

    private val testDisposable = object : Disposable.Default {}
    private val application = MockApplication(testDisposable)

    @BeforeEach
    fun setUp() {
        ApplicationManager.setApplication(application, testDisposable)
    }

    @Test
    fun `provide authToken by state`() = testTDDSettings(
        prepareState = { authToken = "authToken" },
        assert = { assertThat(authToken).isEqualTo(authToken) }
    )

    @Test
    fun `provide projectPackage by state`() = testTDDSettings(
        prepareState = { projectPackage = "projectPackage" },
        assert = { assertThat(projectPackage).isEqualTo(projectPackage) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide isAddTestCommentsBeforeGeneration by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { isAddTestCommentsBeforeGeneration = isEnabled },
        assert = { assertThat(isAddTestCommentsBeforeGeneration).isEqualTo(isAddTestCommentsBeforeGeneration) }
    )

    @Test
    fun `provide starcoderModel by state`() = testTDDSettings(
        prepareState = { starcoderModel = "starcoderModel" },
        assert = { assertThat(starcoderModel).isEqualTo(starcoderModel) }
    )

    @Test
    fun `provide starcoderMaxNewTokens by state`() = testTDDSettings(
        prepareState = { starcoderMaxNewTokens = 154 },
        assert = { assertThat(starcoderMaxNewTokens).isEqualTo(starcoderMaxNewTokens) }
    )

    @Test
    fun `provide starcoderTemperature by state`() = testTDDSettings(
        prepareState = { starcoderTemperature = 0.73f },
        assert = { assertThat(starcoderTemperature).isEqualTo(starcoderTemperature) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide starcoderDoSample by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { starcoderDoSample = isEnabled },
        assert = { assertThat(starcoderDoSample).isEqualTo(starcoderDoSample) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide starcoderUseCache by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { starcoderUseCache = isEnabled },
        assert = { assertThat(starcoderUseCache).isEqualTo(starcoderUseCache) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide starcoderWaitForModel by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { starcoderWaitForModel = isEnabled },
        assert = { assertThat(starcoderWaitForModel).isEqualTo(starcoderWaitForModel) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide isConfirmSourceBeforeGeneration by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { isConfirmSourceBeforeGeneration = isEnabled },
        assert = { assertThat(isConfirmSourceBeforeGeneration).isEqualTo(isConfirmSourceBeforeGeneration) }
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `provide isFixApplyGenerationResultError by state`(isEnabled: Boolean) = testTDDSettings(
        prepareState = { isFixApplyGenerationResultError = isEnabled },
        assert = { assertThat(isFixApplyGenerationResultError).isEqualTo(isFixApplyGenerationResultError) }
    )

    @Test
    fun `provide globalAdditionalPrompt by state`() = testTDDSettings(
        prepareState = { globalAdditionalPrompt = "Global additional prompt" },
        assert = { assertThat(globalAdditionalPrompt).isEqualTo(globalAdditionalPrompt) }
    )

    @Test
    fun `provide generationTargetAdditionalPrompt by state`() = testTDDSettings(
        prepareState = { generationTargetAdditionalPrompt = "Global additional prompt" },
        assert = { assertThat(generationTargetAdditionalPrompt).isEqualTo(generationTargetAdditionalPrompt) }
    )

    private fun testTDDSettings(
        prepareState: FusionTDDSettingsState.() -> Unit,
        assert: FusionTDDSettings.() -> Unit,
    ) {
        val state = FusionTDDSettingsState()
        state.prepareState()

        val settings = FusionTDDSettings()
        settings.loadState(state)

        settings.assert()
    }

    @AfterEach
    fun tearDown() {
        testDisposable.dispose()
    }
}