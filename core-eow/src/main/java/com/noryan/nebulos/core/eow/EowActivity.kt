package com.noryan.nebulos.core.eow

import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import com.smorecompandroid.SMORecompAndroidActivity
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.ui.UiOptions
import com.noryan.nebulos.recomp.RecompGameFiles
import com.noryan.nebulos.recomp.RecompSaveData
import com.noryan.nebulos.recomp.RecompSession
import com.noryan.recompsdl.RecompPad

/**
 * The Echoes of Wisdom game screen (`:eow` process). It IS the Odyssey runtime's activity (the same runtime, libsmo_main.so) (SDL3 + the NVN->Vulkan runtime); Nebulos
 * owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical controller (router ->
 * [RecompSession] -> SDL virtual gamepad). The runtime's own start screen is skipped (LARECOMP_NO_LAUNCHER) and the game's code runs
 * natively on the CPU (LARECOMP_NATIVE=1): no game-code build on the device.
 */
class EowActivity : SMORecompAndroidActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null

    /**
     * The GPU driver: SMO recomp loads the first driver in `<external files>/drivers` (a .so or an adrenotools zip) through adrenotools.
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
        // Debugging aid: KEY=VALUE lines in <external files>/runtime-env-eow.txt become environment variables of the runtime (LARECOMP_WATCHDOG=20, LARECOMP_LOG_FILES=1...)
        runCatching { File(getExternalFilesDir(null), "runtime-env-eow.txt").takeIf { it.isFile }?.readLines()?.forEach { l -> l.split("=", limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { env(it[0].trim(), it[1].trim()) } } }
        super.onCreate(savedInstanceState)
        session = RecompSession(this, EowCore.STATE_UNAVAILABLE_REASON)
        val h = NebulosCoreHost.install(this, session, System.SWITCH, UiOptions(title = intent.getStringExtra("nebulos.title") ?: EowCore.GAME_TITLE, loadingTimeoutMs = 60 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting Echoes of Wisdom")
        h.setTargetFps(60)
        // Debugging aid (runtime-env-eow.txt: NEBULOS_FORCE_READY=<ms>): drop the loading screen after that long whatever the picture looks like
        runCatching { Os.getenv("NEBULOS_FORCE_READY")?.toLongOrNull()?.let { ms -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ hooks?.onGameReady() }, ms) } }
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the runtime starts: make sure the game files are there (first launch: dump them), set the data root. */
    override fun main() {
        val h = hooks
        try {
            val prepared = RecompGameFiles.ensure(
                this, "eow", EowCore.TITLE_ID, intent.getStringExtra(EowCore.EXTRA_ROM_PATH).orEmpty(), { v -> if (v.trim() == "1.0.0") "" else null },
                EowCore.GAME_TITLE, dumper = EowXciDump,
            ) { f, text -> runOnUiThread { if (f.isNaN()) h?.setLoadingText(text) else h?.setLoadingProgress(f, text) } }
            RecompSaveData.hideOverlayOnce(this, prepared.dataRoot)
            aspectOriginalOnce(prepared.dataRoot)
            RecompSaveData.link(this, "eow", prepared.dataRoot)   // the game's saves live in the _Nebulos folder (symlinked into the data root)
            env("LARECOMP_DATA_ROOT", prepared.dataRoot.absolutePath)
            Log.i(TAG, "game ${prepared.displayVersion}, data root ${prepared.dataRoot}")
        } catch (f: RecompGameFiles.Failure) {
            Log.e(TAG, "prepare failed: ${f.message}")
            runOnUiThread { h?.error(f.title, f.message ?: "Could not prepare the game files.", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        } catch (t: Throwable) {
            Log.e(TAG, "prepare crashed", t)
            runOnUiThread { h?.error(EowCore.GAME_TITLE, "Could not prepare the game files: $t", fatal = true) }
            Thread.sleep(Long.MAX_VALUE)
        }
        // Echoes of Wisdom boots to a picture (the title movie), so the picture gate works; the first launch needs several minutes (extraction, shader builds) behind the loading screen.
        runOnUiThread { h?.setLoadingText("Starting Echoes of Wisdom"); h?.onGameReadyAfterPicture(settleMs = 1500) }
        super.main()
    }

    /**
     * One time: Echoes of Wisdom's picture is 16:9 and the Nova's screen is 4:3. The runtime's default "Fill (crop)" cuts the picture's sides,
     * so this core starts with "original" (the whole 16:9 picture with bars). A player who wants another choice changes it in the runtime's settings afterwards.
     */
    private fun aspectOriginalOnce(dataRoot: File) {
        runCatching {
            val marker = File(dataRoot, ".aspect-original")
            if (marker.exists()) return
            val ini = File(dataRoot, "smorecompandroid.ini")
            val line = "aspect = original"
            if (ini.isFile) {
                val t = ini.readText()
                ini.writeText(if (Regex("(?m)^aspect\\s*=").containsMatchIn(t)) Regex("(?m)^aspect\\s*=.*$").replace(t, line) else "$line\n$t")
            } else {
                dataRoot.mkdirs(); ini.writeText("$line\n")
            }
            marker.writeText("1")
        }
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
        const val TAG = "EowActivity"
        const val DRIVER_ASSET = "eden_turnip_driver.zip"
        const val DRIVER_ZIP = "Turnip_v26.2.0-R8_nebulos.zip"
    }
}
