package com.noryan.nebulos.core.azahar

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.WindowCallbackWrapper
import androidx.drawerlayout.widget.DrawerLayout
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import org.citra.citra_emu.utils.NebulosBridge
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.activities.EmulationActivity
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.utils.SettingsFile
import org.citra.citra_emu.utils.DirectoryInitialization
import org.citra.citra_emu.utils.EmulationMenuSettings
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.utils.CitraDirectoryUtils
import java.io.File

/** Per-process (`:azahar`) glue between the stock Azahar EmulationActivity and the shared Nebulos menu/input. */
object AzaharHost {
    private val SYSTEM = System.N3DS
    @Volatile var closing = false
    private var session: AzaharSession? = null
    private var hooks: NebulosCoreHost.Hooks? = null
    private var backGuard: OnBackPressedCallback? = null

    // ---- per-game "4:3 Fill" (AzaharFourThree) ----
    private var gameKey = ""
    private var titleId: String? = null
    /** This game's 4:3 Fill choice (live: the Screen Layout row flips it and the emulator picks it up on the next draw). */
    @Volatile var fillOn = false
        private set
    private fun store(ctx: Context) = AzaharFourThree.prefsStore(ctx)
    /** This game's "4:3 Fill + PiP" choice (only used while [fillOn]). */
    @Volatile var pipOn = false
        private set
    fun setFill(ctx: Context, on: Boolean, pip: Boolean = false) {
        fillOn = on; pipOn = pip
        AzaharFourThree.setEnabled(store(ctx), gameKey, on)
        if (on) AzaharFourThree.setPipEnabled(store(ctx), gameKey, pip)
    }
    /** The layout in force for this game. */
    fun currentMode(ctx: Context) = AzaharFourThree.effectiveMode(fillOn, AzaharScreenLayouts.loadMode(ctx), pipOn)

    // ---- before Activity.onCreate: directory + layout configuration --------------------------------------------
    fun prepare(a: EmulationActivity) {
        val i = a.intent
        // User directory = Nebulos's own real folder (no SAF). Always overrides whatever a legacy install left in the
        // CITRA_DIRECTORY pref (an old SAF tree is simply ignored; its files are not migrated).
        val dir = i.getStringExtra(AzaharCore.EXTRA_USERDIR)?.let(::File) ?: AzaharCore.ensureUserDir(a)
        val want = Uri.fromFile(dir).toString()
        if (PermissionsHandler.citraDirectory.toString() != want) {
            PermissionsHandler.setCitraDirectory(want)
            DirectoryInitialization.resetCitraDirectoryState()
        }
        // A stale Lime3DS pref would make the stock activity think a manual directory update is needed.
        CitraDirectoryUtils.removeLimeDirectoryPreference()
        if (!DirectoryInitialization.areCitraDirectoriesReady()) DirectoryInitialization.start()
        if (!DirectoryInitialization.areCitraDirectoriesReady()) return // stock activity then bounces to its setup screen

        // The game surface is the only UI: no touch-button overlay, drawer permanently locked closed, no stats.
        EmulationMenuSettings.showOverlay = false
        EmulationMenuSettings.drawerLockMode = DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        EmulationMenuSettings.swapScreens = false

        titleId = i.getStringExtra(AzaharCore.EXTRA_TITLEID)
        gameKey = AzaharFourThree.gameKey(titleId, i.getStringExtra(AzaharCore.EXTRA_ROM) ?: "")
        val (sw, sh) = windowSize(a)
        fillOn = AzaharFourThree.isEnabled(store(a), gameKey, titleId, sw, sh)
        pipOn = fillOn && AzaharFourThree.isPipEnabled(store(a), gameKey, titleId, sw, sh)

        val first = a.getSharedPreferences("nebulos_azahar_state", Context.MODE_PRIVATE)
        if (!first.getBoolean("defaults_v1", false)) {
            // One-time performance defaults (same ones proven in the RomRunner embedding); user may change later.
            put(IntSetting.GRAPHICS_API, 2) // Vulkan (GL SIGSEGVs in OpenGL::Driver::ReportDriverInfo on the very first launch, before config.ini exists)
            put(IntSetting.RESOLUTION_FACTOR, 4)
            put(BooleanSetting.USE_FRAME_LIMIT, true)
            put(IntSetting.FRAME_LIMIT, 100)
            first.edit().putBoolean("defaults_v1", true).apply()
        }
        applyLayout(a, runCatching { AzaharCore.ScreenMode.valueOf(i.getStringExtra(AzaharCore.EXTRA_MODE)!!) }
            .getOrDefault(AzaharCore.ScreenMode.AUTO))
    }

