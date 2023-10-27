package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

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