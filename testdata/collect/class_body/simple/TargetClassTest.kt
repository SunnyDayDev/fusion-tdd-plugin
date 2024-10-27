package project

import org.junit.jupiter.api.Test

class TargetClassTest {

    private val targetClass = TargetClass()

    // expected to be collected to generation context
    @Test
    fun testFun1() {
        targetClass.fun1()
    }

    // expected to be collected to generation context
    @Test
    fun testFun2() {
        targetClass.fun2()
    }
}