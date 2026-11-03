package com.noryan.nebulos.core.primehack

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.noryan.nebulos.api.FastForwardSupport
import com.noryan.nebulos.api.Pad
import com.noryan.nebulos.api.menu.CoreSessionBase
import com.noryan.nebulos.core.primehack.jni.NativeLibrary
import com.noryan.nebulos.core.primehack.jni.activities.EmulationActivity
import com.noryan.nebulos.core.primehack.jni.features.input.model.NebulosInput
import com.noryan.nebulos.core.primehack.jni.features.settings.model.NativeConfig
import com.noryan.nebulos.core.primehack.jni.features.settings.model.Settings
import java.io.File

/**
 * CoreSession for the embedded PrimeHack engine. Canonical pad events are written to the virtual ciface device
 * "Nebulos/0/Nebulos Pad" (native, see Android.cpp NebulosDevice); PrimeHackProfiles binds GCPad1/Wiimote1 to it.
 * All NativeLibrary calls are safe from any thread (they take the JNI HostThreadLock).
 */
class PrimeHackSession(
    context: Context,
    romPath: String,
    private val activity: Activity,
) : CoreSessionBase(context, "primehack", romPath), FastForwardSupport, com.noryan.nebulos.api.OptionCycleSupport {
    /** Game ID of the disc (set by the activity after it reads the header). */
    @Volatile var gameId: String? = null

    override val optionLabel = "4:3 Screen"
    override val optionAvailable get() = com.noryan.nebulos.api.FourByThree.supports(gameId)
    override fun optionValue(): String = gameId?.let { com.noryan.nebulos.api.FourByThree.optionValue(context, it, PrimeHackConfig.fourThreeAtLaunch) } ?: ""
    override fun cycleOption(): Boolean { val id = gameId ?: return false; com.noryan.nebulos.api.FourByThree.toggle(context, id); return true }


    @Volatile var paused = false
        private set


    // ---- fast forward (hold) ---------------------------------------------------------------------------------
    /**
     * Hold-to-run Fast Forward via Dolphin's EmulationSpeed, written to the in-memory CurrentRun config layer (never
     * saved; highest priority). Dolphin's CoreTiming/Mixer pick it up through the CPU-thread config callback, so no
     * native setter is needed; the Mixer time-stretches audio. Capped at MAX_MULTIPLIER x. Release deletes the
     * CurrentRun key so the normal value (base/game config, 1.0 unless the user changed it) applies exactly again.
     */
    @Synchronized
    override fun setFastForward(on: Boolean): Boolean = try {
        val file = Settings.FILE_DOLPHIN; val sec = Settings.SECTION_INI_CORE; val key = "EmulationSpeed"
        if (on) {
            if (!NativeLibrary.IsRunning()) false
            else {
                NativeConfig.setFloat(NativeConfig.LAYER_CURRENT, file, sec, key, FastForwardSupport.MAX_MULTIPLIER.toFloat())
                true
            }
        } else {
            NativeConfig.deleteKey(NativeConfig.LAYER_CURRENT, file, sec, key)
            true
        }
    } catch (_: Throwable) { false }

    override fun pause() {
        EmulationActivity.hasUserPausedEmulation = true   // stops Dolphin's fragment un-pausing on resume while the menu is up
        if (NativeLibrary.IsRunning()) NativeLibrary.PauseEmulation(false)
        paused = true
        ShaderCacheKeeper.flush(full = true)   // menu open: persist the shader caches while paused (Close Game kills the process)
    }

    override fun resume() {
        paused = false
        EmulationActivity.hasUserPausedEmulation = false
        if (NativeLibrary.IsRunning()) NativeLibrary.UnPauseEmulation()
    }

    override fun writeState(slot: Int, file: File): Boolean {
        if (!NativeLibrary.IsRunning()) return false
        val before = if (file.exists()) file.lastModified() else 0L
        val tmpSize = if (file.exists()) file.length() else -1L
        NativeLibrary.SaveStateAs(file.absolutePath)
        // State::SaveAs hands the compress+write to a worker thread; wait for the file to appear/change and settle.
        val deadline = android.os.SystemClock.uptimeMillis() + 8000
        var lastLen = -2L
        var stable = 0
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
            if (!file.exists()) continue
            val changed = file.lastModified() != before || file.length() != tmpSize
            if (!changed) continue
            val len = file.length()
            if (len == lastLen && len > 0) { if (++stable >= 3) return true } else { stable = 0; lastLen = len }
        }
        return file.exists() && file.length() > 0 && file.lastModified() != before
    }

    override fun readState(slot: Int, file: File): Boolean {
        if (!NativeLibrary.IsRunning()) return false
        NativeLibrary.LoadStateAs(file.absolutePath)
        return true
    }

    override fun close() {
        // Core::Stop makes the native side call NativeLibrary.finishEmulationActivity() -> Activity.finish().
        try { NativeLibrary.StopEmulation() } catch (_: Throwable) {}
        // Safety net: if native shutdown wedges, tear the engine process down anyway.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!activity.isDestroyed) activity.finish()
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 1500)
        }, 4000)
    }

    override fun injectButton(pad: Pad, pressed: Boolean) {
        val i = when (pad) {
            Pad.A -> NebulosInput.A; Pad.B -> NebulosInput.B; Pad.X -> NebulosInput.X; Pad.Y -> NebulosInput.Y
            Pad.L1 -> NebulosInput.L1; Pad.R1 -> NebulosInput.R1; Pad.L2 -> NebulosInput.L2; Pad.R2 -> NebulosInput.R2
            Pad.L3 -> NebulosInput.L3; Pad.R3 -> NebulosInput.R3
            Pad.START -> NebulosInput.START; Pad.SELECT -> NebulosInput.SELECT
            Pad.DPAD_UP -> NebulosInput.UP; Pad.DPAD_DOWN -> NebulosInput.DOWN
            Pad.DPAD_LEFT -> NebulosInput.LEFT; Pad.DPAD_RIGHT -> NebulosInput.RIGHT
            else -> return
        }
        NebulosInput.set(i, if (pressed) 1.0 else 0.0)
    }

    override fun injectAxis(pad: Pad, value: Float) {
        val i = when (pad) {
            Pad.LS_UP -> NebulosInput.LS_UP; Pad.LS_DOWN -> NebulosInput.LS_DOWN
            Pad.LS_LEFT -> NebulosInput.LS_LEFT; Pad.LS_RIGHT -> NebulosInput.LS_RIGHT
            Pad.RS_UP -> NebulosInput.RS_UP; Pad.RS_DOWN -> NebulosInput.RS_DOWN
            Pad.RS_LEFT -> NebulosInput.RS_LEFT; Pad.RS_RIGHT -> NebulosInput.RS_RIGHT
            Pad.L2 -> NebulosInput.L_TRIGGER; Pad.R2 -> NebulosInput.R_TRIGGER
            else -> return
        }
        NebulosInput.set(i, value.coerceIn(0f, 1f).toDouble())
    }

    /** Release every virtual input (menu open, focus loss, pause). */
    fun releaseAll() {
        for (i in 0 until NebulosInput.COUNT) NebulosInput.set(i, 0.0)
    }
}
