package com.noryan.nebulos.core.smw

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.CoreSessionBase
import com.noryan.nebulos.recomp.RecompInput
import java.io.File

/**
 * [com.noryan.nebulos.api.CoreSession] of the Super Mario World core (own process). The game is paused while the menu is open; save states are the
 * port's own snapshots, kept in the shared Nebulos state folder. Input: canonical pads -> the one SDL virtual controller, by position (the Nebulos
 * Nintendo labels sit where the SNES buttons sit, so A is A, B is B, X is X, Y is Y); the left stick works as a D-pad (the game's own handling). One player.
 */
class SmwSession(private val activity: Activity, context: Context, romPath: String) : CoreSessionBase(context, "smw", romPath) {
    private val main = Handler(Looper.getMainLooper())
    private val dirs = FloatArray(4)   // left stick up/down/left/right

    override fun pause() { releaseAll(); call { SmwNative.nativeSetPaused(true) } }
    override fun resume() { call { SmwNative.nativeSetPaused(false) } }

    override fun writeState(slot: Int, file: File): Boolean {
        file.parentFile?.mkdirs()
        return state(file, save = true)
    }

    override fun readState(slot: Int, file: File): Boolean = state(file, save = false)

    private fun state(file: File, save: Boolean): Boolean =
        try { SmwNative.nativeState(file.absolutePath, save) } catch (_: UnsatisfiedLinkError) { false }

    override fun close() {
        releaseAll()
        call { SmwNative.nativeSetPaused(true) }   // nothing is written by the game from here on
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 500)
    }

    fun releaseAll() {
        dirs.fill(0f)
        tokens.values.forEach { main.removeCallbacksAndMessages(it) }
        call { SmwNative.nativeReleaseAll() }
    }

    private val downAt = HashMap<Int, Long>()   // SDL button -> uptime of its last press
    private val tokens = HashMap<Int, Any>()    // SDL button -> its delayed release

    override fun injectButton(pad: Pad, pressed: Boolean) {
        val b = RecompInput.button(pad) ?: return
        val token = tokens.getOrPut(b) { Any() }
        main.removeCallbacksAndMessages(token)
        if (pressed) {
            downAt[b] = SystemClock.uptimeMillis()
            call { SmwNative.nativeButton(b, true) }
        } else {
            // the controller is read once per frame: a tap shorter than that would be missed, so its release waits out the rest of MIN_PRESS_MS
            val at = (downAt[b] ?: 0L) + MIN_PRESS_MS
            if (at <= SystemClock.uptimeMillis()) call { SmwNative.nativeButton(b, false) }
            else main.postAtTime({ call { SmwNative.nativeButton(b, false) } }, token, at)
        }
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val v = value.coerceIn(0f, 1f)
        when (pad) {
            Pad.L2 -> call { SmwNative.nativeAxis(RecompInput.AXIS_LTRIG, RecompInput.trigger(v)) }
            Pad.R2 -> call { SmwNative.nativeAxis(RecompInput.AXIS_RTRIG, RecompInput.trigger(v)) }
            // SDL: +Y is down; canonical "up" is positive
            Pad.LS_UP, Pad.LS_DOWN -> { dirs[if (pad == Pad.LS_UP) 0 else 1] = v; call { SmwNative.nativeAxis(RecompInput.AXIS_LEFTY, RecompInput.stick(dirs[0], dirs[1])) } }
            Pad.LS_LEFT, Pad.LS_RIGHT -> { dirs[if (pad == Pad.LS_LEFT) 2 else 3] = v; call { SmwNative.nativeAxis(RecompInput.AXIS_LEFTX, RecompInput.stick(dirs[2], dirs[3])) } }
            else -> {}
        }
    }

    private companion object { const val MIN_PRESS_MS = 50L }

    private inline fun call(block: () -> Unit) {
        try { block() } catch (_: UnsatisfiedLinkError) { /* native library not loaded yet */ }
    }
}
