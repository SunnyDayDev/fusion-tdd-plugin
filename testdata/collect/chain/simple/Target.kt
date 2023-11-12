package project

class Target {

    fun callerFun() {
        chainedFun()
    }

    private fun chainedFun() {
        // <caret>
    }
}