package com.noryan.nebulos.api

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

/**
 * Holds Nebulos's loading screen until the game's SurfaceView shows real picture.
 *
 * Most engines report "ready" when their first frame is presented, but a game often draws pure black for many more seconds while its own
 * start-up runs (Switch games especially), and some engines hitch hard in the first seconds of play. A core calls [hold] instead of
 * `hooks.onGameReady()`: the loading screen stays until a few percent of the surface is non-black (a lone spinner on black does not
 * count), then for [settleMs] longer (so a known start-up hitch happens behind the loading screen), then the screen fades out. The picture is
 * checked about three times a second with a small PixelCopy. If the surface cannot be sampled, or [maxWaitMs] passes, it releases anyway.
 */
object PictureGate {
    private const val TAG = "NebulosUi"
    private const val POLL_MS = 300L
    private const val W = 64
    private const val H = 36
    /**
     * Of the 64x36 samples, this many must be non-black (~2.5%) before it counts as picture: more than a lone corner spinner on black
     * (Animal Crossing's is ~0.7%), but a small first-run dialog counts, so the player is never kept from seeing something to act on.
     */
    const val MIN_LIT = 58

    /**
     * Stricter (~6%): for engines whose start-up passes through small bright screens (logos, spinners) that are not the game yet, and that have no
     * small first-run dialog to protect (Eden, Dolphin). Animal Crossing's intermediate logo screen counts as picture at 2.5% but not at 6%.
     */
    const val MIN_LIT_STRICT = 140
    private const val LIT_THRESHOLD = 24
    private const val MAX_COPY_FAILURES = 20
    private const val NO_SURFACE_GIVE_UP_MS = 5_000L

    /** Pure rule used by the poll loop (testable): enough non-black pixels. */
    fun isPicture(pixels: IntArray, minLit: Int = MIN_LIT): Boolean =
        pixels.count { c -> maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) > LIT_THRESHOLD } >= minLit

    @JvmStatic
    @JvmOverloads
    fun hold(
        activity: Activity,
        hooks: NebulosCoreHost.Hooks,
        settleMs: Long = 0,
        maxWaitMs: Long = 60_000,
        statusText: String? = "Starting game",
        isCurrent: () -> Boolean = { true },
        minLit: Int = MIN_LIT,
        /** What to do when the gate is over; the default tells the host the game is ready (use [NebulosCoreHost.Hooks.onGameReadyAfterPicture] for the full flow). */
        onDone: (() -> Unit)? = null,
    ) {
        val main = Handler(Looper.getMainLooper())
        val t0 = SystemClock.uptimeMillis()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val px = IntArray(W * H)
        var failures = 0
        var pictureAt = -1L
        var finished = false
        fun done(why: String) {
            if (finished) return
            finished = true
            Log.i(TAG, "picture gate over after ${SystemClock.uptimeMillis() - t0} ms: $why")
            if (isCurrent()) (onDone ?: { hooks.onGameReady() })()
        }
        fun poll() {
            if (finished) return
            if (activity.isDestroyed || activity.isFinishing || !isCurrent()) return
            val elapsed = SystemClock.uptimeMillis() - t0
            if (elapsed > maxWaitMs) { done("gave up waiting"); return }
            if (pictureAt >= 0) {
                if (SystemClock.uptimeMillis() - pictureAt >= settleMs) done("picture + ${settleMs} ms settle") else main.postDelayed(::poll, POLL_MS)
                return
            }
            if (statusText != null) hooks.setLoadingText(statusText)   // engines' own late progress calls would otherwise put their text back
            val view = findSurfaceView(activity.window.decorView)
            if (view == null || !view.holder.surface.isValid) {
                if (elapsed > NO_SURFACE_GIVE_UP_MS) done("no game surface to sample") else main.postDelayed(::poll, POLL_MS)
                return
            }
            try {
                PixelCopy.request(view, bmp, { result ->
                    if (finished) return@request
                    if (result == PixelCopy.ERROR_SOURCE_NO_DATA) {
                        // The game has not presented a single frame yet (slow starts: Dread, Tears of the Kingdom...): that is "not yet", not a failure.
                        main.postDelayed(::poll, POLL_MS)
                        return@request
                    }
                    if (result != PixelCopy.SUCCESS) {
                        if (++failures > MAX_COPY_FAILURES) done("sampling keeps failing (result $result)") else main.postDelayed(::poll, POLL_MS)
                        return@request
                    }
                    failures = 0
                    bmp.getPixels(px, 0, W, 0, 0, W, H)
                    if (isPicture(px, minLit)) {
                        pictureAt = SystemClock.uptimeMillis()
                        Log.i(TAG, "picture on screen after ${pictureAt - t0} ms; settling ${settleMs} ms")
                    }
                    main.postDelayed(::poll, if (pictureAt >= 0 && settleMs <= 0) 0L else POLL_MS)
                }, main)
            } catch (t: Throwable) {
                done("sampling threw $t")
            }
        }
        Log.i(TAG, "picture gate started (settle $settleMs ms)")
        poll()
    }

    private fun findSurfaceView(v: View): SurfaceView? {
        if (v is SurfaceView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findSurfaceView(v.getChildAt(i))?.let { return it }
        return null
    }
}