    /** True when this launch uses the Nebulos custom screen layouts (a single display), so the Screen Layout row is offered. */
    @Volatile var customLayout = false
        private set

    private const val CUSTOM_LAYOUT = 5 // Settings::LayoutOption::CustomLayout on Android

    /** Window size for the layout maths: the game is forced to landscape, so the long side is the width. */
    private fun windowSize(ctx: Context): Pair<Int, Int> {
        val b = (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).maximumWindowMetrics.bounds
        return maxOf(b.width(), b.height()) to minOf(b.width(), b.height())
    }

    private fun writeScreenLayout(ctx: Context, mode: AzaharScreenLayouts.Mode, bottomFocus: Boolean) {
        val (w, h) = windowSize(ctx)
        val l = AzaharScreenLayouts.compute(mode, bottomFocus, w, h)
        // The emulator draws the top screen as a native 4:3 picture exactly while the 4:3 Fill layout is in force (with PiP: only while the top screen is the big one).
        runCatching { NativeLibrary.setFourThreeFill(AzaharScreenLayouts.usesNativeFourThree(mode, bottomFocus)) }
        put(IntSetting.SCREEN_LAYOUT, CUSTOM_LAYOUT)
        put(IntSetting.LANDSCAPE_TOP_X, l.topSlot.x); put(IntSetting.LANDSCAPE_TOP_Y, l.topSlot.y)
        put(IntSetting.LANDSCAPE_TOP_WIDTH, l.topSlot.width); put(IntSetting.LANDSCAPE_TOP_HEIGHT, l.topSlot.height)
        put(IntSetting.LANDSCAPE_BOTTOM_X, l.bottomSlot.x); put(IntSetting.LANDSCAPE_BOTTOM_Y, l.bottomSlot.y)
        put(IntSetting.LANDSCAPE_BOTTOM_WIDTH, l.bottomSlot.width); put(IntSetting.LANDSCAPE_BOTTOM_HEIGHT, l.bottomSlot.height)
        put(BooleanSetting.SWAP_SCREEN, bottomFocus)
        EmulationMenuSettings.swapScreens = bottomFocus
    }

    /** Menu row / L3: rewrite the rectangles and make the running emulator pick them up (same calls as Azahar's own layout cycling). */
    fun applyScreenLayout(a: Activity, mode: AzaharScreenLayouts.Mode, bottomFocus: Boolean) {
        if (!customLayout) return
        writeScreenLayout(a, mode, bottomFocus)
        NativeLibrary.reloadSettings()
        NativeLibrary.swapScreens(bottomFocus, a.windowManager.defaultDisplay.rotation)
        NativeLibrary.updateFramebuffer(false)
    }

