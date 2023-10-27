package lib

fun usedFromTest() {
    // Lib calls doesn't tracks, so this call should be ignored
    TargetClass().unusedFunction()
}

fun usedFromTarget() {
    // Lib calls doesn't tracks, so this call should be ignored
    TargetClass().unusedFunction()
}

fun unused() = Unit