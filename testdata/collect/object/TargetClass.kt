package project

class TargetClass {

    fun onSome(some: Some) { }

    fun createByCompanionFactory(): DataClassWithCompanionFactory<Int> { }
}

sealed interface Some {
    data object Object : Some
}

data class DataClassWithCompanionFactory<T>(
    val value: T,
) {

    companion object {

        fun createInt(value: Int): DataClassWithCompanionFactory<Int> = DataClassWithCompanionFactory(value)
    }
}