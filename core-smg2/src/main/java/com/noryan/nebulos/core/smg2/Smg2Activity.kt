package com.noryan.nebulos.core.smg2

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
import com.noryan.nebulos.recomp.RecompSaveData
import dev.smg2.aurora.AuroraSurface
import dev.smg2.sdl.SDLActivity
import dev.smg2.sdl.SDLSurface
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * The Super Mario Galaxy 2 game screen (`:smg2` process): SDL3's activity around Astral Italian's native library (Aurora/Dawn on Vulkan + the game). Nebulos owns the loading screen
 * (with the first-launch disc extraction progress), the Back menu, notices and errors ([NebulosCoreHost]) and the physical controller (router -> [Smg2Session] -> the SDL virtual gamepad in
 * libnebula_smg2.so). The game folder is `files/smg2` (internal) and `native-dumps/SB4E01` (the extracted disc); saves, settings and logs live in `_Nebulos/smg2-core`.
 *
 * Start order: SDL's thread asks [getArguments] before it starts the game; that call waits until [prepare] (own thread) has read the disc (the first time) and set the environment the native
 * entry reads. The loading screen shows that progress.
 */
class Smg2Activity : SDLActivity() {
    private lateinit var session: Smg2Session
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())
    private val ready = CountDownLatch(1)
    @Volatile private var failed = false

    override fun getLibraries(): Array<String> = arrayOf("nebula_smg2")

    // Aurora presents through its own surface, which is where the WebGPU swapchain attaches.
    override fun createSDLSurface(context: android.content.Context): SDLSurface = AuroraSurface(context)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        SaveRoot.adopt(intent)
        super.onCreate(savedInstanceState)
        if (mBrokenLibraries) return
        immersive()
        session = Smg2Session(this, Smg2Core.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.WII, UiOptions(title = intent.getStringExtra("nebulos.title") ?: Smg2Core.GAME_TITLE, loadingTimeoutMs = 15 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setTargetFps(60)
        h.setLoadingText("Starting Super Mario Galaxy 2")
        try { Smg2Pad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
        val rom = intent.getStringExtra(Smg2Core.EXTRA_ROM_PATH).orEmpty()
        Thread({ prepare(h, rom) }, "smg2-prepare").start()
    }

    /** Off the main thread: folders and environment -> (first launch) extraction of the disc -> release SDL's thread. */
    private fun prepare(h: NebulosCoreHost.Hooks, rom: String) {
        fun fail(msg: String) {
            failed = true
            ui.post { h.error(Smg2Core.GAME_TITLE, msg, fatal = true) }
            // SDL's thread stays parked in getArguments: the game never starts
        }
        try {
            val files = Smg2Files.prepareFolders(this)
            val game = Smg2Files.gameDir(this)
            if (!Smg2Files.isExtracted(game)) {
                val romFile = File(rom)
                if (!romFile.isFile) return fail("The disc image was not found: $rom")
                ui.post { h.setLoadingProgress(0f, "Checking the disc") }
                val problem = Smg2Native.nativeProbe(rom)
                if (problem.isNotEmpty()) return fail(problem)
                game.deleteRecursively()   // a half-finished earlier attempt: the importer needs an absent or empty folder
                game.parentFile?.mkdirs()
                val free = game.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < 3L * 1024 * 1024 * 1024) return fail("Not enough free space to read the disc (about 3 GB are needed, ${free / (1024 * 1024)} MB are free).")
                val t0 = android.os.SystemClock.elapsedRealtime()
                var lastSecond = -1L
                val result = Smg2Native.nativeImport(rom, game.absolutePath) { fraction, name ->
                    val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000
                    if (secs != lastSecond) {
                        lastSecond = secs
                        ui.post { h.setLoadingProgress(fraction.coerceIn(0f, 0.99f), "First launch: reading the disc (one time only), ${(fraction * 100).toInt()}%") }
                    }
                    !failed
                }
                if (result.isNotEmpty()) {
                    game.deleteRecursively()
                    return fail("Reading the disc failed: $result")
                }
                Smg2Files.markExtracted(game)
                Log.i(TAG, "disc extracted in ${(android.os.SystemClock.elapsedRealtime() - t0) / 1000}s")
            }
            env("PETARI_GAME_DIR", game.absolutePath)
            env("ASTRAL_SUPPORT_DIR", files.support.absolutePath)
            env("PETARI_CACHE_DIR", files.cache.absolutePath)
            ui.post { h.setLoadingText("Starting Super Mario Galaxy 2") }
            ready.countDown()
            ui.post { h.onGameReadyAfterPicture(settleMs = 1500) }
        } catch (t: Throwable) {
            Log.e(TAG, "prepare failed", t)
            fail("Could not prepare the game: ${t.message ?: t}")
        }
    }

    /** SDL's thread, before the game starts: waits for [prepare]. The native entry reads the game folder, support folder and cache folder from the environment. */
    override fun getArguments(): Array<String> {
        ready.await()
        return arrayOf()
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
        runCatching { Smg2Pad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The port owns global state and threads: a fresh process on each launch.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object { const val TAG = "Smg2Activity" }
}
