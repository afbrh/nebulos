package com.noryan.nebulos.core.jak

import android.os.Handler
import android.os.Looper
import android.os.Process
import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.SlotInfo
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport

/**
 * [CoreSession] for OpenGOAL (runs in the `:jak` process, same process as the game).
 *  - pause/resume : native host pause (display loop stops presenting, audio is silenced): the whole game freezes.
 *  - states       : none, this is a native port with no machine state to snapshot; the games' own saves are used.
 *  - close        : release input, pause, finish the Activity and end the process (cold start every launch, like the other cores).
 *  - input        : canonical pads -> an SDL virtual gamepad ([JakInput]).
 */
internal class JakSession(private val activity: JakActivity) : CoreSession, StateSlotProvider, StateSupport {
    override val stateUnavailableReason: String = JakCore.STATE_UNAVAILABLE_REASON
    override fun slotInfo(slot: Int) = SlotInfo(slot, false)

    private val main = Handler(Looper.getMainLooper())

    override fun pause() { call { JakNative.nativeSetPaused(true) } }
    override fun resume() { call { JakNative.nativeSetPaused(false) } }

    override fun saveState(slot: Int): Boolean { activity.notice(stateUnavailableReason); return false }
    override fun loadState(slot: Int): Boolean { activity.notice(stateUnavailableReason); return false }

    override fun close() {
        call { JakNative.nativeReleaseAll() }
        call { JakNative.nativeSetPaused(true) }
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        // The native game loop cannot be torn down cleanly mid-frame (game saves are written as soon as the game makes them).
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 600)
    }

    private val dirs = FloatArray(8) // LS up/down/left/right, RS up/down/left/right

    override fun injectButton(pad: Pad, pressed: Boolean) {
        val b = JakInput.button(pad) ?: return
        call { JakNative.nativeButton(b, pressed) }
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val v = value.coerceIn(0f, 1f)
        call {
            when (pad) {
                Pad.L2 -> JakNative.nativeAxis(JakInput.AXIS_LTRIG, JakInput.trigger(v))
                Pad.R2 -> JakNative.nativeAxis(JakInput.AXIS_RTRIG, JakInput.trigger(v))
                Pad.LS_UP -> { dirs[0] = v; ly() }
                Pad.LS_DOWN -> { dirs[1] = v; ly() }
                Pad.LS_LEFT -> { dirs[2] = v; lx() }
                Pad.LS_RIGHT -> { dirs[3] = v; lx() }
                Pad.RS_UP -> { dirs[4] = v; ry() }
                Pad.RS_DOWN -> { dirs[5] = v; ry() }
                Pad.RS_LEFT -> { dirs[6] = v; rx() }
                Pad.RS_RIGHT -> { dirs[7] = v; rx() }
                else -> {}
            }
        }
    }

    // SDL: +Y is down; canonical "up" is positive.
    private fun lx() = JakNative.nativeAxis(JakInput.AXIS_LEFTX, JakInput.stick(dirs[2], dirs[3]))
    private fun ly() = JakNative.nativeAxis(JakInput.AXIS_LEFTY, JakInput.stick(dirs[0], dirs[1]))
    // right stick left/right is inverted for all three games (user preference): negative/positive swapped
    private fun rx() = JakNative.nativeAxis(JakInput.AXIS_RIGHTX, JakInput.stick(dirs[7], dirs[6]))
    private fun ry() = JakNative.nativeAxis(JakInput.AXIS_RIGHTY, JakInput.stick(dirs[4], dirs[5]))

    private inline fun call(block: () -> Unit) {
        try { block() } catch (_: UnsatisfiedLinkError) { /* native library not loaded yet */ }
    }
}
