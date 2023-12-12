package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PipelineStepKtTest {

    // region retry

    @Test
    fun `on retry, if error received, retry n times`() {
        val counter = AtomicInteger()
        val pipeLine = PipelineStep<Nothing?, Unit> { _, observer ->
            counter.incrementAndGet()
            observer.invoke(Result.failure(Error()))
        }

        pipeLine.retry(3).executeAndWait()

        assertThat(counter.get()).isEqualTo(4)
    }

    @Test
    fun `on retry, if pipeline cancelled, don't retry`() {
        val counter = AtomicInteger()
        val pipeLine = PipelineStep<Nothing?, Unit> { _, observer ->
            counter.incrementAndGet()
            observer.invoke(Result.failure(PipelineCancellationException()))
        }

        pipeLine.retry(3).executeAndWait()

        assertThat(counter.get()).isEqualTo(1)
    }

    // endregion

    // region retryWithFix

    @Test
    fun `on fixWith, fix input and retry`() {
        // arrange
        val brokenInput = -1
        val fixedInput = 777
        val pipeLine = PipelineStep<Int, Int> { input, observer ->
            if (input != fixedInput) {
                observer.invoke(Result.failure(Error("input is broken")))
            } else {
                observer.invoke(Result.success(input))
            }
        }
        val fixStep = PipelineStep<Int, Int> { _, observer ->
            observer.invoke(Result.success(fixedInput))
        }

        // act
        val outputResult = pipeLine.retryWithFix(fixStep).executeAndWait(brokenInput)

        // assert
        assertThat(outputResult.getOrNull()).isEqualTo(fixedInput)
    }

    @Test
    fun `on fixWith, repeat fix until success`() {
        // arrange
        val brokenInput = 0
        val fixedInput = 3
        val pipeLine = PipelineStep<Int, Int> { input, observer ->
            if (input != fixedInput) {
                observer.invoke(Result.failure(Error("input is broken")))
            } else {
                observer.invoke(Result.success(input))
            }
        }
        val fixStep = PipelineStep<Int, Int> { input, observer ->
            observer.invoke(Result.success(input + 1))
        }

        // act
        val outputResult = pipeLine.retryWithFix(fixStep).executeAndWait(brokenInput)

        // assert
        assertThat(outputResult.getOrNull()).isEqualTo(fixedInput)
    }

    @Test
    fun `on fixWith error result, throw it to the pipeline`() {
        // arrange
        val pipeLine = PipelineStep<Int, Int> { _, observer ->
            observer.invoke(Result.failure(Error("input failed")))
        }
        val fixStep = PipelineStep<Int, Int> { _, observer ->
            observer.invoke(Result.failure(Error("fix failed")))
        }

        // act
        val outputResult = pipeLine.retryWithFix(fixStep).executeAndWait(-1)

        // assert
        assertThat(outputResult.exceptionOrNull()?.message).isEqualTo("fix failed")
    }

    // endregion
}