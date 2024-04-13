package dev.sunnyday.fusiontdd.fusiontddplugin.idea.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.runInEdtAndWait
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClass
import dev.sunnyday.fusiontdd.fusiontddplugin.test.getClassFunction
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

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    @Test
    fun `on dotted class, resolve last class`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = Parent.DotChild.Deeper()
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Parent.DotChild.Deeper"))
    }

    @Test
    fun `on function parameter, resolve instance type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = calculate(Child())
            fun calculate(arg: Parent = Child()): Any = arg
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Child"))
    }

    @Test
    fun `on function parameter constructed by primary, resolve instance type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = calculate(Values.Some(5))
            fun calculate(arg: Parent = Child()): Any = arg
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Values.Some"))
    }

    @Test
    fun `on function parameter constructed by secondary, resolve instance type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = calculate(Values.Some2(5))
            fun calculate(arg: Parent = Child()): Any = arg
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Values.Some2"))
    }

    @Test
    fun `on function named parameter, resolve instance type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = calculate(arg = Child())
            fun calculate(arg: Parent = Child()): Any = arg
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Child"))
    }

    @Test
    fun `on function default parameter, resolve it's expression return type`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "delegate",
            expressionBody = """
            val delegate: Any = calculate()
            fun calculate(arg: Parent = OtherChild()): Any = arg
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("OtherChild"))
    }

    @Test
    fun `on parameter with function scope, return parameter`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate",
            expressionBody = """
            fun calculate(arg: Parent = OtherChild()): Any {
                return arg
            }
            """.trimIndent()
        )
        val scopeFunction = fixture.getClassFunction("Expression.calculate")

        val resolvedExpression = resolver.resolveExpression(
            expression = expression,
            stopConditions = listOf(
                ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(scopeFunction),
            ),
        )

        assertThat(resolvedExpression).isEqualTo(scopeFunction.valueParameters[0])
    }

    @Test
    fun `on resolve class on parameter with function scope, return null`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate",
            expressionBody = """
            fun calculate(arg: Parent = OtherChild()): Any {
                return arg
            }
            """.trimIndent()
        )
        val scopeFunction = fixture.getClassFunction("Expression.calculate")

        val resolvedExpression = resolver.resolveClass(
            expression = expression,
            stopConditions = listOf(
                ExpressionResultInstanceClassResolver.StopCondition.FunctionParameter(scopeFunction),
            ),
        )

        assertThat(resolvedExpression).isNull()
    }

    @Test
    fun `on if expression, resolve with common super`() = runInEdtAndWait {
        val expression = prepareExpression(
            expression = "calculate()",
            expressionBody = """
            fun calculate(): Any = if (true) Child() else OtherChild()
            """.trimIndent()
        )

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

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

        val resolvedClass = resolver.resolveClass(expression)

        assertThat(resolvedClass).isEqualTo(fixture.getClass("Independent"))
    }

    // endregion

    // region Factories + Utils

    private fun prepareExpression(expression: String, expressionBody: String): KtExpression {
        fixture.addFileToProject(
            "Expression.kt",
            """
            abstract class Root
            
            open class Parent : Root() {
                open class DotChild : Parent() {
                    class Deeper : DotChild()
                }
            }
            class Child : Parent()
            class OtherChild : Parent()
            
            class Independent : Root()
            
            sealed class Values : Root() {
                data class Some(val value: Int) : Values
                class Some2(val value1: Int, val value2: Int) : Values {
                    constructor(value1: Int) : this(value1, value1)
                }
            }
            
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