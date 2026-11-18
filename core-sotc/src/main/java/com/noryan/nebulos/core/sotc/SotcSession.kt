package com.noryan.nebulos.core.sotc

import android.os.Handler
import android.os.Looper
import android.os.Process
import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.SlotInfo
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport

/**
 * [CoreSession] for Celosia (runs in the `:sotc` process, same process as the game).
 *  - pause/resume : the guest freezes at its next checkpoint and sound is muted; the clocks are shifted on resume.
 *  - states       : none, a native port has no machine state to snapshot; the game's own memory card saves are used.
 *  - close        : release input, pause, finish the Activity and end the process (cold start every launch).
 *  - input        : canonical pads -> PS2 button masks and stick values ([SotcInput]).
 */
internal class SotcSession(private val activity: SotcActivity) : CoreSession, StateSlotProvider, StateSupport {
    override val stateUnavailableReason: String = SotcCore.STATE_UNAVAILABLE_REASON
    override fun slotInfo(slot: Int) = SlotInfo(slot, false)

    private val main = Handler(Looper.getMainLooper())

    override fun pause() { call { SotcNative.nativeSetPaused(true) } }
    override fun resume() { call { SotcNative.nativeSetPaused(false) } }

    override fun saveState(slot: Int): Boolean { activity.notice(stateUnavailableReason); return false }
    override fun loadState(slot: Int): Boolean { activity.notice(stateUnavailableReason); return false }

    override fun close() {
        call { SotcNative.nativeReleaseAll() }
        call { SotcNative.nativeSetPaused(false) }   // never leave the runtime blocked on a pause: it must be able to exit
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        // The memory card is written as soon as the game saves; the native loop is not torn down mid-frame.
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 600)
    }

    private val dirs = FloatArray(8) // LS up/down/left/right, RS up/down/left/right

    override fun injectButton(pad: Pad, pressed: Boolean) {
        val m = SotcInput.button(pad) ?: return
        call { SotcNative.nativeButton(m, pressed) }
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val v = value.coerceIn(0f, 1f)
        call {
            when (pad) {
                Pad.L2 -> SotcNative.nativeButton(SotcInput.L2, v > SotcInput.TRIGGER_ON)
                Pad.R2 -> SotcNative.nativeButton(SotcInput.R2, v > SotcInput.TRIGGER_ON)
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

    // +1 = right / down (the pad's native convention); the right stick (camera) is inverted on both axes (user preference).
    private fun lx() = SotcNative.nativeAxis(SotcInput.AXIS_LX, SotcInput.axis(dirs[2], dirs[3]))
    private fun ly() = SotcNative.nativeAxis(SotcInput.AXIS_LY, SotcInput.axis(dirs[0], dirs[1]))
    private fun rx() = SotcNative.nativeAxis(SotcInput.AXIS_RX, -SotcInput.axis(dirs[6], dirs[7]))
    private fun ry() = SotcNative.nativeAxis(SotcInput.AXIS_RY, -SotcInput.axis(dirs[4], dirs[5]))

    private inline fun call(block: () -> Unit) {
        try { block() } catch (_: UnsatisfiedLinkError) { /* native library not loaded yet */ }
    }
}
