package dev.sunnyday.fusiontdd.fusiontddplugin.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EitherKtTest {

    @Test
    fun `on get left if value exists, returns left value`() {
        val leftValue = "left"
        val either = Either.Left(leftValue)

        val actualValue = either.getLeftOrNull()

        assertThat(actualValue).isEqualTo(leftValue)
    }

    @Test
    fun `on get left if value no exists, returns null`() {
        val either: Either<Int, String> = Either.Right("right")

        val actualValue = either.getLeftOrNull()

        assertThat(actualValue).isNull()
    }

    @Test
    fun `on get right if value exists, returns right value`() {
        val rightValue = "right"
        val either = Either.Right(rightValue)

        val actualValue = either.getRightOrNull()

        assertThat(actualValue).isEqualTo(rightValue)
    }

    @Test
    fun `on get right if value no exists, returns null`() {
        val either: Either<String, Int> = Either.Left("left")

        val actualValue = either.getRightOrNull()

        assertThat(actualValue).isNull()
    }

    @Test
    fun `on require right if left value exists, returns left value`() {
        val rightValue = "right"
        val either = Either.Right(rightValue)

        val actualValue = either.requireRight()

        assertThat(actualValue).isEqualTo(rightValue)
    }

    @Test
    fun `on isLeft if is Either_Left, returns true`() {
        val either = Either.Left("left")
        assertThat(either.isLeft).isTrue()
    }

    @Test
    fun `on isLeft if is Either_Right, returns false`() {
        val either = Either.Right("right")
        assertThat(either.isLeft).isFalse()
    }

    @Test
    fun `on isRight if is Either_Right, returns true`() {
        val either = Either.Right("right")
        assertThat(either.isRight).isTrue()
    }

    @Test
    fun `on isRight if is Either_Left, returns false`() {
        val either = Either.Left("left")
        assertThat(either.isRight).isFalse()
    }
}