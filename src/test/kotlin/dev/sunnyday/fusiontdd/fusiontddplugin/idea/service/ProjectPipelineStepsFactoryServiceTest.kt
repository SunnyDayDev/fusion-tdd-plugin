package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.*
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

class ProjectPipelineStepsFactoryServiceTest : LightJavaCodeInsightFixtureTestCase5() {

    override fun getTestDataPath(): String = "testdata"

    private var provider: ProjectPipelineStepsFactoryService by Delegates.notNull()

    @BeforeEach
    fun setUp() {
        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, mockk())

        provider = ProjectPipelineStepsFactoryService(fixture.project)
    }

    @Test
    fun `create step for collectTestsAndUsedReferencesForFun`() {
        val actualStep = provider.collectTestsAndUsedReferencesForFun(mockk(), mockk())

        assertThat(actualStep).isInstanceOf(CollectFunctionGenerationContextPipelineStep::class.java)
    }

    @Test
    fun `create step for prepareGenerationSourceCode`() {
        val actualStep = provider.prepareGenerationSourceCode()

        assertThat(actualStep).isInstanceOf(PrepareGenerationSourceCodePipelineStep::class.java)
    }

    @Test
    fun `create step for confirmGenerationSource`() {
        val actualStep = provider.confirmGenerationSource()

        assertThat(actualStep).isInstanceOf(ConfirmGenerationSourcePipelineStep::class.java)
    }

    @Test
    fun `create step for generateCodeSuggestion`() {
        fixture.project.registerServiceInstance(ProjectCodeGenerationServiceProvider::class.java, mockk(relaxed = true))

        val actualStep = provider.generateCodeSuggestion()

        assertThat(actualStep).isInstanceOf(GenerateCodeSuggestionsPipelineStep::class.java)
    }

    @Test
    fun `create step for replaceFunctionBody`() {
        val actualStep = provider.replaceFunctionBody(mockk())

        assertThat(actualStep).isInstanceOf(ReplaceFunctionBodyPipelineStep::class.java)
    }

    @Test
    fun `create step for fixGenerationResult`() {
        val actualStep = provider.fixGenerationResult()

        assertThat(actualStep).isInstanceOf(FixGenerationResultPipelineStep::class.java)
    }
}