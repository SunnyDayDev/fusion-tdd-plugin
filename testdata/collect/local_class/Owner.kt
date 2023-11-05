package project

class Owner(val local: Local) {

    class Local(val value: Int) {

        fun unusedFun() = Unit
    }
}