    private fun put(s: IntSetting, v: Int) { s.int = v; SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, s) }
    private fun put(s: BooleanSetting, v: Boolean) { s.boolean = v; SettingsFile.saveFile(SettingsFile.FILE_NAME_CONFIG, s) }

    private fun applyLayout(ctx: Context, requested: AzaharCore.ScreenMode) {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val hasSecond = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()
        val mode = if (requested == AzaharCore.ScreenMode.AUTO)
            (if (hasSecond) AzaharCore.ScreenMode.DUAL_DISPLAY else AzaharCore.ScreenMode.LARGE_SCREEN) else requested
        val landscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        when (mode) {
            // AYN Thor etc.: top screen on the main display, bottom (touch) screen on the second display.
            AzaharCore.ScreenMode.DUAL_DISPLAY -> {
                put(IntSetting.SCREEN_LAYOUT, 1) // single screen (top)
                put(BooleanSetting.ENABLE_SECONDARY_DISPLAY, true)
                put(IntSetting.SECONDARY_DISPLAY_LAYOUT, 2) // bottom screen
                put(IntSetting.ORIENTATION_OPTION, landscape)
            }
            AzaharCore.ScreenMode.LARGE_SCREEN -> {
                // One display: Nebulos's own layouts (Screen Layout menu row, L3 swap) as exact custom rectangles.
                customLayout = true
                put(BooleanSetting.ENABLE_SECONDARY_DISPLAY, false); put(IntSetting.ORIENTATION_OPTION, landscape)
                writeScreenLayout(ctx, currentMode(ctx), false)
            }
            AzaharCore.ScreenMode.SIDE_BY_SIDE -> {
                put(IntSetting.SCREEN_LAYOUT, 3)
                put(BooleanSetting.ENABLE_SECONDARY_DISPLAY, false); put(IntSetting.ORIENTATION_OPTION, landscape)
            }
            else -> { // STACKED: portrait, top above bottom
                put(IntSetting.SCREEN_LAYOUT, 0); put(IntSetting.PORTRAIT_SCREEN_LAYOUT, 0)
                put(BooleanSetting.ENABLE_SECONDARY_DISPLAY, false)
                put(IntSetting.ORIENTATION_OPTION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            }
        }
    }

    // ---- after Activity.onCreate: Nebulos UI + menu + input ----------------------------------------------------------
    fun attach(a: EmulationActivity) {
        if (a.isFinishing) return
        closing = false
        val s = AzaharSession(a).also { session = it }
        // One call wires the router, the shared menu, hotkeys AND Nebulos's loading screen / notices / error panel.
        val h = NebulosCoreHost.install(a, s, SYSTEM).also { hooks = it }
        s.comboClock = { h.router.lastHotkeyAtMs }
        // Nebulos owns all non-game UI: Azahar's loading layout/toasts/dialogs are patched out (azahar-src, local only)
        // and report through NebulosBridge instead.
        NebulosBridge.notice = { h.notice(it) }
        NebulosBridge.error = { t, m, fatal -> h.error(t, m, fatal) }
        NebulosBridge.progress = { f, t -> h.setLoadingProgress(f, t) }
        NebulosBridge.loadingText = { h.setLoadingText(it) }
        NebulosBridge.ready = {
            h.onGameReady()
            // The emulator read config.ini before prepare() could rewrite the layout: re-apply it now that the game is running.
            a.runOnUiThread { runCatching { applyScreenLayout(a, currentMode(a), false) } }
        }
        NebulosBridge.flushPending()

        // Debug only: `adb shell run-as com.noryan.nebulos touch files/perflog` makes the emulator log FPS/speed once a second (tag RCPerf).
        if (File(a.filesDir, "perflog").exists()) Thread {
            while (!a.isFinishing) {
                runCatching {
                    val p = NativeLibrary.getPerfStats()
                    android.util.Log.i("RCPerf", String.format("sys=%.1f fps=%.1f speed=%.0f%% frame=%.1fms", p[0], p[1], p[2] * 100, p[3] * 1000))
                }
                Thread.sleep(1000)
            }
        }.start()

        // Everything the stock activity would do with keys/sticks goes through Nebulos first (contract rule).
        val orig = a.window.callback
        a.window.callback = object : WindowCallbackWrapper(orig) {
            override fun dispatchKeyEvent(e: KeyEvent): Boolean {
                if (h.dispatchKey(e)) return true
                if (e.keyCode == KeyEvent.KEYCODE_BACK) return true // never let the stock drawer open (host handles Back)
                // Stock activity would map raw gamepad keys itself; those must not double-fire.
                if (e.source and (android.view.InputDevice.SOURCE_GAMEPAD or android.view.InputDevice.SOURCE_DPAD) != 0) return true
                return super.dispatchKeyEvent(e)
            }

            override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
                if (h.dispatchMotion(e)) return true
                if (e.source and android.view.InputDevice.SOURCE_JOYSTICK != 0) return true
                return super.dispatchGenericMotionEvent(e)
            }
        }
    }

    /** Called after every resume: (a) gesture-back toggles the Nebulos menu, (b) keep the pause if the menu is open. */
    fun resumed(a: EmulationActivity) {
        if (backGuard == null) {
            backGuard = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { hooks?.menu?.let { if (it.isOpen) it.model.nav(com.noryan.nebulos.api.menu.MenuNav.BACK) else it.open() } } }
                .also { a.onBackPressedDispatcher.addCallback(a, it) } // added last => highest priority, beats the drawer
        }
        if (hooks?.menu?.isOpen == true) NativeLibrary.pauseEmulation()
    }

    fun detach(a: EmulationActivity) {
        hooks?.onDestroy(); hooks = null; session = null; backGuard = null
        NebulosBridge.notice = null; NebulosBridge.error = null; NebulosBridge.progress = null
        NebulosBridge.loadingText = null; NebulosBridge.ready = null
        if (a.isFinishing) {
            // Native/GL state of a finished Azahar session is not reusable; end the game process (as the prior art did).
            Thread {
                val end = android.os.SystemClock.uptimeMillis() + 3000
                while (NativeLibrary.isRunning() && android.os.SystemClock.uptimeMillis() < end) Thread.sleep(50)
                // isRunning() flips as soon as stop is requested; the game's save files are only closed/flushed once the
                // emulation thread has finished System::Shutdown. Never kill before that (portable save data).
                val flushEnd = android.os.SystemClock.uptimeMillis() + 5000
                while (!runCatching { NativeLibrary.isEmulationFinished() }.getOrDefault(true) &&
                    android.os.SystemClock.uptimeMillis() < flushEnd) Thread.sleep(50)
                Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 250)
            }.start()
        }
    }
}
