package project

import project.TargetClass

class UnusedClass(private val target: TargetClass) {

    fun call() {
        target.unusedFunction()
    }
}