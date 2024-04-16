package project

class Child : Parent(), ExternalInterface {

    var interfaceVar: Int = 0

    override fun callParent() {
        chainedCallParent()
    }

    private fun chainedCallParent() {
        TODO("Not yet implemented")
    }

    override fun callInterface() {
        chainedCallInterface()
    }

    override fun callInterface(intArg: Int) {
        chainedCallInterface()
    }

    private fun chainedCallInterface() {
        TODO("Not yet implemented")
    }

    fun callInterfaceInTest() {
        // All interactions done in tests
        // For example, it can be some verifications that some method called
    }
}