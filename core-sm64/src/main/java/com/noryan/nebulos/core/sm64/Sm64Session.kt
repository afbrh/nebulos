package com.noryan.nebulos.core.sm64

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.SlotInfo
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.nebulos.recomp.RecompInput

/**
 * [CoreSession] of the Super Mario 64 core (own process). Not an emulator: no save states (the game's own saves), no host pause (the shared menu opens over the running game while the
 * router holds the controller back), close = finish and end the process. Input: canonical pads -> the one SDL virtual controller ([Sm64Pad], sm64-src/nebulos/nebulos_vpad.c), laid out
 * for the N64 on Ghostship's default mapping (A=A, B=B, Z=left trigger, R=right trigger, L=left shoulder, C buttons=right stick, Start, D-pad):
 *  - A -> A, B -> B (N64 face buttons by label); X -> B too (the thumb falls on it more naturally while holding the stick); Y -> A (jump alternative);
 *  - L2 / L1 -> Z (the crouch/ground-pound button, on either trigger or bumper), R2 / R1 -> R (camera), right stick -> C buttons, Start -> Start.
 */
class Sm64Session(private val activity: Activity, override val stateUnavailableReason: String) : CoreSession, StateSlotProvider, StateSupport {
    override fun slotInfo(slot: Int) = SlotInfo(slot, false)
    private val main = Handler(Looper.getMainLooper())
    var onNotice: (String) -> Unit = {}

    private val dirs = FloatArray(8)   // LS up/down/left/right, RS up/down/left/right
    private var zL1 = false; private var zL2 = 0f   // Z comes from the bumper or the trigger
    private var rR1 = false; private var rR2 = 0f
    private val held = HashMap<Int, Int>()          // SDL button -> number of canonical buttons holding it
    private val downAt = HashMap<Int, Long>()       // SDL button -> uptime of the press that started holding it

    override fun pause() { releaseAll() }
    override fun resume() {}
    override fun saveState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }
    override fun loadState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }

    override fun close() {
        releaseAll()
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 500)
    }

    fun releaseAll() {
        synchronized(this) {
            held.clear(); dirs.fill(0f); zL1 = false; zL2 = 0f; rR1 = false; rR2 = 0f
        }
        call { Sm64Pad.nativeReleaseAll() }
    }

    private fun sdlButton(p: Pad): Int? = when (p) {
        Pad.A, Pad.Y -> RecompInput.SOUTH          // N64 A (SDL south = Ghostship's A)
        Pad.B, Pad.X -> RecompInput.EAST           // N64 B
        Pad.START -> RecompInput.START
        Pad.DPAD_UP -> RecompInput.DPAD_UP
        Pad.DPAD_DOWN -> RecompInput.DPAD_DOWN
        Pad.DPAD_LEFT -> RecompInput.DPAD_LEFT
        Pad.DPAD_RIGHT -> RecompInput.DPAD_RIGHT
        else -> null
    }

    override fun injectButton(pad: Pad, pressed: Boolean) {
        if (pressed) android.util.Log.d("Sm64Session", "pad " + pad + " down")
        when (pad) {
            Pad.L1 -> { synchronized(this) { zL1 = pressed }; z(); return }
            Pad.R1 -> { synchronized(this) { rR1 = pressed }; r(); return }
            else -> {}
        }
        val b = sdlButton(pad) ?: return
        val now = android.os.SystemClock.uptimeMillis()
        var releaseIn = 0L
        val send = synchronized(this) {
            val n = (held[b] ?: 0) + if (pressed) 1 else -1
            if (n <= 0) held.remove(b) else held[b] = n
            if (pressed) {
                if (n == 1) { downAt[b] = now; main.removeCallbacksAndMessages(b) }
                n == 1
            } else if (n <= 0) {
                // the game samples the controller once per 30 fps frame: a tap shorter than that would be missed, so the release waits out the rest of MIN_PRESS_MS
                releaseIn = (MIN_PRESS_MS - (now - (downAt[b] ?: 0L))).coerceAtLeast(0L)
                releaseIn == 0L
            } else false
        }
        if (send) call { Sm64Pad.nativeButton(b, pressed) }
        else if (!pressed && releaseIn > 0L) main.postAtTime({
            val still = synchronized(this) { held[b] ?: 0 } > 0
            if (!still) call { Sm64Pad.nativeButton(b, false) }
        }, b, now + releaseIn)
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val v = value.coerceIn(0f, 1f)
        when (pad) {
            Pad.L2 -> { synchronized(this) { zL2 = v }; z() }
            Pad.R2 -> { synchronized(this) { rR2 = v }; r() }
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

    // SDL: +Y is down; canonical "up" is positive.
    private fun lx() = call { Sm64Pad.nativeAxis(RecompInput.AXIS_LEFTX, RecompInput.stick(dirs[2], dirs[3])) }
    private fun ly() = call { Sm64Pad.nativeAxis(RecompInput.AXIS_LEFTY, RecompInput.stick(dirs[0], dirs[1])) }
    private fun rx() = call { Sm64Pad.nativeAxis(RecompInput.AXIS_RIGHTX, RecompInput.stick(dirs[6], dirs[7])) }
    private fun ry() = call { Sm64Pad.nativeAxis(RecompInput.AXIS_RIGHTY, RecompInput.stick(dirs[4], dirs[5])) }
    private fun z() { val v = synchronized(this) { if (zL1) 1f else zL2 }; call { Sm64Pad.nativeAxis(RecompInput.AXIS_LTRIG, RecompInput.trigger(v)) } }
    private fun r() { val v = synchronized(this) { if (rR1) 1f else rR2 }; call { Sm64Pad.nativeAxis(RecompInput.AXIS_RTRIG, RecompInput.trigger(v)) } }

    private companion object { const val MIN_PRESS_MS = 70L }

    private inline fun call(block: () -> Unit) {
        try { block() } catch (_: UnsatisfiedLinkError) { /* native library not loaded yet */ }
    }
}
