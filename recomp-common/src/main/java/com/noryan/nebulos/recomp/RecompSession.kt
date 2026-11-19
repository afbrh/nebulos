package com.noryan.nebulos.recomp

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.SlotInfo
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.recompsdl.RecompPad

/**
 * [CoreSession] shared by the BotW / TotK recomp cores (each runs in its own process). The recomp runtimes are ports, not emulators:
 *  - states : none (the game's own saves are used), so the menu greys both rows with [stateUnavailableReason];
 *  - pause  : the runtime has no host pause; the shared menu simply opens over the running game, controller input is held back by the
 *             router while the menu is open (all held inputs are released);
 *  - close  : finish the Activity and end the process (the runtime calls exit() when its window goes, and reserves ~12.8 GB of guest
 *             address space: never reused, a cold start every launch like the other cores);
 *  - input  : canonical pads -> the SDL virtual gamepad ([RecompInput], recomp_vpad.c).
 */
class RecompSession(
    private val activity: Activity,
    override val stateUnavailableReason: String,
) : CoreSession, StateSlotProvider, StateSupport {
    override fun slotInfo(slot: Int) = SlotInfo(slot, false)
    private val main = Handler(Looper.getMainLooper())
    var onNotice: (String) -> Unit = {}

    override fun pause() { call { RecompPad.nativeReleaseAll() } }
    override fun resume() {}
    override fun saveState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }
    override fun loadState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }

    override fun close() {
        call { RecompPad.nativeReleaseAll() }
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 500)
    }

    private val dirs = FloatArray(8) // LS up/down/left/right, RS up/down/left/right

    override fun injectButton(pad: Pad, pressed: Boolean) {
        val b = RecompInput.button(pad) ?: return
        call { RecompPad.nativeButton(b, pressed) }
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val v = value.coerceIn(0f, 1f)
        call {
            when (pad) {
                Pad.L2 -> RecompPad.nativeAxis(RecompInput.AXIS_LTRIG, RecompInput.trigger(v))
                Pad.R2 -> RecompPad.nativeAxis(RecompInput.AXIS_RTRIG, RecompInput.trigger(v))
                Pad.LS_UP -> { dirs[0] = v; ly(0) }
                Pad.LS_DOWN -> { dirs[1] = v; ly(0) }
                Pad.LS_LEFT -> { dirs[2] = v; lx(0) }
                Pad.LS_RIGHT -> { dirs[3] = v; lx(0) }
                Pad.RS_UP -> { dirs[4] = v; ry() }
                Pad.RS_DOWN -> { dirs[5] = v; ry() }
                Pad.RS_LEFT -> { dirs[6] = v; rx() }
                Pad.RS_RIGHT -> { dirs[7] = v; rx() }
                else -> {}
            }
        }
    }

    // SDL: +Y is down; canonical "up" is positive.
    private fun lx(o: Int) = RecompPad.nativeAxis(RecompInput.AXIS_LEFTX, RecompInput.stick(dirs[o + 2], dirs[o + 3]))
    private fun ly(o: Int) = RecompPad.nativeAxis(RecompInput.AXIS_LEFTY, RecompInput.stick(dirs[o], dirs[o + 1]))
    private fun rx() = RecompPad.nativeAxis(RecompInput.AXIS_RIGHTX, RecompInput.stick(dirs[6], dirs[7]))
    private fun ry() = RecompPad.nativeAxis(RecompInput.AXIS_RIGHTY, RecompInput.stick(dirs[4], dirs[5]))

    private inline fun call(block: () -> Unit) {
        try { block() } catch (_: UnsatisfiedLinkError) { /* native library not loaded yet */ }
    }
}
