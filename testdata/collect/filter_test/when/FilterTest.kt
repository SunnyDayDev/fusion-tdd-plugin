package project

import org.junit.jupiter.api.Test

class FilterTest {

    private val target = Target()

    @Test
    fun `test onEventOne`() {
        target.onEvent(Event.One)
    }

    @Test
    fun `test onEventTwo`() {
        target.onEvent(Event.Two())
    }

    @Test
    fun `test onEventThree`() {
        target.onEvent(Event.Three)
    }

    @Test
    fun `test onEventFour`() {
        target.onEvent(Event.Four)
    }

    @Test
    fun `test Ignored`() {
        target.onEvent(Ignored)
    }

    @Test
    fun `test onValueEventOne`() {
        target.onValue(Event.One)
    }

    @Test
    fun `test onValueValue`() {
        target.onValue(Value(3))
    }

    @Test
    fun `test onRangeConditionEventOne`() {
        target.onRangeCondition(Event.One)
    }

    @Test
    fun `test onRangeConditionValue`() {
        target.onRangeCondition(5)
    }
}