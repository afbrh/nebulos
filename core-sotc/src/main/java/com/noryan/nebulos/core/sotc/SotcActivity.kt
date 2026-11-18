package com.noryan.nebulos.core.sotc

import android.app.NativeActivity
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The Shadow of the Colossus game screen (`:sotc` process): a NativeActivity running Celosia from libps2EntryRunner.so, with
 * three changes so that Nebulos can own the screen:
 *  - the window's own surface and input queue are given back to the view system, and a SurfaceView carries the picture; its Surface
 *    is handed to the native app glue ([SotcNative.nativeSurfaceCreated]) as NativeActivity would have handed over the window.
 *    Nebulos's loading screen / Back menu / notices are views above it.
 *  - the physical controller never reaches the native side: it is consumed here (InputRouter -> [SotcSession]) and injected as a PS2 pad.
 *  - the disc import runs here BEFORE the game starts: the runtime's main thread waits for [SotcNative.nativeDataReady].
 */
class SotcActivity : NativeActivity() {
    private lateinit var session: SotcSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private lateinit var surfaceView: SurfaceView
    private val surfaceThread = HandlerThread("sotc-surface").also { it.start() }
    private val surfaceHandler = Handler(surfaceThread.looper)
    private val lock = Any()
    private var holder: SurfaceHolder? = null
    private var dataReady = false
    private var surfaceDelivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareProcessEnvironment()
        SotcNative.listener = null   // touches SotcNative: loads libps2EntryRunner.so for JNI before the native side starts
        super.onCreate(savedInstanceState)   // starts the native side (it waits for the data)
        window.takeSurface(null)             // the picture goes through the SurfaceView below
        window.takeInputQueue(null)          // controller events come through the views, to Nebulos
        window.setFormat(PixelFormat.RGBA_8888)
        // Edge to edge: otherwise the surface is shrunk by the (hidden) navigation bar's inset and the picture gets black borders.
        window.setDecorFitsSystemWindows(false)
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }

        surfaceView = SurfaceView(this)
        surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { synchronized(lock) { holder = h }; deliverSurface() }
            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { withdrawSurface() }
        })
        addContentView(surfaceView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        session = SotcSession(this)
        val h = NebulosCoreHost.install(this, session, System.PS2, UiOptions(title = SotcCore.TITLE, loadingTimeoutMs = 20 * 60_000L))
        hooks = h
        h.setLoadingText("Starting ${SotcCore.TITLE}")
        h.setTargetFps(60)
        hideSystemBars()

        SotcNative.listener = { kind, _ -> if (kind == 0) runOnUiThread { hooks?.onGameReady() } }
        val iso = intent?.getStringExtra(SotcCore.EXTRA_ROM_PATH)
        thread(name = "sotc-import") {
            val err = SotcData.ensureReady(this, iso) { line -> runOnUiThread { hooks?.setLoadingText(line) } }
            if (err != null) {
                Log.e(TAG, "game data not ready: $err")
                runOnUiThread { hooks?.error(SotcCore.TITLE, err, fatal = true) }   // the host closes the game when the panel is dismissed
                return@thread
            }
            runOnUiThread { hooks?.setLoadingText("Starting ${SotcCore.TITLE}") }
            synchronized(lock) { dataReady = true }
            SotcNative.nativeDataReady(true)
            deliverSurface()
        }
    }

    /** Hands the SurfaceView's surface to the native app glue once the data is in place (raylib's InitWindow waits for it). */
    private fun deliverSurface() {
        val s = synchronized(lock) {
            if (!dataReady || surfaceDelivered) return
            val h = holder ?: return
            surfaceDelivered = true
            h.surface
        }
        surfaceHandler.post { runCatching { SotcNative.nativeSurfaceCreated(s) } }
    }

    private fun withdrawSurface() {
        synchronized(lock) {
            holder = null
            if (!surfaceDelivered) return
            surfaceDelivered = false
        }
        val done = CountDownLatch(1)
        surfaceHandler.post { runCatching { SotcNative.nativeSurfaceDestroyed() }; done.countDown() }
        done.await(1500, TimeUnit.MILLISECONDS)   // the surface is gone when this callback returns
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

    /** Before the native side starts (NativeActivity.onCreate loads the library and starts the game's thread). */
    private fun prepareProcessEnvironment() {
        fun env(k: String, v: String) = runCatching { Os.setenv(k, v, true) }.onFailure { Log.e(TAG, "setenv $k failed: $it") }
        env("PS2X_NEBULOS", "1")   // the runtime waits for the import and takes the pad from here
        env("PS2X_BOOT_ELF", File(SotcData.discDir(this), SotcData.BOOT_FILE).absolutePath)
        env("PS2X_MEMCARD_DIR", SotcSaveData.memcardDir(this).absolutePath)
        env("SOTC_NATIVE", "1")
    }

    internal fun notice(text: String) { hooks?.notice(text) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hooks?.dispatchKey(event) == true) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true // Back belongs to the shared menu, never to the game
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)
        }
        return true // controller input reaches the game only through the injected pad
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (hooks?.dispatchMotion(ev) == true) return true
        val src = ev.source
        if (src and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD) != 0) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onPause() {
        hooks?.onPause()
        runCatching { SotcNative.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        SotcData.cancel()
        SotcNative.listener = null
        super.onDestroy()
        // The runtime ran in this process: end it so every launch is a cold start (also when the game's own exit was chosen).
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object { private const val TAG = "SotcActivity" }
}
