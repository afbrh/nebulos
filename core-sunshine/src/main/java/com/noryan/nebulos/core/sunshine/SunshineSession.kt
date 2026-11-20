package com.noryan.nebulos.core.sunshine

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.SlotInfo
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import dev.chasem.sms.sdl.TouchController

/**
 * [CoreSession] of the Sunshine core (own process). The port is not an emulator: no save states (the game's own saves are used), no host pause (the shared menu opens
 * over the running game while the router holds the controller back), close = finish and end the process. Input: canonical pads -> the port's one extra SDL controller
 * ([TouchController], Android's own key and axis numbers, the port's touch-controls path), by the GameCube's labels.
 */
class SunshineSession(private val activity: Activity, override val stateUnavailableReason: String) : CoreSession, StateSlotProvider, StateSupport {
    override fun slotInfo(slot: Int) = SlotInfo(slot, false)
    private val main = Handler(Looper.getMainLooper())
    var onNotice: (String) -> Unit = {}
    private val held = HashSet<Int>()
    private val dirs = FloatArray(8)   // LS up/down/left/right, RS up/down/left/right
    @Volatile private var ready = false

    /** The virtual controller is added when SDL first polls; calls before that are harmless. */
    fun start() { ready = true; releaseAll() }

    override fun pause() { releaseAll() }
    override fun resume() {}
    override fun saveState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }
    override fun loadState(slot: Int): Boolean { onNotice(stateUnavailableReason); return false }

    override fun close() {
        releaseAll()
        main.postDelayed({ if (!activity.isFinishing) activity.finishAndRemoveTask() }, 250)
        main.postDelayed({ Process.killProcess(Process.myPid()) }, 500)
    }

    private fun releaseAll() {
        if (!ready) return
        runCatching {
            for (k in held.toList()) TouchController.button(k, false)
            held.clear()
            TouchController.axis(0, 0f); TouchController.axis(1, 0f); TouchController.axis(2, 0f); TouchController.axis(3, 0f)
            TouchController.axis(4, -1f); TouchController.axis(5, -1f)
            dirs.fill(0f)
        }
    }

    private fun key(p: Pad): Int? = when (p) {
        Pad.A -> KeyEvent.KEYCODE_BUTTON_A
        Pad.B -> KeyEvent.KEYCODE_BUTTON_B
        Pad.X -> KeyEvent.KEYCODE_BUTTON_X
        Pad.Y -> KeyEvent.KEYCODE_BUTTON_Y
        Pad.START -> KeyEvent.KEYCODE_BUTTON_START
        Pad.R1 -> KeyEvent.KEYCODE_BUTTON_R1     // the GameCube's Z
        Pad.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        Pad.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        Pad.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        Pad.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }

    override fun injectButton(pad: Pad, pressed: Boolean) {
        if (!ready) return
        val k = key(pad) ?: return
        synchronized(held) { if (pressed) held += k else held -= k }
        runCatching { TouchController.button(k, pressed) }
    }

    override fun injectAxis(pad: Pad, value: Float) {
        if (!ready) return
        val v = value.coerceIn(0f, 1f)
        runCatching {
            when (pad) {
                Pad.L2 -> TouchController.axis(4, -1f + 2f * v)   // a trigger rests at -1
                Pad.R2 -> TouchController.axis(5, -1f + 2f * v)
                Pad.LS_UP -> { dirs[0] = v; TouchController.axis(1, dirs[1] - dirs[0]) }
                Pad.LS_DOWN -> { dirs[1] = v; TouchController.axis(1, dirs[1] - dirs[0]) }
                Pad.LS_LEFT -> { dirs[2] = v; TouchController.axis(0, dirs[3] - dirs[2]) }
                Pad.LS_RIGHT -> { dirs[3] = v; TouchController.axis(0, dirs[3] - dirs[2]) }
                Pad.RS_UP -> { dirs[4] = v; TouchController.axis(3, dirs[5] - dirs[4]) }
                Pad.RS_DOWN -> { dirs[5] = v; TouchController.axis(3, dirs[5] - dirs[4]) }
                Pad.RS_LEFT -> { dirs[6] = v; TouchController.axis(2, dirs[7] - dirs[6]) }
                Pad.RS_RIGHT -> { dirs[7] = v; TouchController.axis(2, dirs[7] - dirs[6]) }
                else -> {}
            }
        }
    }
}
