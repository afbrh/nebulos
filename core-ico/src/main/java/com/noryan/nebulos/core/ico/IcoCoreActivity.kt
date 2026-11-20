package com.noryan.nebulos.core.ico

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.defnf.icopc.IcoActivity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSession
import com.noryan.recompsdl.RecompPad
import java.io.File

/**
 * The ICO game screen (`:ico` process). It IS ico-pc's activity (IcoActivity, SDL3 linked statically into libico_main.so, renamed Java
 * package); Nebulos owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical controller (router ->
 * [RecompSession] -> SDL virtual gamepad). The first-run disc picker is answered with the image Nebulos launched ([IcoActivity.sNebulosDisc]).
 * Buttons map by position like the other PS2 cores (the handheld's south button is the PS2 Cross).
 */
class IcoCoreActivity : IcoActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")   // only the Nebulos virtual pad may become a gamepad
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        val files = IcoSetup.filesDir(this)
        if (files != null) { IcoSetup.seedConfig(files); IcoSetup.linkSaves(this, files) }
        val iso = intent?.getStringExtra(IcoCore.EXTRA_ROM_PATH)
        if (iso != null && File(iso).isFile) IcoActivity.sNebulosDisc = iso   // a plain path: SDL_IOFromFile reads it in place (a file: URI would not open)
        super.onCreate(savedInstanceState)
        session = RecompSession(this, IcoCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.PS2, UiOptions(title = intent.getStringExtra("nebulos.title") ?: IcoCore.TITLE, loadingTimeoutMs = 45 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting ${IcoCore.TITLE}")
        h.setTargetFps(60)
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the native side starts. */
    override fun main() {
        runOnUiThread { hooks?.onGameReadyAfterPicture(settleMs = 1500) }
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

    private companion object { const val TAG = "IcoCoreActivity" }
}
