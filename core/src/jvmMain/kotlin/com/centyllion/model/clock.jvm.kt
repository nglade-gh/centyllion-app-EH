package com.centyllion.model

/** Wall clock of the JVM. */
actual fun systemClock(): Clock = Clock { System.currentTimeMillis() }
