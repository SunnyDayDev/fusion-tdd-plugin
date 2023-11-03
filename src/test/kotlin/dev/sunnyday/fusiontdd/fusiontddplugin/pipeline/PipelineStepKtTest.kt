package dev.sunnyday.fusiontdd.fusiontddplugin.pipeline

import com.google.common.truth.Truth.assertThat
import dev.sunnyday.fusiontdd.fusiontddplugin.pipeline.util.PipelineCancellationException
import dev.sunnyday.fusiontdd.fusiontddplugin.test.executeAndWait
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PipelineStepKtTest {

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
}