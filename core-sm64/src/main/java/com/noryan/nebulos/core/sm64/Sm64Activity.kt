package com.noryan.nebulos.core.sm64

import android.content.Intent
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
import com.noryan.nebulos.core.sm64.sdl.SDLActivity
import com.noryan.nebulos.recomp.RecompSaveData
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * The Super Mario 64 game screen (`:sm64` process): Ghostship's SDL2 activity. Nebulos owns the loading screen (with the first-launch extraction progress), the Back menu, notices and
 * errors ([NebulosCoreHost]) and the physical controller (router -> [Sm64Session] -> the SDL virtual controller). The game data folder is `files/sm64`; saves live in `_Nebulos/sm64-core`.
 *
 * Start order: SDL's thread asks [getArguments] before it starts the game; that call waits until [prepare] (own thread) has unpacked the bundled recipes, checked the ROM hash and, the first
 * time, run the Torch extraction in the `:sm64x` process. The loading screen shows that progress.
 */
class Sm64Activity : SDLActivity() {
    private lateinit var session: Sm64Session
    private var hooks: NebulosCoreHost.Hooks? = null
    private val ui = Handler(Looper.getMainLooper())
    private val ready = CountDownLatch(1)
    @Volatile private var failed = false

    override fun getLibraries(): Array<String> = arrayOf("SDL2_sm64", "sm64_main")

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a game controller: real controllers are consumed by Nebulos's InputRouter.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        env("SDL_ANDROID_TRAP_BACK_BUTTON", "1")
        env("SDL_IOS_ORIENTATIONS", "LandscapeLeft LandscapeRight")   // SDL would otherwise rotate the game with the device
        SaveRoot.adopt(intent)
        val data = Sm64Files.dataDir(this)
        val saves = RecompSaveData.link(this, "sm64", data, sub = "saves", linkName = "saves")   // the saves live in the _Nebulos folder
        env("GHOSTSHIP_DATA_DIR", data.absolutePath)
        env("GHOSTSHIP_SAVES_DIR", File(data, "saves").absolutePath)
        super.onCreate(savedInstanceState)
        if (mBrokenLibraries) return
        immersive()
        session = Sm64Session(this, Sm64Core.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.N64, UiOptions(title = intent.getStringExtra("nebulos.title") ?: Sm64Core.GAME_TITLE, loadingTimeoutMs = 15 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setTargetFps(60)
        h.setLoadingText("Starting Super Mario 64")
        try { Sm64Pad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
        val rom = intent.getStringExtra(Sm64Core.EXTRA_ROM_PATH).orEmpty()
        if (saves == null) Log.w(TAG, "saves link failed: saves stay in files/sm64/saves")
        Thread({ prepare(h, rom) }, "sm64-prepare").start()
    }

    /** Off the main thread: bundled files -> ROM check -> (first launch) extraction -> release SDL's thread. */
    private fun prepare(h: NebulosCoreHost.Hooks, rom: String) {
        fun fail(msg: String) {
            failed = true
            ui.post { h.error(Sm64Core.GAME_TITLE, msg, fatal = true) }
            // SDL's thread stays parked in getArguments: the game never starts
        }
        try {
            Sm64Files.stageBundled(this)?.let { return fail(it) }
            if (!Sm64Files.isExtracted(this)) {
                val romFile = File(rom)
                if (!romFile.isFile) return fail("The ROM was not found: $rom")
                ui.post { h.setLoadingProgress(0f, "Checking the ROM") }
                val sha = Sm64Files.sha1(romFile)
                if (sha !in Sm64Files.SUPPORTED_ROMS) {
                    return fail("That is not a supported Super Mario 64 ROM.\n\nSupported: " + Sm64Files.SUPPORTED_ROMS.values.joinToString(", ") + " (big-endian .z64).\n\nSHA-1 of this file: $sha")
                }
                val t0 = android.os.SystemClock.elapsedRealtime()
                val status = File(Sm64Files.dataDir(this), Sm64ExtractService.STATUS)
                status.delete()
                startService(Intent(this, Sm64ExtractService::class.java).putExtra(Sm64ExtractService.EXTRA_ROM, rom))
                var last = ""
                while (true) {
                    Thread.sleep(300)
                    val line = runCatching { status.readText().trim() }.getOrDefault("")
                    val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000
                    when {
                        line == "ok" -> { Log.i(TAG, "extraction finished in ${secs}s"); break }
                        line.startsWith("error") -> return fail("Converting the ROM failed: ${line.removePrefix("error").trim()}")
                        line.startsWith("run") && line != last -> {
                            last = line
                            val n = line.removePrefix("run").trim().toIntOrNull() ?: 0
                            ui.post { h.setLoadingProgress((n / EXTRACT_TOTAL).coerceIn(0f, 0.98f), "First launch: converting the ROM (one time only), ${secs}s") }
                        }
                        secs > 20 * 60 -> return fail("Converting the ROM did not finish.")
                    }
                    if (line.isEmpty() && secs > 60) return fail("The ROM converter did not start.")
                }
                if (!Sm64Files.isExtracted(this)) return fail("The ROM conversion produced no asset archive.")
            }
            ui.post { h.setLoadingText("Starting Super Mario 64") }
            ready.countDown()
            ui.post { h.onGameReadyAfterPicture(settleMs = 1500) }
        } catch (t: Throwable) {
            Log.e(TAG, "prepare failed", t)
            fail("Could not prepare the game: ${t.message ?: t}")
        }
    }

    /** SDL's thread, before the game starts: waits for [prepare]. The game reads its data folder and saves folder from the environment set in onCreate. */
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
        return true // no raw key ever reaches SDL: controller input arrives through the virtual controller only
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        if (ev.source and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    /** No touch input reaches the game (the engine's own touch overlay is off and a stray touch must not move anything). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = super.dispatchTouchEvent(ev)

    override fun onPause() {
        hooks?.onPause()
        runCatching { Sm64Pad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
        // The engine owns global state and threads: a fresh process on each launch.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private companion object {
        const val TAG = "Sm64Activity"
        /** Torch's asset counter at the end of an extraction is ~this (calibrated on the Nova); only drives the progress bar. */
        const val EXTRACT_TOTAL = 3800f
    }
}
