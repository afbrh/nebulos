package com.noryan.nebulos.core.darkchronicle

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
 * The Dark Chronicle game screen (`:darkchronicle` process). It IS ChronicleTwo's SDL3 activity (renamed Java package, SDL3 linked statically into
 * libdarkchronicle_main.so). Nebulos owns the loading screen, the Back menu, notices and errors through [NebulosCoreHost], and the physical
 * controller (router -> [RecompSession] -> SDL virtual gamepad).
 *
 * First launch (on SDL's thread, before the native game starts): the port's runtime files (Luau scripts, glyphs, fonts) are copied out of the APK
 * into the run directory, then the port's extractor converts the user's own disc image (the path Nebulos launched with, read in place) into
 * `<run dir>/data.zip`. A stamp (disc path, size, date, core version) makes later launches skip both.
 */
class DarkChronicleActivity : SDLActivity() {
    private lateinit var session: RecompSession
    private var hooks: NebulosCoreHost.Hooks? = null
    private val runDir: File get() = File(filesDir, "darkchronicle")

    override fun getLibraries(): Array<String> = arrayOf("darkchronicle_main")   // SDL3 is inside it (static)

    /**
     * The first run writes a config.json with the desktop defaults (FPS counter on, no frame cap): once, turn the counter off and cap at 60.
     * The marker is only written after such a file was seen, so a first launch (no config yet) tries again on the next one.
     */
    private fun androidDefaultsOnce() {
        runCatching {
            val marker = File(runDir, ".android-defaults1")
            if (marker.exists()) return
            val fpsOn = Regex("\"show_fps\"\\s*:\\s*true")
            val noCap = Regex("\"max_fps\"\\s*:\\s*0(\\.0*)?(?![0-9.])")
            var seen = false
            val save = SaveRoot.coreSubDir(this, "darkchronicle", "save")
            for (root in listOfNotNull(save.parentFile, runDir)) {   // config.json sits beside the save folder
                root.walkTopDown().maxDepth(4).filter { it.isFile && it.name == "config.json" && it.length() < 262144 }.forEach { f ->
                    seen = true
                    val t = f.readText()
                    val r = noCap.replace(fpsOn.replace(t, "\"show_fps\": false"), "\"max_fps\": 60.0")
                    if (r != t) { f.writeText(r); Log.i(TAG, "Android defaults applied in ${f.path}") }
                }
            }
            if (seen) { runDir.mkdirs(); marker.writeText("1") }
        }.onFailure { Log.e(TAG, "androidDefaultsOnce failed: $it") }
    }

    override fun getArguments(): Array<String> {
        androidDefaultsOnce()
        runDir.mkdirs()
        val save = SaveRoot.coreSubDir(this, "darkchronicle", "save")
        return arrayOf("--run-dir", runDir.absolutePath, "--save", save.absolutePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        env("SDL_GAMECONTROLLER_IGNORE_DEVICES_EXCEPT", "0x5243/0x0001")   // only the Nebulos virtual pad may become a gamepad
        env("SDL_JOYSTICK_HIDAPI", "0")
        env("SDL_ACCELEROMETER_AS_JOYSTICK", "0")
        env("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "1")   // the Nebulos overlay can hold window focus: keep the virtual pad live
        env("KZ_ANDROID_EXECUTABLE", File(runDir, "darkchronicle").absolutePath)   // scripts/, glyphs/ and fonts/ are looked up beside it
        super.onCreate(savedInstanceState)
        session = RecompSession(this, DarkChronicleCore.STATE_UNAVAILABLE_REASON, DarkChronicleInput::button)
        val h = NebulosCoreHost.install(this, session, System.PS2, UiOptions(title = intent.getStringExtra("nebulos.title") ?: DarkChronicleCore.TITLE, loadingTimeoutMs = 60 * 60_000L))
        hooks = h
        session.onNotice = { h.notice(it) }
        h.setLoadingText("Starting ${DarkChronicleCore.TITLE}")
        h.setTargetFps(60)
        try { RecompPad.nativeStart() } catch (t: Throwable) { Log.e(TAG, "virtual pad: $t") }
    }

    /** Runs on SDL's thread right before the native side starts. */
    override fun main() {
        if (!prepare()) return
        runOnUiThread { hooks?.onGameReadyAfterPicture(settleMs = 1500) }
        super.main()
    }

    /** Runtime files and disc data; false (after showing the error) when the game cannot start. */
    private fun prepare(): Boolean {
        runCatching { copyAssets("darkchronicle", runDir) }.onFailure {
            Log.e(TAG, "assets: $it"); fail("The game's runtime files could not be set up: ${it.message}"); return false
        }
        val iso = intent?.getStringExtra(DarkChronicleCore.EXTRA_ROM_PATH)?.let(::File)
        val data = File(runDir, "data")
        val zip = File(runDir, "data.zip")
        val stamp = File(runDir, ".extracted")
        val want = iso?.let { "${it.absolutePath}|${it.length()}|${it.lastModified()}|${BuildConfig.VERSION_NAME}" }
        if (zip.isFile && stamp.isFile && want != null && stamp.readText() == want) return true
        if (iso == null || !iso.isFile) {
            // no disc this time: an earlier extraction (no stamp match needed) still runs
            if (zip.isFile) return true
            fail("The disc image was not found. Dark Chronicle needs your own PAL disc image (SCES_511.90).")
            return false
        }
        runOnUiThread { hooks?.setLoadingText("Preparing ${DarkChronicleCore.TITLE} from your disc (first launch only, several minutes)") }
        zip.delete(); File(runDir, "data.json").delete(); stamp.delete()
        // the extractor wants the build's gameplay seed beside the game root as data.zip (it publishes the finished archive over it)
        runCatching { copyTree("darkchronicle-seed/data.zip", zip) }.onFailure { Log.e(TAG, "seed: $it"); fail("The game's gameplay data is missing from this build."); return false }
        val err = try { nativeExtract(iso.absolutePath, data.absolutePath) } catch (t: Throwable) { "extractor unavailable: $t" }
        if (err != null) {
            Log.e(TAG, "extract: $err"); fail("The disc image could not be converted: $err"); return false
        }
        stamp.writeText(want!!)
        return true
    }

    private fun fail(message: String) = runOnUiThread { hooks?.error(DarkChronicleCore.TITLE, message, fatal = true) }

    /** Copies an APK asset folder into [dest] unless an identical build already did (marker file holds the core version). */
    private fun copyAssets(assetDir: String, dest: File) {
        val marker = File(dest, ".assets")
        if (marker.isFile && marker.readText() == BuildConfig.VERSION_NAME + assetsStamp()) return
        copyTree(assetDir, dest)
        dest.mkdirs(); marker.writeText(BuildConfig.VERSION_NAME + assetsStamp())
    }
    private fun assetsStamp(): String = packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString()
    private fun copyTree(assetPath: String, dest: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {   // a file
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dest.mkdirs()
            for (c in children) copyTree("$assetPath/$c", File(dest, c))
        }
    }

    /** Returns null on success, else the error text. */
    private external fun nativeExtract(iso: String, dataDir: String): String?

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

    private companion object { const val TAG = "DarkChronicleActivity" }
}
