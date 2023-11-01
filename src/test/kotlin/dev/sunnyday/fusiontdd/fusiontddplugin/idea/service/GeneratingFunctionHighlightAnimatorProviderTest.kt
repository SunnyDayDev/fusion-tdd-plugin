package dev.sunnyday.fusiontdd.fusiontddplugin.idea.service

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor.GeneratingFunctionHighlightAnimator
import org.junit.jupiter.api.Test

class GeneratingFunctionHighlightAnimatorProviderTest {

    @Test
    fun `on get GeneratingFunctionHighlightAnimator, just provide it`() {
        val provider = GeneratingFunctionHighlightAnimatorProvider()
        val actualAnimator = provider.getGeneratingFunctionHighlightAnimator()
        assertThat(actualAnimator).isInstanceOf(GeneratingFunctionHighlightAnimator::class.java)
    }
}