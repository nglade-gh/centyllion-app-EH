package com.centyllion.model

/**
 * Source of real time used by the timed pause (`Pause(t)`).
 *
 * The core never calls the system clock directly: the run loop receives a clock, so that a test
 * can advance time without sleeping (`no Thread.sleep` anywhere) and so that the JS and the JVM
 * builds keep one single semantics. Only the **comfort** pause of a demonstration reads it: the
 * simulation itself is a step counter and never depends on time.
 */
fun interface Clock {
    /** Current real time, in milliseconds. Only differences are meaningful. */
    fun nowMillis(): Long
}

/**
 * Clock of the machine, used when no clock is injected: `System.currentTimeMillis()` on the JVM,
 * `Date.now()` in the browser.
 */
expect fun systemClock(): Clock
