package project

import project.Owner
import project.TargetClass
import org.junit.jupiter.api.Test

class TargetClassTest {

    @Test
    fun `test targetFun` {
        TargetClass().targetFun(Owner(Owner.Local(3)))
    }
}