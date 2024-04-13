package project

import org.junit.jupiter.api.Test
import project.Target

class TargetTest {

    @Test
    fun `test callerFun`() {
        Target().execute()
        Target().executeWithExpression()
    }

    @Test
    fun `test executeWithExpression`() {
        Target().executeWithExpression()
    }
}