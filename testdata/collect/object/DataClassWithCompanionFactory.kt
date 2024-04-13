package project

data class DataClassWithCompanionFactory<T>(
    val value: T,
) {

    companion object {

        fun createInt(value: Int): DataClassWithCompanionFactory<Int> = DataClassWithCompanionFactory(value)
    }
}