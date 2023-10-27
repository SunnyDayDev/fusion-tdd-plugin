/**
 * @see dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps.PrepareGenerationSourceCodePipelineStepTest
 */

package project

import org.junit.jupiter.api.Test
import project.TargetClass

class TestClass {

    @Test
    fun `test target fun`() {
        TargetClass().targetFun()
    }

    @Test
    fun `test something other`() {
        assertEquals(2, 2)
    }
}