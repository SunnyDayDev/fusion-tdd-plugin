package dev.sunnyday.fusiontdd.fusiontddplugin.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

@Target(AnnotationTarget.FUNCTION)
annotation class TestStep

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class ParentTests

class TestSteps : BeforeTestExecutionCallback {

    override fun beforeTestExecution(testContext: ExtensionContext) {
        val isTestParentTests = testContext.requiredTestMethod.isAnnotationPresent(ParentTests::class.java) ||
                testContext.requiredTestClass.isAnnotationPresent(ParentTests::class.java)

        testContext.requiredTestInstances.allInstances.forEach { testInstance ->
            val testInstanceClass = testInstance::class.java

            testInstanceClass.classInheritanceChain().forEach { superClass ->
                superClass.declaredMethods.forEach { method ->
                    if (method.isAnnotationPresent(TestStep::class.java)) {
                        method.invoke(testInstance)
                    }
                }
            }

            testInstanceClass.classInheritanceChain().forEach { superClass ->
                superClass.declaredMethods.forEach { method ->
                    if (isTestParentTests && method.isAnnotationPresent(Test::class.java)) {
                        method.invoke(testInstance)
                    }
                }
            }
        }
    }

    private fun Class<*>.classInheritanceChain(): Sequence<Class<*>> {
        return generateSequence(this) { klass ->
            klass.superclass.takeIf { it != Any::class.java }
        }
    }
}