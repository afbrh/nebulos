package com.noryan.nebulos.core.botw

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.botwrecompandroid.BOTWRecompAndroidActivity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompGameFiles
import com.noryan.nebulos.recomp.RecompSession
import com.noryan.recompsdl.RecompPad

/**
 * The Breath of the Wild game screen (`:botw` process). It IS BOTWRecompAndroid's activity (SDL3 + the NVN->Vulkan runtime); Nebulos
 * owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical controller (router ->
 * [RecompSession] -> SDL virtual gamepad). The runtime's own start screen is skipped (LARECOMP_NO_LAUNCHER) and the game's code runs
 * natively on the CPU (LARECOMP_NATIVE=1): no game-code build on the device.
 */
class BotwActivity : BOTWRecompAndroidActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        env("LARECOMP_NO_LAUNCHER", "1")
        env("LARECOMP_NATIVE", "1")
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by
        // Nebulos's InputRouter. HIDAPI and the phone-sensor "joystick" are off so nothing double-triggers.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        super.onCreate(savedInstanceState)
        session = RecompSession(this, BotwCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.SWITCH, UiOptions(title = intent.getStringExtra("nebulos.title") ?: BotwCore.GAME_TITLE, loadingTimeoutMs = 60 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting Breath of the Wild")
        h.setTargetFps(30)
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the runtime starts: make sure the game files are there (first launch: dump them), set the data root. */
    override fun main() {
        val h = hooks
        try {
            val prepared = RecompGameFiles.ensure(
                this, "botw", BotwCore.TITLE_ID, intent.getStringExtra(BotwCore.EXTRA_ROM_PATH).orEmpty(), RecompGameFiles::botwFolder,
                BotwCore.GAME_TITLE,
            ) { f, text -> runOnUiThread { if (f.isNaN()) h?.setLoadingText(text) else h?.setLoadingProgress(f, text) } }
            env("LARECOMP_DATA_ROOT", prepared.dataRoot.absolutePath)
            Log.i(TAG, "game ${prepared.displayVersion}, data root ${prepared.dataRoot}")
        } catch (f: RecompGameFiles.Failure) {
            Log.e(TAG, "prepare failed: ${f.message}")
            runOnUiThread { h?.error(f.title, f.message ?: "Could not prepare the game files.", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        } catch (t: Throwable) {
            Log.e(TAG, "prepare crashed", t)
            runOnUiThread { h?.error(BotwCore.GAME_TITLE, "Could not prepare the game files: $t", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        }
        runOnUiThread { h?.setLoadingText("Starting Breath of the Wild"); h?.onGameReadyAfterPicture(settleMs = 1500) }
        super.main()
    }

    private fun env(k: String, v: String) { runCatching { Os.setenv(k, v, true) }.onFailure { Log.e(TAG, "setenv $k failed: $it") } }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hooks?.dispatchKey(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)
        }
        return true // no raw key ever reaches SDL: controller input arrives through the virtual pad only
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        if (ev.source and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onPause() {
        hooks?.onPause()
        runCatching { RecompPad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
    }

    private companion object { const val TAG = "BotwActivity" }
}
