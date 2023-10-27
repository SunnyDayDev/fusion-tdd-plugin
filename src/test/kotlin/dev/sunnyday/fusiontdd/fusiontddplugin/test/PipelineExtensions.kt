package dev.sunnyday.fusiontdd.fusiontddplugin.test

import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.PipelineStep
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.execute
import java.util.concurrent.CompletableFuture

internal fun <I, O> PipelineStep<I, O>.executeAndWait(input: I): Result<O> {
    val future = CompletableFuture<Result<O>>()

    execute(input, future::complete)

    return future.get()
}

internal fun <O> PipelineStep<Nothing?, O>.executeAndWait(): Result<O> {
    val future = CompletableFuture<Result<O>>()

    execute(future::complete)

    return future.get()
}