package dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.registerServiceInstance
import dev.sunnyday.fusiontdd.fusiontddplugin.test.requireChildByName
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JSlider
import javax.swing.JTextField

class FusionTTDSettingsConfigurableProviderTest : LightJavaCodeInsightFixtureTestCase5() {

    private val settings = mockk<FusionTDDSettings>(relaxed = true)

    override fun getTestDataPath(): String = "testdata"

    @BeforeEach
    fun setUp() {
        fixture.project.registerServiceInstance(FusionTDDSettings::class.java, settings)
    }

    @Test
    fun `'starcoder auth token' option is present on settings`() {
        every { settings.authToken } returns "abx34"
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JTextField>(settings::authToken.name)

        assertThat(checkBox.text).isEqualTo("abx34")

        checkBox.text = "xxfg54"
        configurableSettings.apply()

        verify { settings.authToken = "xxfg54" }
    }

    @Test
    fun `'project package' option is present on settings`() {
        every { settings.projectPackage } returns "initial"
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JTextField>(settings::projectPackage.name)

        assertThat(checkBox.text).isEqualTo("initial")

        checkBox.text = "changed"
        configurableSettings.apply()

        verify { settings.projectPackage = "changed" }
    }

    @Test
    fun `'starcoder model' option is present on settings`() {
        every { settings.starcoderModel } returns "some"
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JTextField>(settings::starcoderModel.name)

        assertThat(checkBox.text).isEqualTo("some")

        checkBox.text = "super"
        configurableSettings.apply()

        verify { settings.starcoderModel = "super" }
    }

    @Test
    fun `'max new tokens' option is present on settings`() {
        every { settings.starcoderMaxNewTokens } returns 100
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JSlider>(settings::starcoderMaxNewTokens.name)

        assertThat(checkBox.value).isEqualTo(100)

        checkBox.value = 250
        configurableSettings.apply()

        verify { settings.starcoderMaxNewTokens = 250 }
    }

    @Test
    fun `'temperature' option is present on settings`() {
        every { settings.starcoderTemperature } returns 3f
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JTextField>(settings::starcoderTemperature.name)

        assertThat(checkBox.text).isEqualTo("3")

        checkBox.text = "2"
        configurableSettings.apply()

        verify { settings.starcoderTemperature = 2f }
    }

    @Test
    fun `'do sample' option is present on settings`() {
        every { settings.starcoderDoSample } returns true
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JCheckBox>(settings::starcoderDoSample.name)

        assertThat(checkBox.isSelected).isTrue()

        checkBox.isSelected = false
        configurableSettings.apply()

        verify { settings.starcoderDoSample = false }
    }

    @Test
    fun `'use cache' option is present on settings`() {
        every { settings.starcoderUseCache } returns true
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JCheckBox>(settings::starcoderUseCache.name)

        assertThat(checkBox.isSelected).isTrue()

        checkBox.isSelected = false
        configurableSettings.apply()

        verify { settings.starcoderUseCache = false }
    }

    @Test
    fun `'add tests comment' option is present on settings`() {
        every { settings.isAddTestCommentsBeforeGeneration } returns true
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JCheckBox>(settings::isAddTestCommentsBeforeGeneration.name)

        assertThat(checkBox.isSelected).isTrue()

        checkBox.isSelected = false
        configurableSettings.apply()

        verify { settings.isAddTestCommentsBeforeGeneration = false }
    }

    @Test
    fun `'generation source' option is present on settings`() {
        every { settings.isConfirmSourceBeforeGeneration } returns true
        val configurableSettings = FusionTTDSettingsConfigurableProvider(fixture.project).createConfigurable()
        val checkBox = requireNotNull(configurableSettings.createComponent())
            .requireChildByName<JCheckBox>(settings::isConfirmSourceBeforeGeneration.name)

        assertThat(checkBox.isSelected).isTrue()

        checkBox.isSelected = false
        configurableSettings.apply()

        verify { settings.isConfirmSourceBeforeGeneration = false }
    }
}