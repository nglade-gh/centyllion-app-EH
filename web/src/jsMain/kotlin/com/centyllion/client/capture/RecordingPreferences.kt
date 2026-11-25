package com.centyllion.client.capture

import com.centyllion.model.RecordingSettings
import com.centyllion.model.decodeRecordingSettings
import com.centyllion.model.encodeRecordingSettings
import kotlinx.browser.window

/**
 * The settings of the Records tab, kept by the browser.
 *
 * They are interface preferences, so they live in `localStorage` and never in the project file:
 * opening a `.nbd2` written on another machine must not change the way this one records. The
 * encoding and every fallback belong to the core (`encodeRecordingSettings` /
 * `decodeRecordingSettings`, `core/.../model/recording.kt`), which `:core:jvmTest` checks; this
 * file only adds the two browser calls, both of them able to fail without consequence.
 */

/** localStorage key of the recording settings: one short line-oriented text, never an image. */
const val RECORDING_SETTINGS_KEY = "centyllion-recording-settings"

/**
 * The settings remembered by this browser, or the defaults when nothing was stored, when the
 * stored text is unreadable and when `localStorage` itself is refused (private mode, a page
 * without storage): recording always works.
 */
fun loadRecordingSettings(): RecordingSettings = try {
    decodeRecordingSettings(window.localStorage.getItem(RECORDING_SETTINGS_KEY))
} catch (t: Throwable) {
    console.warn("Records: local storage is not available, using the default settings.", t)
    RecordingSettings()
}

/**
 * Remembers [settings] for the next visit. A browser refusing `localStorage` keeps the settings
 * for the current session only: the caller is not told, since nothing in the interface depends on
 * the write.
 */
fun saveRecordingSettings(settings: RecordingSettings) {
    try {
        window.localStorage.setItem(RECORDING_SETTINGS_KEY, encodeRecordingSettings(settings))
    } catch (t: Throwable) {
        console.warn("Records: cannot remember the recording settings.", t)
    }
}
