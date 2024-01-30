package project

import project.TargetClass
import project.DataClassWithCompanionFactory
import project.Some
import org.junit.jupiter.api.Test

class TargetTest {

    private val target = TargetClass()

    @Test
    fun `collect object argument`() {
        target.onSome(Some.Object)
    }

    @Test
    fun `test createByCompanionFactory`() {
        val expected = DataClassWithCompanionFactory.createInt(3)
        val actual = target.createByCompanionFactory()
        assertEquals(expected, actual)
    }
}