package com.noryan.nebulos.core.totk

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import com.totkrecompandroid.TOTKRecompAndroidActivity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompGameFiles
import com.noryan.nebulos.recomp.RecompSaveData
import com.noryan.nebulos.recomp.RecompSession
import com.noryan.recompsdl.RecompPad

/**
 * The Tears of the Kingdom game screen (`:totk` process). It IS TOTKDroid's activity (SDL3 + the NVN->Vulkan runtime); Nebulos
 * owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical controller (router ->
 * [RecompSession] -> SDL virtual gamepad). The runtime's own start screen is skipped (LARECOMP_NO_LAUNCHER) and the game's code runs
 * natively on the CPU (LARECOMP_NATIVE=1): no game-code build on the device.
 */
class TotkActivity : TOTKRecompAndroidActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null

    /**
     * The GPU driver: TOTKDroid loads the first driver in `<external files>/drivers` (a .so or an adrenotools zip) through adrenotools.
     * Nebulos already bundles Eden's Turnip (v26.2.0-R8, Mesa main, MIT: the asset `eden_turnip_driver.zip`); it is the one build tested
     * here that has VK_KHR_map_memory2, which the runtime needs to map the game's memory as GPU memory (no per-draw CPU copies: ~12% more
     * fps in the open world and far fewer glitches than the CPU-copy path). The Adreno stock driver renders TOTK visibly wrong.
     */
    private fun installDriver() {
        runCatching {
            val dir = File(getExternalFilesDir(null) ?: return, "drivers").also { it.mkdirs() }
            val zip = File(dir, DRIVER_ZIP)
            val have = zip.length()
            val want = runCatching { assets.openFd(DRIVER_ASSET).use { it.length } }.getOrDefault(-1L)
            if (have != want) {
                dir.listFiles()?.forEach { if (it.isFile) it.delete() }
                assets.open(DRIVER_ASSET).use { i -> zip.outputStream().use { o -> i.copyTo(o) } }
            }
        }.onFailure { Log.e(TAG, "driver setup failed: $it") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installDriver()
        env("LARECOMP_NO_LAUNCHER", "1")
        env("LARECOMP_NATIVE", "1")
        // Only the Nebulos virtual pad (vendor 0x5243 / product 0x0001) may become a gamepad: real controllers are consumed by
        // Nebulos's InputRouter. HIDAPI and the phone-sensor "joystick" are off so nothing double-triggers.
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        super.onCreate(savedInstanceState)
        session = RecompSession(this, TotkCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.SWITCH, UiOptions(title = intent.getStringExtra("nebulos.title") ?: TotkCore.GAME_TITLE, loadingTimeoutMs = 60 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting Tears of the Kingdom")
        h.setTargetFps(30)
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the runtime starts: make sure the game files are there (first launch: dump them), set the data root. */
    override fun main() {
        val h = hooks
        try {
            val prepared = RecompGameFiles.ensure(
                this, "totk", TotkCore.TITLE_ID, intent.getStringExtra(TotkCore.EXTRA_ROM_PATH).orEmpty(), RecompGameFiles::totkFolder,
                TotkCore.GAME_TITLE,
            ) { f, text -> runOnUiThread { if (f.isNaN()) h?.setLoadingText(text) else h?.setLoadingProgress(f, text) } }
            importStandaloneSave(prepared.dataRoot)
            RecompSaveData.hideOverlayOnce(this, prepared.dataRoot)
            RecompSaveData.link(this, "totk", prepared.dataRoot)   // the game's saves live in the _Nebulos folder (symlinked into the data root)
            env("LARECOMP_DATA_ROOT", prepared.dataRoot.absolutePath)
            Log.i(TAG, "game ${prepared.displayVersion}, data root ${prepared.dataRoot}")
        } catch (f: RecompGameFiles.Failure) {
            Log.e(TAG, "prepare failed: ${f.message}")
            runOnUiThread { h?.error(f.title, f.message ?: "Could not prepare the game files.", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        } catch (t: Throwable) {
            Log.e(TAG, "prepare crashed", t)
            runOnUiThread { h?.error(TotkCore.GAME_TITLE, "Could not prepare the game files: $t", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        }
        runOnUiThread { h?.setLoadingText("Starting Tears of the Kingdom"); h?.onGameReadyAfterPicture(settleMs = 1500) }
        super.main()
    }

    /**
     * One-time import of a save made by the standalone TOTKDroid app: `<external files>/totk-import/save` (copied there with adb or a file
     * manager) becomes `<data root>/save`; a save the core already has is kept aside as `save.before-import`. The import folder is removed after.
     */
    private fun importStandaloneSave(dataRoot: File) {
        runCatching {
            val src = File(getExternalFilesDir(null), "totk-import/save")
            val done = File(dataRoot, ".standalone-save-imported")  // (a folder left by adb cannot be deleted by the app: never import twice)
            if (!src.isDirectory || done.exists()) return
            val dst = File(dataRoot, "save")
            if (dst.exists()) { val old = File(dataRoot, "save.before-import"); old.deleteRecursively(); dst.renameTo(old) }
            src.copyRecursively(dst, overwrite = true)
            done.writeText(java.lang.System.currentTimeMillis().toString())
            File(getExternalFilesDir(null), "totk-import").deleteRecursively()
            Log.i(TAG, "imported the standalone app's save into $dst")
        }.onFailure { Log.e(TAG, "save import failed: $it") }
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
        runCatching { RecompPad.nativeReleaseAll() }
        super.onPause()
    }

    override fun onDestroy() {
        hooks?.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TotkActivity"
        const val DRIVER_ASSET = "eden_turnip_driver.zip"
        const val DRIVER_ZIP = "Turnip_v26.2.0-R8_nebulos.zip"
    }
}
