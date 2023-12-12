package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

import com.intellij.openapi.Disposable
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException
import java.util.concurrent.atomic.AtomicInteger

internal fun interface PipelineStep<in Input, out Output> {

    fun execute(input: Input, observer: (Result<Output>) -> Unit)
}

internal fun <Output> PipelineStep<Nothing?, Output>.execute(outputObserver: (Result<Output>) -> Unit = {}) {
    execute(null, outputObserver)
}

internal fun <Input, Output, NextOutput> PipelineStep<Input, Output>.andThen(
    nextStep: PipelineStep<Output, NextOutput>,
): PipelineStep<Input, NextOutput> {
    return PipelineStep { input, observer ->
        execute(input) { innerStepResult ->
            innerStepResult
                .onFailure { error -> observer.invoke(Result.failure(error)) }
                .onSuccess { result -> nextStep.execute(result, observer) }
        }
    }
}

internal fun <Input, Output> PipelineStep<Input, Output>.retry(times: Int): PipelineStep<Input, Output> {
    return PipelineStep { input, observer ->
        val counter = AtomicInteger(0)

        val retryObserver = object : (Result<Output>) -> Unit {

            override fun invoke(result: Result<Output>) {
                when {
                    result.exceptionOrNull() is PipelineCancellationException -> observer.invoke(result)
                    result.isSuccess -> observer.invoke(result)
                    counter.getAndIncrement() < times -> execute(input, this)
                    else -> observer.invoke(result)
                }
            }
        }

        execute(input, retryObserver)
    }
}

internal fun <Input, Output> PipelineStep<Input, Output>.retryWithFix(
    fixStep: PipelineStep<Input, Input>,
): PipelineStep<Input, Output> {
    return PipelineStep { input, observer ->
        executeRetryWithFix(input, observer, fixStep)
    }
}

private fun <Input, Output> PipelineStep<Input, Output>.executeRetryWithFix(
    input: Input,
    observer: (Result<Output>) -> Unit,
    fixStep: PipelineStep<Input, Input>,
) {
    execute(input) { result ->
        if (result.isSuccess) {
            observer.invoke(result)
        } else {
            fixStep.execute(input) { fixedInputResult ->
                fixedInputResult
                    .onSuccess { fixedInput -> executeRetryWithFix(fixedInput, observer, fixStep) }
                    .onFailure { fixError -> observer(Result.failure(fixError)) }
            }
        }
    }
}

internal fun <Input, Output, Progress> PipelineStep<Input, Output>.wrapWithProgress(
    onExecute: () -> Progress,
    onResult: (Progress) -> Unit,
): PipelineStep<Input, Output> {
    return PipelineStep { input, observer ->
        val progress = onExecute.invoke()

        execute(input) { result ->
            onResult.invoke(progress)
            observer.invoke(result)
        }
    }
}

internal fun <Input, Output> PipelineStep<Input, Output>.wrapWithProgress(
    onExecute: () -> Disposable,
): PipelineStep<Input, Output> {
    return wrapWithProgress(onExecute) { disposableProgress -> disposableProgress.dispose() }
}