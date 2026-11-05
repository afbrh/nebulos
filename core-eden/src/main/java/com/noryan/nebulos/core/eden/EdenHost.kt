package com.noryan.nebulos.core.eden

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.view.WindowCallbackWrapper
import com.noryan.nebulos.api.NebulosCoreHost
import com.noryan.nebulos.api.System
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.utils.NebulosBridge

/**
 * Integrates the shared Nebulos input router + in-game menu with Eden's game Activity WITHOUT patching Eden
 * (EmulationActivity is a final class): the Activity's Window.Callback is wrapped so [NebulosCoreHost] sees every
 * key/motion event FIRST (contract rule). Events it does not consume fall through to Eden's own dispatch.
 */
object EdenHost {
    private const val PICTURE_WAIT_MS = 60_000L
    private const val MAX_COPY_FAILURES = 20
    /** Of the 64x36 samples, how many must be non-black before it counts as picture (~6%: a lone corner spinner on black does not). */
    private const val PICTURE_MIN_LIT = 140
    @Volatile var session: EdenSession? = null
        private set
    private var hooks: NebulosCoreHost.Hooks? = null

    fun attach(activity: EmulationActivity) {
        val s = EdenSession(activity).also { session = it }
        val h = NebulosCoreHost.install(activity, s, System.SWITCH).also { hooks = it }
        // Nebulos owns all non-game UI: Eden's loading card/toasts/dialogs are patched out (eden-src, local only) and
        // report through NebulosBridge instead.
        NebulosBridge.notice = { h.notice(it) }
        NebulosBridge.error = { t, m, fatal -> h.error(t, m, fatal) }
        NebulosBridge.progress = { f, t -> h.setLoadingProgress(f, t) }
        NebulosBridge.loadingText = { h.setLoadingText(it) }
        NebulosBridge.ready = { holdUntilPicture(activity, h) }
        NebulosBridge.flushPending()
        EdenDriver.pendingNotice?.let { h.notice(it); EdenDriver.pendingNotice = null }
        val window = activity.window
        val original: Window.Callback = window.callback
        window.callback = object : WindowCallbackWrapper(original) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                h.dispatchKey(event) || super.dispatchKeyEvent(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
                h.dispatchMotion(event) || super.dispatchGenericMotionEvent(event)
        }
        watchGameSurface(activity)
        // Eden's own Back handling (drawer) never runs: key Back is consumed by hooks.dispatchKey above and the
        // predictive/gesture back is taken by the PRIORITY_OVERLAY callback NebulosMenu.attach registers.
    }

    /**
     * Eden reports "ready" when its first frame is presented, but a Switch game then often draws pure black for many more seconds while its own
     * startup runs. Keep Nebulos's loading screen up until the game surface shows real picture (a few percent of the screen non-black, so a lone spinner on black doesn't count), checked ~3x a second
     * with a small PixelCopy of the game surface. Falls back to "ready now" if the surface can't be sampled, and gives up waiting after [PICTURE_WAIT_MS].
     */
    private fun holdUntilPicture(activity: EmulationActivity, h: NebulosCoreHost.Hooks) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val t0 = android.os.SystemClock.uptimeMillis()
        val bmp = android.graphics.Bitmap.createBitmap(64, 36, android.graphics.Bitmap.Config.ARGB_8888)
        val px = IntArray(64 * 36)
        var failures = 0
        fun done(why: String) { android.util.Log.i("NebulosEden", "picture wait over after ${android.os.SystemClock.uptimeMillis() - t0}ms: $why"); if (hooks === h) h.onGameReady() }
        fun poll() {
            if (activity.isDestroyed || activity.isFinishing || hooks !== h) return
            h.setLoadingText("Starting game")   // every round: Eden's own late progress calls would put "Building shaders" back
            val view = findSurfaceView(activity.window.decorView)
            if (android.os.SystemClock.uptimeMillis() - t0 > PICTURE_WAIT_MS || view == null || !view.holder.surface.isValid) { done("timeout/no surface"); return }
            try {
                android.view.PixelCopy.request(view, bmp, { result ->
                    if (result != android.view.PixelCopy.SUCCESS) {
                        // No buffer yet / surface busy: try again; only give up when sampling keeps failing (the game then just shows as before).
                        if (++failures > MAX_COPY_FAILURES) done("pixelcopy keeps failing, result $result") else main.postDelayed(::poll, 300)
                        return@request
                    }
                    failures = 0
                    bmp.getPixels(px, 0, 64, 0, 0, 64, 36)
                    val lit = px.count { c -> maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) > 24 }
                    if (lit >= PICTURE_MIN_LIT) done("picture ($lit lit)") else main.postDelayed(::poll, 300)
                }, main)
            } catch (t: Throwable) { done("pixelcopy threw $t") }
        }
        android.util.Log.i("NebulosEden", "ready from Eden: waiting for picture")
        poll()
    }

    /** Called from the lifecycle hook when the Activity pauses/destroys. */
    fun onPause() { hooks?.onPause(); session?.onAway() }

    /** Called from the lifecycle hook when the Activity resumes. */
    fun onResume() { session?.onBack(hooks?.menu?.isOpen == true) }

    /**
     * Watches the game SurfaceView (Eden's fragment inflates it after the Activity is created, so look for it until it exists)
     * to learn when its surface is destroyed and when a new one arrives.
     */
    private fun watchGameSurface(activity: EmulationActivity, tries: Int = 100) {
        val view = findSurfaceView(activity.window.decorView)
        if (view == null) {
            if (tries > 0) activity.window.decorView.postDelayed({ if (!activity.isDestroyed) watchGameSurface(activity, tries - 1) }, 100)
            return
        }
        // Switch games are 16:9: on a taller (more square) screen Eden centres the picture with a bar above and below. Pin the picture
        // to the top edge instead, like the GBA picture, by sliding the surface up by the top bar's height (all the spare room is then
        // below the picture).
        val title = activity.intent?.getStringExtra("nebulos.title").orEmpty()
        val fill = EdenMods.fillsScreen(activity, title)
        fun hugTop() {
            val pic = view.width * 9 / 16
            val bar = (view.height - pic) / 2
            if (fill && pic > 0 && bar > 2) {
                // Stretch the 16:9 picture (drawn centred, bars above and below) over the whole surface height.
                val s = view.height.toFloat() / pic
                view.pivotX = view.width / 2f; view.pivotY = 0f
                view.scaleY = s; view.translationY = -bar * s
            } else {
                view.scaleY = 1f
                view.translationY = if (bar > 2) -bar.toFloat() else 0f
            }
        }
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> hugTop() }
        hugTop()
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                session?.onSurfaceBack(holder.surface, hooks?.menu?.isOpen == true)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) { session?.onSurfaceLost() }
        })
    }

    private fun findSurfaceView(v: View): SurfaceView? {
        if (v is SurfaceView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findSurfaceView(v.getChildAt(i))?.let { return it }
        return null
    }

    fun detach(@Suppress("UNUSED_PARAMETER") activity: EmulationActivity) {
        hooks?.onDestroy(); hooks = null; session = null
        NebulosBridge.notice = null; NebulosBridge.error = null; NebulosBridge.progress = null
        NebulosBridge.loadingText = null; NebulosBridge.ready = null
    }
}
