package project.client

import project.target.ITarget
import lib.mock.mock

class TargetClientTest {

    private val targetInterface: ITarget = mock()

    @Test
    fun test_doSome() {
        val targetClient = TargetClient(target = targetInterface)
        targetClient.doSome()
        targetInterface.targetFun()
    }
}