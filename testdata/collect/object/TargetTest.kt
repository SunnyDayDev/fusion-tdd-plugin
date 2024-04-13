package project

import project.TargetClass
import project.DataClassWithCompanionFactory
import project.ImportedObjectCompanion.Companion.importedCall
import project.ImportedObjectCompanion.Nested.Companion.nestedImportedCall
import project.ImportedObjectCompanion.Companion.CONST
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

    @Test
    fun `test importedCall`() {
        importedCall()
        target.importedCall()
    }

    @Test
    fun `test importedCall`() {
        nestedImportedCall()
        target.nestedImportedCall()
    }

    @Test
    fun `test importedConst`() {
        target.importedConst(CONST)
    }
}