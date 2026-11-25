package com.centyllion.model

import kotlin.js.Date

/**
 * Wall clock of the browser.
 *
 * `Date.now()` returns a `Double` in Kotlin/JS: the epoch milliseconds are narrowed to a `Long`,
 * which is exact within the range of the JavaScript numbers (2^53).
 *
 * This is the wall clock of the page: it keeps running when the tab is in the background, but the
 * animation frames that poll it do not, which is why a timed pause can last longer than asked
 * (never shorter).
 */
actual fun systemClock(): Clock = Clock { Date.now().toLong() }
