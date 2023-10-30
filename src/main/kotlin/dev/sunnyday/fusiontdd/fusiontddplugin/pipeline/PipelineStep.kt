package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

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
                    result.isSuccess -> observer.invoke(result)
                    counter.getAndIncrement() < times -> execute(input, this)
                    else -> observer.invoke(result)
                }
            }
        }

        execute(input, retryObserver)
    }
}