package project

import org.junit.jupiter.api.Test
import project.Target

class TargetTest {

    @Test
    fun `test targetFun`() {
        Target().targetFun(Argument())
    }
}