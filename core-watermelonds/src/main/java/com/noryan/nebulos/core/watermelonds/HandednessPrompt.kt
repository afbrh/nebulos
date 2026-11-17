package com.noryan.nebulos.core.watermelonds

import android.os.SystemClock
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.nebulos.NebulosBridge

/**
 * Phantom Hourglass and Spirit Tracks ask "Do you hold the stylus in your right or left hand?" every time a new file is created. This answers it:
 * "Right", then "Yes" on the "You use your right hand?" confirmation. A watcher thread hashes the dialogue bubble of the touch screen
 * ([NebulosBridge.screenshotBuffer], filled by [MelonEmulator.takeScreenshot]) and taps only when the picture is exactly one of the two known dialogues,
 * stable for a few polls, so nothing else on screen is ever touched. Phantom Hourglass and Spirit Tracks are both recognised, US versions only (AZEE, BKIE); other regions never match.
 * Nothing is read from or written to the game's memory or save.
 */
internal class HandednessPrompt(gameCode: String) {
    private val profile: Profile = profileFor(gameCode) ?: Profile(0, 0, 0, 0, 0, 0)
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "nebulos-hand-prompt").apply { isDaemon = true; start() }
    }

    fun stop() { running = false; thread?.interrupt(); thread = null }

    private fun loop() {
        var last = 0
        var stable = 0
        var cooldownUntil = 0L
        var loggedAt = 0
        val startedAt = SystemClock.uptimeMillis()
        while (running) {
            // New files are only created from the title screen, i.e. near the start of a session: poll fast at first, then rarely (each poll costs a small GPU readback).
            try { Thread.sleep(if (SystemClock.uptimeMillis() - startedAt < FAST_WINDOW_MS) POLL_MS else SLOW_POLL_MS) } catch (_: InterruptedException) { return }
            val buf = NebulosBridge.screenshotBuffer ?: continue
            // Waits for the next rendered frame (so it blocks while the game is paused) and fills the buffer with the real picture.
            if (!runCatching { MelonEmulator.takeScreenshot() }.getOrDefault(false)) continue
            val h = hash(buf)
            if (h == last) stable++ else { last = h; stable = 1 }
            if (LOG_HASHES && stable == STABLE_POLLS && h != loggedAt) { loggedAt = h; android.util.Log.i(TAG, "bubble hash ${Integer.toHexString(h)}") }
            if (stable < STABLE_POLLS || SystemClock.uptimeMillis() < cooldownUntil) continue
            when (h) {
                profile.promptHash -> { tap(profile.rightX, profile.rightY); android.util.Log.i(TAG, "answered: Right") }
                profile.confirmHash -> { tap(profile.yesX, profile.yesY); android.util.Log.i(TAG, "answered: Yes") }
                else -> continue
            }
            cooldownUntil = SystemClock.uptimeMillis() + COOLDOWN_MS
            last = 0; stable = 0 // look at the next screen afresh
        }
    }

    /** FNV-1a over the dialogue-text rectangle of the touch screen (rows 192-383 of the 256 x 384 BGRA screenshot buffer). */
    private fun hash(buf: java.nio.ByteBuffer): Int {
        var h = -0x7ee3623b // 2166136261
        for (row in BY until BY + BH) for (col in BX until BX + BW) {
            val px = buf.getInt(((192 + row) * 256 + col) * 4) and 0x00FFFFFF
            h = (h xor px) * 16777619
        }
        return h
    }

    private fun tap(x: Int, y: Int) {
        runCatching { MelonEmulator.onScreenTouch(x, y) }
        try { Thread.sleep(HOLD_MS) } catch (_: InterruptedException) {}
        runCatching { MelonEmulator.onScreenRelease() }
    }

    companion object {
        private const val TAG = "NebulosDsHand"
        /** While true the watcher logs the hash of every settled screen so reference values can be captured. */
        private const val LOG_HASHES = false
        private const val POLL_MS = 200L
        private const val SLOW_POLL_MS = 1000L
        private const val FAST_WINDOW_MS = 3 * 60_000L
        private const val STABLE_POLLS = 4
        private const val HOLD_MS = 160L
        private const val COOLDOWN_MS = 1800L
        // The text area inside the dialogue bubble (both prompts use the same speech box in each game; Phantom Hourglass's bubble corners show an animated
        // wood background, so they are left out): touch-screen pixels x 28-218, y 158-188.
        private const val BX = 28; private const val BY = 158; private const val BW = 190; private const val BH = 30
        /**
         * What one game shows: FNV-1a hashes of the bubble in the two dialogues (captured on a real run; 0 = not captured, never matches) and the
         * touch-screen pixel centres of the "Right" and "Yes" buttons.
         */
        private class Profile(val promptHash: Int, val confirmHash: Int, val rightX: Int, val rightY: Int, val yesX: Int, val yesY: Int)

        private val PHANTOM_HOURGLASS_US = Profile(0x70012b38, 0x398b1b8e, 205, 112, 205, 112)
        private val SPIRIT_TRACKS_US = Profile(0x0daddd80, 0x043fed40, 205, 112, 205, 112)

        private fun profileFor(code: String?): Profile? = when (code) { "AZEE" -> PHANTOM_HOURGLASS_US; "BKIE" -> SPIRIT_TRACKS_US; else -> null }

        /** Phantom Hourglass (AZEE) and Spirit Tracks (BKIE), US versions. */
        fun isSupportedGameCode(code: String?) = profileFor(code) != null
    }
}
