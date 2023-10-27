package project

import project.TargetClass

class UsedClass(private val target: TargetClass) {

    fun call() {
        target.usedFunction()
    }
}