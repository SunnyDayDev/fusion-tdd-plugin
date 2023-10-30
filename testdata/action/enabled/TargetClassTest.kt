package project

import org.junit.jupiter.api.Test
import project.TargetClass

class TargetClassTest {

    val target = TargetClass()

    @Test
    fun testTarget() {
        target.targetFunction()
    }
}