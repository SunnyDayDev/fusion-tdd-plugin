package dev.sunnyday.fusiontdd.fusiontddplugin.domain.util

internal sealed interface Either<out L, out R> {

    data class Left<L>(val value: L) : Either<L, Nothing>

    data class Right<R>(val value: R) : Either<Nothing, R>
}


internal fun <L> Either<L, *>.getLeftOrNull(): L? = (this as? Either.Left)?.value

internal fun <R> Either<*, R>.getRightOrNull(): R? = (this as? Either.Right)?.value

internal fun <L> Either<L, *>.requireLeft(): L = (this as Either.Left).value

internal fun <R> Either<*, R>.requireRight(): R = (this as Either.Right).value

internal val Either<*, *>.isLeft: Boolean get() = this is Either.Left

internal val Either<*, *>.isRight: Boolean get() = this is Either.Right

internal inline fun <L, R, NL> Either<L, R>.mapLeft(map: (value: L) -> NL): Either<NL, R> {
    return if (isLeft) Either.Left(map(requireLeft())) else Either.Right(requireRight())
}

internal inline fun <L, R, NL> Either<L, R>.flatMapLeft(map: (value: L) -> Either<NL, R>): Either<NL, R> {
    return if (isLeft) map(requireLeft()) else Either.Right(requireRight())
}