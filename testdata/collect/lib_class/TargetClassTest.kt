package project

import lib.LibClass
import project.TargetClass
import org.junit.jupiter.api.Test

class TargetClassTest {

    @Test
    fun `test targetFun`() {
        TargetClass().targetFun(LibClass())
    }
}