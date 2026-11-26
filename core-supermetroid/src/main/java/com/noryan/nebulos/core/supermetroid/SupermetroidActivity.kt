package com.noryan.nebulos.core.supermetroid

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.save.SaveRoot
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.core.supermetroid.sdl.SDLActivity
import com.noryan.nebulos.recomp.RecompSaveData
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * The Super Metroid game screen (`:supermetroid` process): the port's SDL2 activity. Nebulos owns the loading screen, the Back menu, notices and errors
 * ([NebulosCoreHost]) and the physical controller (router -> [SupermetroidSession] -> the SDL virtual controller). The game folder is `files/supermetroid`; its
 * `saves` folder (the cartridge save) lives in `_Nebulos/supermetroid-core`.
 *
 * Start order: SDL's thread asks [getArguments] before it starts the game; that call waits until [prepare] (own thread) has written the settings and,
 * the first time, copied the checked ROM into the game folder.
 */
class SupermetroidActivity : SDLActivity() {
    private lateinit var session: SupermetroidSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())
    private val ready = CountDownLatch(1)

    /** SDL2 is part of the game's library. */
    override fun getLibraries(): Array<String> = arrayOf("supermetroid_main")

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a game controller: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        env("SDL_ANDROID_TRAP_BACK_BUTTON", "1")
        env("SDL_IOS_ORIENTATIONS", "LandscapeLeft LandscapeRight")   // SDL would otherwise rotate the game with the device
        SaveRoot.adopt(intent)
        val data = SupermetroidFiles.dataDir(this)
        val saves = RecompSaveData.link(this, "supermetroid", data, sub = "saves", linkName = "saves")
        if (saves == null) Log.w(TAG, "saves link failed: saves stay in files/supermetroid/saves")
        env("SM_DATA_DIR", data.absolutePath)
        super.onCreate(savedInstanceState)
        if (mBrokenLibraries) return
        immersive()
        val rom = intent.getStringExtra(SupermetroidCore.EXTRA_ROM_PATH).orEmpty()
        session = SupermetroidSession(this, applicationContext, rom)
        val h = NebulosCoreHost.install(this, session, System.SNES, UiOptions(title = intent.getStringExtra("nebulos.title") ?: SupermetroidCore.GAME_TITLE))
        hooks = h
        h.setTargetFps(60)
        h.setLoadingText("Starting Super Metroid")
        try { SmNative.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
        Thread({ prepare(h, rom) }, "supermetroid-prepare").start()
    }

    /** Off the main thread: settings -> (first launch) the ROM into the game folder -> release SDL's thread. */
    private fun prepare(h: NebulosCoreHost.Hooks, rom: String) {
        // on failure SDL's thread stays parked in getArguments: the game never starts
        fun fail(msg: String) { ui.post { h.error(SupermetroidCore.GAME_TITLE, msg, fatal = true) } }
        try {
            SupermetroidFiles.ensureSettings(this)
            if (!SupermetroidFiles.hasRom(this)) SupermetroidFiles.copyRom(this, File(rom))?.let { return fail(it) }
            ui.post { h.setLoadingText("Starting Super Metroid") }
            ready.countDown()
            ui.post { h.onGameReadyAfterPicture(settleMs = 300) }
        } catch (t: Throwable) {
            Log.e(TAG, "prepare failed", t)
            fail("Could not prepare the game: ${t.message ?: t}")
        }
    }

    /** SDL's thread, before the game starts: waits for [prepare]. */
    override fun getArguments(): Array<String> {
        ready.await()
        return arrayOf("--config", File(SupermetroidFiles.dataDir(this), SupermetroidFiles.INI).absolutePath)
    }

    /** The system bars away (the game uses the whole screen); a swipe from the edge shows them for a moment. */
    private fun immersive() {
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun env(k: String, v: String) { runCatching { Os.setenv(k, v, true) }.onFailure { Log.e(TAG, "setenv $k failed: $it") } }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hooks?.dispatchKey(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)
        }
        return true // no raw key ever reaches SDL: controller input arrives through the virtual controller only
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        if (ev.source and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onPause() {
        hooks?.onPause()
        if (::session.isInitialized) session.releaseAll()
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The game owns global state: a fresh process on each launch.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object {
        const val TAG = "SupermetroidActivity"
    }
}
