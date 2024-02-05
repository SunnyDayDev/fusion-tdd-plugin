package project

import org.junit.jupiter.api.Test
import project.Target

class WhenTest {

    @Test
    fun `test callerFun`() {
        When().execute(3)
    }

    @Test
    fun `test executeWithExpression`() {
        When().executeWithExpression(3)
    }
}