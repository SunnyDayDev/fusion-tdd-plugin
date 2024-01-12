package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassProperty
import org.jetbrains.kotlin.psi.KtExpression
import org.junit.jupiter.api.Test

class ExpressionResultInstanceClassResolverTest : LightJavaCodeInsightFixtureTestCase5() {

    // region Definitions + Setup

    private val resolver = ExpressionResultInstanceClassResolver()

    override fun getTestDataPath(): String = "testdata"

    // endregion

    // region Specifications (Tests)

    @Test
    fun `on property reference, resolve with referenced property instance`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegated",
            expressionBody = """
            val delegated = Independent()
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on delegated property reference, resolve with referenced property instance (get() = )`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegated",
            expressionBody = """
            val real = Independent()
            val delegated: Any
                get() = real
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on delegated property reference, resolve with referenced property instance (get() {})`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegated",
            expressionBody = """
            val real = Independent()
            val delegated: Any
                get() { return real }
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on function reference, resolve with returned instance (block body)`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any {
                return Independent()
            }
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on function reference, resolve with returned instance (expression body)`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any = Independent()
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on if expression, resolve with common super`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any = if (true) Child() else OtherChild()
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Parent"))
    }

    @Test
    fun `on when expression, resolve with common super`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any = when ("value") {
                "parent" -> Parent()
                "child" -> Child()
                else -> Independent()
            }
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Root"))
    }

    @Test
    fun `on when expression with single statement, use it type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any = when ("value") {
                else -> Independent()
            }
            """.trimIndent()
        )

        val resolvedClass = resolver.resolve(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    // endregion

    // region Factories + Utils

    private fun prepareExpression(expression: String, expressionBody: String): KtExpression {
        fixture.addFileToProject(
            "Expression.kt",
            """
            abstract class Root
            
            open class Parent : Root()
            class Child : Parent
            class OtherChild : Parent
            
            class Independent : Root()
            
            class Expression {
            
            $expressionBody
            
            val expression = $expression
            }
            """.trimIndent()
        )

        return requireNotNull(fixture.getClassProperty("Expression.expression").initializer)
    }

    // endregion
}