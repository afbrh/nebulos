package com.noryan.nebulos.core.galaxy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import dev.nebula.galaxy.GameActivity
import dev.nebula.galaxy.NebulosBridge
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSaveData

/**
 * The Super Mario Galaxy game screen (`:galaxy` process). It IS Nebula's GameActivity (SDL3 + the Vulkan renderer + the Petari game); Nebulos owns the loading
 * screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical controller (router -> [GalaxySession] -> the SDL virtual gamepad in libmain.so).
 * Nebula's own launcher is not part of this core: [getArguments] (it runs on SDL's thread right before the game starts) sets the game up from the user's disc image the
 * first time, links the saves into the `_Nebulos` folder and then hands the normal arguments over.
 */
class GalaxyActivity : GameActivity() {
    private lateinit var session: GalaxySession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())

    /** Nebula's host is libnebula_main.so here (libmain.so is Dusklight's name in the same APK). */
    override fun getLibraries(): Array<String> = arrayOf("SDL3", "nebula_main")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        super.onCreate(savedInstanceState)
        immersive()
        session = GalaxySession(this, GalaxyCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.WII, UiOptions(title = intent.getStringExtra("nebulos.title") ?: GalaxyCore.GAME_TITLE, loadingTimeoutMs = 60 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting Super Mario Galaxy")
        h.setTargetFps(60)
        try { GalaxyPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** SDL's thread, before the game starts. */
    override fun getArguments(): Array<String> {
        val h = hooks
        try {
            prepare(h)
        } catch (f: Failure) {
            Log.e(TAG, "prepare failed: ${f.message}")
            ui.post { h?.error(GalaxyCore.GAME_TITLE, f.message ?: "Could not set the game up.", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        } catch (t: Throwable) {
            Log.e(TAG, "prepare crashed", t)
            ui.post { h?.error(GalaxyCore.GAME_TITLE, "Could not set the game up: $t", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        }
        ui.post { h?.setLoadingText("Starting Super Mario Galaxy"); h?.onGameReadyAfterPicture(settleMs = 1500) }
        return super.getArguments()
    }

    private class Failure(message: String) : Exception(message)

    private fun prepare(h: NebulosCoreHost.Hooks?) {
        val data = NebulosBridge.dataDir(this)
        RecompSaveData.link(this, "galaxy", data, sub = "saves", linkName = "saves")   // the game's saves live in the _Nebulos folder
        if (NebulosBridge.installed(this)) return
        val rom = File(intent.getStringExtra(GalaxyCore.EXTRA_ROM_PATH).orEmpty())
        if (!rom.isFile) throw Failure("The disc image was not found: $rom")
        val info = NebulosBridge.identify(rom) ?: throw Failure("The disc image could not be opened.")
        val id = Regex("\"gameId\"\\s*:\\s*\"([^\"]*)\"").find(info)?.groupValues?.get(1).orEmpty()
        val supported = Regex("\"supported\"\\s*:\\s*true").containsMatchIn(info)
        if (!supported || !GalaxyCore.isGalaxyGameId(id)) {
            val why = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(info)?.groupValues?.get(1).orEmpty()
            throw Failure("This disc image is not a Super Mario Galaxy Nebula can use (revision 0 of the North America, Europe or Japan release).${if (why.isNotEmpty()) " $why" else ""}")
        }
        ui.post { h?.setLoadingProgress(0f, "Setting up Super Mario Galaxy from your disc (first launch only)") }
        var last = 0L
        val started = android.os.SystemClock.elapsedRealtime()
        val error = NebulosBridge.install(this, rom, id) { done, total ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - last >= 250 || done == total) {
                last = now
                val f = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                val mb = done / (1 shl 20)
                val secs = (now - started) / 1000.0
                val rate = if (secs > 1) " (${(mb / secs).toInt()} MB/s)" else ""
                ui.post { h?.setLoadingProgress(f, "Setting up Super Mario Galaxy: ${(f * 100).toInt()}%$rate") }
            }
            true
        }
        if (error != null) throw Failure("Setup from the disc failed: $error")
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
        runCatching { GalaxyPad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
    }

    private companion object { const val TAG = "GalaxyActivity" }
}
