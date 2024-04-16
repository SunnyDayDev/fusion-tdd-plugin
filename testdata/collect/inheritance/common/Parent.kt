package project

abstract class Parent {

    abstract fun callParent()
}

interface ExternalInterface {

    var interfaceVar: Int

    fun callInterface()

    fun callInterface(intArg: Int)
}