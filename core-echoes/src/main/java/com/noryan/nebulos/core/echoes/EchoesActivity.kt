package com.noryan.nebulos.core.echoes

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import org.metroidprime2.port.MetroidPrime2Activity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSaveData

/**
 * The Metroid Prime 2: Echoes game screen (`:echoes` process). It IS the port's own activity (SDL3 + Aurora/Dawn + the game); Nebulos owns the loading screen, the Back menu,
 * notices and errors through [NebulosCoreHost], and the physical controller (router -> [EchoesSession] -> the SDL virtual gamepad in libmetroid_prime2_port.so).
 * The port's own file picker is not used: `port_paths.txt` (which the port reads at start, and which the port's activity only writes when it is absent) names the user's disc
 * image in place, the saves folder in `_Nebulos` and the texture folder, before the port's `onCreate` runs.
 */
class EchoesActivity : MetroidPrime2Activity() {
    private lateinit var session: EchoesSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        val rom = intent.getStringExtra(EchoesCore.EXTRA_ROM_PATH).orEmpty()
        val problem = writePaths(rom)
        super.onCreate(savedInstanceState)
        immersive()
        session = EchoesSession(this, EchoesCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.GAMECUBE, UiOptions(title = intent.getStringExtra("nebulos.title") ?: EchoesCore.GAME_TITLE, loadingTimeoutMs = 10 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setTargetFps(60)
        if (problem != null) {
            h.error(EchoesCore.GAME_TITLE, problem, fatal = true)
            return
        }
        h.setLoadingText("Starting Metroid Prime 2: Echoes")
        try { EchoesPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
        ui.post { h.onGameReadyAfterPicture(settleMs = 1500) }
    }

    /** Writes `port_paths.txt` (read by the port's native entry); returns a message when the disc image is missing. */
    private fun writePaths(rom: String): String? {
        val files = filesDir
        val root = File(files, "echoes").also { it.mkdirs() }
        // the port's user folder (memory card, settings.cfg, controls, touch layout) lives in the _Nebulos folder
        RecompSaveData.link(this, "echoes", root, sub = "user", linkName = "user")
        // the quick-resume snapshot (user/savestates) is one folder at a fixed name: each disc gets its own
        com.noryan.nebulos.api.save.SaveRoot.adopt(intent)
        RecompSaveData.activatePerGameFolder(File(root, "user"), "savestates", com.noryan.nebulos.api.save.SaveRoot.gameKey(this, rom))
        File(root, "cache").mkdirs()
        val text = buildString {
            append("user=").append(File(root, "user").absolutePath).append('\n')
            append("cache=").append(File(root, "cache").absolutePath).append('\n')
            if (rom.isNotEmpty()) append("disc=").append(rom).append('\n')
            append("env.MP2_TEXTURES=").append(File(root, "textures").absolutePath).append('\n')
            // no SDL message boxes (the port offers to copy a save from another release of the same region, which Android shows as a dialog a controller cannot answer, and the copy crashed at start)
            append("env.MP2_NO_DISC_DIALOG=1\n")
        }
        runCatching { File(files, "port_paths.txt").writeText(text) }.onFailure { Log.e(TAG, "port_paths.txt: $it") }
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
        runCatching { EchoesPad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The port owns global state and threads: a fresh process on each launch.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object { const val TAG = "EchoesActivity" }
}
