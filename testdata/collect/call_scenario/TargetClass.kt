package project

import lib.usedFromTarget

class TargetClass {

    fun targetFunction() {
        // generate here
    }

    fun usedFunction() {
        usedFromTarget()
    }

    fun unusedFunction() {
        // do noting
    }
}