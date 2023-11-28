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

    private fun doSome() {

    }

    private fun doElse() {

    }

    private fun doAll() {

    }
}