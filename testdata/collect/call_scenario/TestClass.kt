package project

import lib.usedFromTest
import lib.unused
import org.junit.jupiter.api.Test
import project.TargetClass
import project.UsedClass
import project.UnusedClass

class TestClass {

    val target = TargetClass()
    val usedClass = UsedClass(target)

    // Property that not referenced in tests with target function should be ignored
    val unusedClass = UnusedClass(target)

    @Test
    fun testTarget() {
        usedFromTest()
        usedClass.call()
        target.targetFunction()
    }

    @Test
    fun testTarget2() {
        target.targetFunction()
    }

    @Test
    fun testOther() {
        // Test without usage of target function must be ignored
        unusedClass.call()
        unused()
    }
}