package project

class TargetClass {

    fun onSome(some: Some) {}

    fun createByCompanionFactory(): DataClassWithCompanionFactory<Int> {}

    fun importedCall() {}

    fun nestedImportedCall() {}

    fun importedConst(value: String) {}
}