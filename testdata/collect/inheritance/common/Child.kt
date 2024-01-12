package project

class Child : Parent(), ExternalInterface {

    override fun callParent() {
        chainedCallParent()
    }

    private fun chainedCallParent() {
        TODO("Not yet implemented")
    }
    override fun callInterface() {
        chainedCallInterface()
    }

    private fun chainedCallInterface() {
        TODO("Not yet implemented")
    }
}