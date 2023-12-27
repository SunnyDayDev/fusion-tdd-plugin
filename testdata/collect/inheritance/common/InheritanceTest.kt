package project

import org.junit.jupiter.api.Test

class InheritanceTest {

    private val child = Child()

    private val parentProperty: Parent = child
    private val parent: Parent = Child()

    private val externalInterfaceProperty: ExternalInterface = child
    private val externalInterface: ExternalInterface = Child()

    private val other = Other()
    private val otherInterface: ExternalInterface = other
    private val otherParent: Parent = other

    @Test
    fun `test child_callParent()`() {
        child.callParent()
    }

    @Test
    fun `test parentProperty_callParent()`() {
        parent.callParent()
    }

    @Test
    fun `test parent_callParent()`() {
        parent.callParent()
    }

    @Test
    fun `test child_callInterface()`() {
        child.callInterface()
    }

    @Test
    fun `test externalInterfaceProperty_callInterface()`() {
        externalInterface.callInterface()
    }

    @Test
    fun `test externalInterface_callInterface()`() {
        externalInterface.callInterface()
    }

    @Test
    fun `test other`() {
        otherParent.callParent()
        otherInterface.callInterface()
    }
}