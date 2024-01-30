package project

class Target {

    // region Common 'is' condition

    fun onEvent(event: Any): Int {
        return when (val whenProp = event) {
            !is Event -> 0
            Event.One -> onEventOne()
            is Event.Two -> 2
            else -> onElse()
        }
    }

    private fun onEventOne(): Int = 0

    private fun onElse(): Int = 0

    // endregion

    // region Concrete value condition

    fun onValue(event: Any): Int {
        return when (val whenProp = event) {
            Value(5) -> 5
            Event.One -> 1
            else -> onValueElse()
        }
    }

    private fun onValueElse() = 0

    // endregion

    // region Range condition

    fun onRangeCondition(event: Any): Int {
        return when (val whenProp = event) {
            in 0..5 -> 5
            Event.One -> onRangeConditionEventOne()
            else -> 0
        }
    }

    fun onRangeConditionEventOne() = 0

    // endregion
}

sealed interface Event {

    object One : Event

    class Two : Event

    object Three : Event

    object Four : Event

    object Unused : Event
}

object Ignored

data class Value(val value: Int)