package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import dev.sunnyday.fusiontdd.fusiontddplugin.data.CodeGenerationServiceImpl
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.properties.Delegates

internal class ProjectCodeGenerationServiceProviderTest : LightJavaCodeInsightFixtureTestCase5() {

    override fun getTestDataPath(): String = "testdata"

    private var provider: ProjectCodeGenerationServiceProvider by Delegates.notNull()

    @BeforeEach
    fun setUp() {
        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, mockk())

        provider = ProjectCodeGenerationServiceProvider(fixture.project)
    }

    @Test
    fun `inject CodeGenerationService`() {
        val provider = ProjectCodeGenerationServiceProvider(fixture.project)

        val actualCodeGenerationService = provider.getCodeGenerationService()

        assertThat(actualCodeGenerationService).isInstanceOf(CodeGenerationServiceImpl::class.java)
    }

    @Test
    fun `multiple call returns same instance`() {
        val firstCallInstance = provider.getCodeGenerationService()
        val nextCallInstance = provider.getCodeGenerationService()

        assertThat(firstCallInstance).isSameInstanceAs(nextCallInstance)
    }
}