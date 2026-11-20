package com.noryan.nebulos.core.sunshine

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.system.Os
import java.io.File
import dev.chasem.sms.sdl.SDLActivity
import dev.chasem.sms.sdl.TouchController
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSaveData

/**
 * The Super Mario Sunshine game screen (`:sunshine` process): the PC port's SDL2 activity (what chasem-dev/sms-android's GameActivity does, minus its launcher). Nebulos owns
 * the loading screen, the Back menu, notices and errors ([NebulosCoreHost]) and the physical controller (router -> [SunshineSession] -> [TouchController], the port's own extra
 * SDL controller).
 */
class SunshineActivity : SDLActivity() {
    private lateinit var session: SunshineSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun getLibraries(): Array<String> = arrayOf("SDL2", "smsbootstrap")

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        if (mBrokenLibraries) return
        immersive()
        TouchController.install()
        session = SunshineSession(this, SunshineCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.GAMECUBE, UiOptions(title = intent.getStringExtra("nebulos.title") ?: SunshineCore.GAME_TITLE, loadingTimeoutMs = 10 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        session.start()
        h.setLoadingText("Starting Super Mario Sunshine")
        h.setTargetFps(60)
        ui.post { h.onGameReadyAfterPicture(settleMs = 1500) }
    }

    /** SDL's thread, before the game starts. */
    override fun getArguments(): Array<String> {
        val h = hooks
        val files = filesDir
        val data = File(files, "game").also { it.mkdirs() }
        val saves = File(files, "saves").also { it.mkdirs() }
        RecompSaveData.link(this, "sunshine", saves, sub = "card-a", linkName = "card-a")   // the memory card's files live in the _Nebulos folder
        val rom = intent.getStringExtra(SunshineCore.EXTRA_ROM_PATH).orEmpty()
        if (!File(rom).isFile) {
            ui.post { h?.error(SunshineCore.GAME_TITLE, "The disc image was not found: $rom", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        }
        val a = ArrayList<String>()
        a += listOf("--data", data.absolutePath, "--libraries", applicationInfo.nativeLibraryDir, "--disc", rom)
        a += listOf("--env", "SMS_SAVE_DIR=" + File(saves, "card-a").absolutePath)
        a += listOf("--env", "SMS_SETTINGS=" + File(data, "settings.txt").absolutePath)
        a += listOf("--env", "SMS_ANDROID_CONTROLLER_MAPPINGS=" + File(files, "controller-mappings.txt").absolutePath)
        a += listOf("--env", "SMS_BINDINGS=" + File(files, "controller-bindings.txt").absolutePath)
        a += listOf("--env", "SMS_ANDROID_STARTUP_POLICY=Nebulos")
        return a.toTypedArray()
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
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The game owns global heaps and threads: a fresh process on each launch (as the port's own launcher does).
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object { const val TAG = "SunshineActivity" }
}
