package com.noryan.nebulos.core.jak

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.core.jak.sdl.SDLActivity

/**
 * The Jak and Daxter game screen (`:jak` process): SDL3's activity (GLES surface, audio via cubeb/AAudio in native code)
 * running OpenGOAL from libjak_main.so. Nebulos owns everything else (loading screen, Back menu, notices, errors).
 *
 * First launch of a game: [getArguments] runs on SDL's own thread BEFORE the engine starts, so the one-time disc -> game-data
 * import blocks right there while the Nebulos loading screen shows its progress; the surface/UI thread is never blocked.
 *
 * Input: the physical controller is consumed here (InputRouter -> [JakSession] -> SDL virtual gamepad); SDL never sees raw
 * device events and is told to ignore every real gamepad.
 */
class JakActivity : SDLActivity() {
    private lateinit var session: JakSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val game: String get() = intent?.getStringExtra(JakCore.EXTRA_GAME) ?: "jak1"

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareProcessEnvironment()
        super.onCreate(savedInstanceState)
        // Edge to edge: otherwise the game surface is shrunk by the (hidden) navigation bar's inset and the picture gets black borders.
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        session = JakSession(this)
        val title = intent.getStringExtra("nebulos.title") ?: JakCore.TITLES[game] ?: "Jak and Daxter"
        val h = NebulosCoreHost.install(this, session, System.PS2, UiOptions(title = title, loadingTimeoutMs = 45 * 60_000L))
        hooks = h
        h.setLoadingText("Starting $title")
        h.setTargetFps(60)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(android.view.WindowInsets.Type.systemBars())
            it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun prepareProcessEnvironment() {
        JakSaveData.prepare(this)
        fun env(k: String, v: String) = runCatching { Os.setenv(k, v, true) }.onFailure { Log.e(TAG, "setenv $k failed: $it") }
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
    }

    override fun getLibraries(): Array<String> = arrayOf("jak_main")

    /** Runs on SDL's thread before the engine starts: one-time game-data import (blocks), then the engine's argv. */
    override fun getArguments(): Array<String> {
        val g = game
        val iso = intent?.getStringExtra(JakCore.EXTRA_ROM_PATH)
        val err = JakData.ensureReady(this, g, iso, intent?.getStringArrayExtra(JakCore.EXTRA_TEXPACKS)?.toList()) { line -> hooks?.let { h -> runOnUiThread { h.setLoadingText(line) } } }
        runOnUiThread { hooks?.setLoadingText("Starting ${JakCore.TITLES[g] ?: "Jak and Daxter"}") }
        if (err != null) {
            Log.e(TAG, "game data not ready: $err")
            runOnUiThread { hooks?.error(JakCore.TITLES[g] ?: "Jak and Daxter", err, fatal = true) }
            while (true) Thread.sleep(60_000)   // the host closes the game (kills the process) when the panel is dismissed
        }
        JakData.forceAspect43(this, g)
        val proj = JakData.projDir(this).absolutePath
        return arrayOf("gk", "--proj-path", proj, "--config-path", "$proj/user", "--game", g, "--", "-boot", "-fakeiso")
    }

    /** Native -> host events (game thread): 0 = game is up, 1 = notice, 2 = fatal error. */
    fun onNebulosEvent(kind: Int, a: String?, b: String?) {
        Log.i(TAG, "native event kind=$kind a=$a b=$b")
        when (kind) {
            0 -> runOnUiThread { hooks?.onGameReady() }
            1 -> a?.let { t -> runOnUiThread { hooks?.notice(t) } }
            2 -> runOnUiThread { hooks?.error(a ?: "Jak and Daxter", b ?: "OpenGOAL stopped.", fatal = true) }
        }
    }

    internal fun notice(text: String) { hooks?.notice(text) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hooks?.dispatchKey(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true // Back belongs to the shared menu, never to the game
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)
        }
        return true // no raw key ever reaches SDL: controller input arrives through the virtual pad only
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        val src = ev.source
        if (src and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onPause() {
        hooks?.onPause()
        runCatching { JakNative.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        JakData.cancel()
        super.onDestroy()
        // The engine ran in this process: end it so every launch is a cold start (also when the game's own Exit was chosen).
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object { const val TAG = "JakActivity" }
}
