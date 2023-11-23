package project

class Target {

    fun execute() {
        if (true) {
            doThen()
        } else {
            doElse()
        }
        if (true) {
            doBoth()
        } else {
            doBoth()
        }
    }

    private fun doThen() {
        if (true) {
            doThen2()
        } else {
            doElse2()
        }
    }

    private fun doElse() {
        if (true) {
            doThen3()
        } else {
            doElse3()
        }
    }

    private fun doThen2() {

    }

    private fun doElse2() {

    }

    private fun doThen3() {

    }

    private fun doElse3() {

    }

    private fun doBoth() {

    }
}