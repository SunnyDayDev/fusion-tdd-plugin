package project

class ImportedObjectCompanion {

    companion object {

        const val CONST = "const"

        fun importedCall() {}
    }

    class Nested {

        companion object {

            fun nestedImportedCall() {}
        }
    }
}