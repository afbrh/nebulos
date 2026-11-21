package com.noryan.nebulos.core.prime1

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import org.metroidprime.port.MetroidPrimeActivity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSaveData

/**
 * The Metroid Prime game screen (`:prime1` process). It IS the port's own activity (SDL3 + Aurora/Dawn + the game); Nebulos owns the loading screen, the Back menu,
 * notices and errors through [NebulosCoreHost], and the physical controller (router -> [Prime1Session] -> the SDL virtual gamepad in libmetroid_prime_port.so).
 * The port's own file picker is not used. Unlike the Echoes port, this one has no `port_paths.txt`: its native entry reads environment variables (`MP_DISC` names the user's
 * disc image in place, `MP_USER_PATH` the saves/settings folder in `_Nebulos`, `MP_CACHE_PATH`, `MP_TEXTURES`, `MP_RESOURCES_PATH` the files copied by the port's activity), set here
 * with setenv before the port's `onCreate` loads the native library (an explicit MP_DISC beats the port's remembered disc path).
 */
class Prime1Activity : MetroidPrimeActivity() {
    private lateinit var session: Prime1Session
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        val rom = intent.getStringExtra(Prime1Core.EXTRA_ROM_PATH).orEmpty()
        val problem = setupPaths(rom)
        super.onCreate(savedInstanceState)
        immersive()
        session = Prime1Session(this, Prime1Core.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.GAMECUBE, UiOptions(title = intent.getStringExtra("nebulos.title") ?: Prime1Core.GAME_TITLE, loadingTimeoutMs = 10 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setTargetFps(60)
        if (problem != null) {
            h.error(Prime1Core.GAME_TITLE, problem, fatal = true)
            return
        }
        h.setLoadingText("Starting Metroid Prime")
        try { Prime1Pad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
        ui.post { h.onGameReadyAfterPicture(settleMs = 1500) }
    }

    /** Sets the port's MP_* environment (read by its native entry); returns a message when the disc image is missing. */
    private fun setupPaths(rom: String): String? {
        val root = File(filesDir, "prime1").also { it.mkdirs() }
        // the port's user folder (memory card USA/ EUR/, port_settings.ini, controller mappings, mods, save states) lives in the _Nebulos folder
        RecompSaveData.link(this, "prime1", root, sub = "user", linkName = "user")
        // the quick-resume snapshot (user/savestates) is one folder at a fixed name: each disc gets its own
        com.noryan.nebulos.api.save.SaveRoot.adopt(intent)
        RecompSaveData.activatePerGameFolder(File(root, "user"), "savestates", com.noryan.nebulos.api.save.SaveRoot.gameKey(this, rom))
        val cache = File(root, "cache").also { it.mkdirs() }
        env("MP_USER_PATH", File(root, "user").absolutePath + "/")
        env("MP_CACHE_PATH", cache.absolutePath + "/")
        env("MP_TEXTURES", File(root, "textures").absolutePath)
        env("MP_RESOURCES_PATH", root.absolutePath + "/")
        // no SDL message boxes (disc errors, the offer to copy a save from another release: a controller cannot answer them) and no update check (the author's repo is gone)
        env("MP_NO_DISC_DIALOG", "1")
        env("MP_UPDATE_CHECK", "0")
        if (rom.isNotEmpty()) env("MP_DISC", rom)
        return if (!File(rom).isFile) "The disc image was not found: $rom" else null
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
        return true // no raw key ever reaches SDL: controller input arrives through the virtual pad only
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        if (ev.source and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onPause() {
        hooks?.onPause()
        runCatching { Prime1Pad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The port owns global state and threads: a fresh process on each launch.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object { const val TAG = "Prime1Activity" }
}
