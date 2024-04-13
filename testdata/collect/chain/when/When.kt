package project

class When {

    fun execute(case: Int) {
        when (case) {
            1 -> {
                doSome()
                doAll()
            }

            2 -> {
                doAll()
            }

            else -> {
                doElse()
                doAll()
            }
        }
    }

    private fun doSome() = Unit
    private fun doElse() = Unit
    private fun doAll() = Unit

    fun executeWithExpression(case: Int) {
        when (whenExpression(case)) {
            1 -> {
                doSomeWithExpression()
                doAllWithExpression()
            }

            2 -> {
                doAllWithExpression()
            }

            else -> {
                doElseWithExpression()
                doAllWithExpression()
            }
        }
    }

    private fun whenExpression(case: Int): Int = case

    private fun doSomeWithExpression() = Unit
    private fun doElseWithExpression() = Unit
    private fun doAllWithExpression() = Unit
}