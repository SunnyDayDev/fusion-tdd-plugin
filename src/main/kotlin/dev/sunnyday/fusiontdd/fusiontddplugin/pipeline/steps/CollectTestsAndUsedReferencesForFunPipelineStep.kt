package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.steps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import dev.sunnyday.fusiontdd.fusiontddplugin.domain.model.FunctionTestDependencies
import dev.sunnyday.fusiontdd.fusiontddplugin.idea.settings.FusionTDDSettings
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import java.util.*

internal class CollectTestsAndUsedReferencesForFunPipelineStep(
    private val targetFunction: KtNamedFunction,
    private val targetClass: KtClass,
    private val testClass: KtClass,
    private val settings: FusionTDDSettings,
) : PipelineStep<Nothing?, FunctionTestDependencies> {

    private val logger: Logger = thisLogger()

    override fun execute(input: Nothing?, observer: (Result<FunctionTestDependencies>) -> Unit) {
        logger.debug("Pipeline: Collect tests and references for '${targetFunction.name}'")

        val result = runCatching {
            val usedReferencesCollector = mutableSetOf<PsiElement>(targetFunction)

            testClass.accept(FunctionUsageCollector(usedReferencesCollector))
            collectDependencies(usedReferencesCollector)
        }

        observer.invoke(result)
    }

    private fun collectDependencies(
        usedReferences: MutableCollection<PsiElement>,
    ): FunctionTestDependencies {
        val classes = mutableSetOf(testClass)

        val queue = ArrayDeque(usedReferences)
        while (queue.isNotEmpty()) {
            val usedElement = queue.remove()

            val ownerClass = PsiTreeUtil.getParentOfType(usedElement, KtClass::class.java)

            ownerClass?.let { usedClass ->
                if (usedClass !== testClass && usedClass !== targetClass) {
                    classes.add(usedClass)
                }
            }

            usedElement.accept(NestedDependenciesCollector(usedReferences, classes, queue))
        }

        classes.add(targetClass)

        return FunctionTestDependencies(
            function = targetFunction,
            testClass = testClass,
            usedClasses = classes.toList(),
            usedReferences = usedReferences.toList(),
        )
    }

    inner class FunctionUsageCollector(
        private val usedReferences: MutableCollection<PsiElement>,
    ) : PsiRecursiveElementWalkingVisitor() {

        private var trackingFunction: KtNamedFunction? = null
        private var isTrackingFunctionUseTargetFunction = false
        private val trackingTouchedElements = mutableSetOf<PsiElement>()

        override fun visitElement(element: PsiElement) {
            when {
                element is KtNamedFunction -> {
                    trackingFunction = element
                }
            }

            val trackingFunction = trackingFunction ?: return super.visitElement(element)

            if (element is KtNameReferenceExpression) {
                val reference = element.mainReference.resolve()

                when (reference) {
                    targetFunction -> {
                        if (!isTrackingFunctionUseTargetFunction && usedReferences.add(trackingFunction)) {
                            isTrackingFunctionUseTargetFunction = true
                            usedReferences.add(trackingFunction)
                        }
                    }

                    is KtProperty, is KtNamedFunction -> {
                        trackingTouchedElements.add(reference)
                    }
                }
            }

            super.visitElement(element)
        }

        override fun elementFinished(element: PsiElement) {
            super.elementFinished(element)

            if (element === trackingFunction) {
                if (isTrackingFunctionUseTargetFunction) {
                    usedReferences.addAll(trackingTouchedElements)
                }

                isTrackingFunctionUseTargetFunction = false
                trackingTouchedElements.clear()

                trackingFunction = null
            }
        }
    }

    private inner class NestedDependenciesCollector(
        private val usedReferences: MutableCollection<PsiElement>,
        private val usedClasses: MutableCollection<KtClass>,
        private val checkQueue: Queue<PsiElement>,
    ) : PsiRecursiveElementWalkingVisitor() {

        override fun visitElement(element: PsiElement) {
            if (element is KtNameReferenceExpression) {
                when (val reference = element.mainReference.resolve()) {
                    is KtClass -> {
                        if (reference.isScannable()) {
                            usedClasses.add(reference)
                        }
                    }

                    is KtConstructor<*> -> {
                        val constructedClass = reference.parent as? KtClass
                        if (constructedClass != null && constructedClass.isScannable()) {
                            if (constructedClass.isTopLevel()) {
                                usedClasses.add(constructedClass)
                            } else {
                                usedReferences.add(constructedClass)
                            }
                        }
                    }

                    is KtProperty, is KtNamedFunction -> {
                        if (
                            (reference.context is KtClassBody || reference.context is KtFile) &&
                            usedReferences.add(reference)
                        ) {
                            if (reference.isScannable()) {
                                checkQueue.add(reference)
                            }
                        }
                    }
                }
            }

            super.visitElement(element)
        }

        private fun PsiElement?.isScannable(): Boolean {
            val fqName = this?.kotlinFqName?.toString().orEmpty()
            return fqName.startsWith(settings.projectPackage.orEmpty())
        }
    }
}