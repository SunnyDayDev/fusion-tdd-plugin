package project.client

import project.target.ITarget

class TargetClient(private val target: ITarget) {

    fun doSome() {
        target.targetFun()
    }
}