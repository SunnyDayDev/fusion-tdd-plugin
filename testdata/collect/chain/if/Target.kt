package project

class Target {

    // region common

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

    private fun doThen2()  = Unit
    private fun doElse2()  = Unit

    private fun doThen3()  = Unit
    private fun doElse3()  = Unit

    private fun doBoth()  = Unit

    // endregion

    // region expression

    fun executeWithExpression() {
        if (ifExpression()) {
            doThenOnIfWithExpression()
            doBothOnIfWithExpression()
        } else {
            doElseOnIfWithExpression()
            doBothOnIfWithExpression
        }
    }

    private fun doThenOnIfWithExpression() = Unit
    private fun doElseOnIfWithExpression() = Unit
    private fun doBothOnIfWithExpression() = Unit

    private fun ifExpression(): Boolean = TODO()


    // endregion
}