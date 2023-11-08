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
        val usedClasses = mutableSetOf(testClass)

        val checkQueue = ArrayDeque(usedReferences)
        while (checkQueue.isNotEmpty()) {
            val usedElement = checkQueue.remove()

            val ownerClass = PsiTreeUtil.getParentOfType(usedElement, KtClass::class.java)
            ownerClass?.let { usedClass ->
                if (usedClass !== testClass && usedClass !== targetClass) {
                    if (usedClass.isScannable()) {
                        if (usedClass.isTopLevel()) {
                            usedClasses.add(usedClass)
                        }
                    } else {
                        usedReferences.add(usedClass)
                    }
                }
            }

            if (usedElement.isScannable()) {
                collectNestedDependencies(usedElement, usedReferences, usedClasses, checkQueue)
            }
        }

        usedClasses.add(targetClass)

        return FunctionTestDependencies(
            function = targetFunction,
            testClass = testClass,
            usedClasses = usedClasses.toList(),
            usedReferences = usedReferences.toList(),
        )
    }

    private fun collectNestedDependencies(
        usedElement: PsiElement,
        usedReferences: MutableCollection<PsiElement>,
        usedClasses: MutableCollection<KtClass>,
        checkQueue: Queue<PsiElement>,
    ) {
        when (usedElement) {
            is KtNamedFunction -> {
                usedElement.annotationEntries.forEach { annotationEntry ->
                    val userType = (annotationEntry.typeReference?.typeElement as? KtUserType) ?: return@forEach
                    val annotationClass =
                        userType.referenceExpression?.mainReference?.resolve() ?: return@forEach
                    usedReferences.add(annotationClass)
                }
            }
        }

        usedElement.accept(NestedDependenciesCollector(usedReferences, usedClasses, checkQueue))
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
                when (val reference = element.mainReference.resolve()) {
                    targetFunction -> {
                        if (!isTrackingFunctionUseTargetFunction && usedReferences.add(trackingFunction)) {
                            isTrackingFunctionUseTargetFunction = true
                            usedReferences.add(trackingFunction)
                        }
                    }

                    targetClass, null -> {
                        // no-op
                    }

                    is KtClass -> {
                        if (!(reference.isScannable() && reference.isTopLevel())) {
                            trackingTouchedElements.add(reference)
                        }
                    }

                    else -> {
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
                    targetClass -> Unit // no-op

                    is KtClass -> onClassReference(reference)
                    is KtConstructor<*> -> onConstructorReference(reference)
                    is KtProperty -> onDeclarationReference(reference)
                    is KtNamedFunction -> onDeclarationReference(reference)
                }
            }

            super.visitElement(element)
        }

        private fun onClassReference(reference: KtClass) {
            if (reference.isScannable()) {
                if (reference.isTopLevel()) {
                    usedClasses.add(reference)
                }
            } else {
                usedReferences.add(reference)
            }
        }

        private fun onConstructorReference(reference: KtConstructor<*>) {
            val constructedClass = reference.parent as? KtClass
            if (
                constructedClass != null &&
                constructedClass !== targetClass &&
                constructedClass.isScannable()
            ) {
                if (constructedClass.isTopLevel()) {
                    usedClasses.add(constructedClass)
                } else {
                    usedReferences.add(constructedClass)
                }
            }
        }

        private fun onDeclarationReference(reference: KtDeclaration) {
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

    private fun PsiElement?.isScannable(): Boolean {
        val fqName = this?.kotlinFqName?.toString().orEmpty()
        return fqName.startsWith(settings.projectPackage.orEmpty())
    }
}