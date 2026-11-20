package com.noryan.nebulos.core.darkcloud

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.save.SaveRoot
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompSession
import com.noryan.recompsdl.RecompPad
import com.noryan.recompsdl.SDLActivity
import java.io.File

/**
 * The Dark Cloud game screen (`:darkcloud` process). It IS Chronicle's SDL3 activity (renamed Java package, SDL3 linked statically into
 * libchronicle_main.so); Nebulos owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical
 * controller (router -> [RecompSession] -> SDL virtual gamepad). Chronicle's own touch overlay and file picker are not used: the disc is the
 * path Nebulos launched with, and the native side reaches these methods by name (JNI), see port/src/platform/firstrun.cpp.
 */
class DarkCloudActivity : SDLActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null

    override fun getLibraries(): Array<String> = arrayOf("chronicle_main")   // SDL3 is inside it (static)

    /** One time: a config.json saved while the FPS counter was still on by default keeps it on; turn it off once (marker file). */
    private fun hideFpsOnce() {
        runCatching {
            val marker = File(filesDir, "darkcloud/.fps-reset2")
            if (marker.exists()) return
            val on = Regex("\"show_fps\"\\s*:\\s*true")
            for (root in listOfNotNull(SaveRoot.coreSubDir(this, "darkcloud", "save"), filesDir, getExternalFilesDir(null))) {   // config.json lives in the save folder
                root.walkTopDown().maxDepth(5).filter { it.isFile && it.name == "config.json" && it.length() < 262144 }.forEach { f ->
                    val t = f.readText()
                    if (on.containsMatchIn(t)) { f.writeText(on.replace(t, "\"show_fps\": false")); Log.i(TAG, "FPS counter off in ${f.path}") }
                }
            }
            marker.parentFile?.mkdirs(); marker.writeText("1")
        }.onFailure { Log.e(TAG, "hideFpsOnce failed: $it") }
    }

    override fun getArguments(): Array<String> {
        hideFpsOnce()
        val data = File(filesDir, "darkcloud/data").also { it.parentFile?.mkdirs() }
        val save = SaveRoot.coreSubDir(this, "darkcloud", "save")
        return arrayOf("--data", data.absolutePath, "--save", save.absolutePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")   // only the Nebulos virtual pad may become a gamepad
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        super.onCreate(savedInstanceState)
        session = RecompSession(this, DarkCloudCore.STATE_UNAVAILABLE_REASON, DarkCloudInput::button)
        val h = NebulosCoreHost.install(this, session, System.PS2, UiOptions(title = intent.getStringExtra("nebulos.title") ?: DarkCloudCore.TITLE, loadingTimeoutMs = 30 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting ${DarkCloudCore.TITLE}")
        h.setTargetFps(60)
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the native side starts. */
    override fun main() {
        runOnUiThread { hooks?.onGameReadyAfterPicture(settleMs = 1500) }
        super.main()
    }

    // --- methods the native side calls by name (first-run import) ---
    @Suppress("unused") fun showImportStatus(message: String) { runOnUiThread { hooks?.setLoadingText(message) } }
    @Suppress("unused") fun hideImportStatus() {}
    @Suppress("unused") fun showImportError(message: String) {
        runOnUiThread { hooks?.error(DarkCloudCore.TITLE, "The disc image could not be imported: $message", fatal = true) }
    }
    /** The native first-run flow asks for the disc: answer with the image Nebulos launched (a real path, read in place). */
    @Suppress("unused") fun requestDiscImage() {
        val iso = intent?.getStringExtra(DarkCloudCore.EXTRA_ROM_PATH)
        Thread({ nativeDiscImageResult(if (iso != null && File(iso).isFile) iso else null) }, "darkcloud-disc").start()
    }
    // Chronicle's own touch overlay does not exist here (Nebulos draws the touch controls): these only keep its native calls harmless.
    @Suppress("unused") fun setTouchControlsVisible(visible: Boolean) {}
    @Suppress("unused") fun setTouchControlsOpacity(opacity: Float) {}

    private external fun nativeDiscImageResult(path: String?)
    private external fun nativeCancelFirstRun()

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

    private companion object { const val TAG = "DarkCloudActivity" }
}
