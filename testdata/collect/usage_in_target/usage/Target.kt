package project

class Target : TargetBase() {

    fun targetFun(arg: Argument) {
        usedAbstractFun()
        usedFun()
        arg.usedArgumentFun()
    }

    abstract fun usedAbstractFun()

    fun usedFun() {
        nestedUsedFun()
    }

    fun nestedUsedFun() {

    }
}

abstract class TargetBase {

}

class Argument {

    fun usedArgumentFun() = Unit
}