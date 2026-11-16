package com.noryan.nebulos.api

import android.view.MotionEvent

/**
 * Double tap on the small (picture-in-picture) screen of the Nebulos layout swaps the screens. This is the only way to swap: there is no
 * hotkey and no menu row. A core feeds every touch of its game window to [onTouch]; the first tap is left to the game, the second tap of a
 * double tap is consumed (with its move/up) so a double tap on a touch button does not press it twice.
 *
 * [smallRect] = the small screen as x, y, width, height in window pixels, or null while the swap does not apply (another layout, menu open).
 */
class DoubleTapSwap(private val density: Float, private val smallRect: () -> IntArray?, private val onSwap: () -> Unit) {
    private var downInSmall = false
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var swallowing = false
    private val slop get() = 20f * density

    /** True = the event is consumed and must not reach the game. */
    fun onTouch(e: MotionEvent): Boolean {
        if (swallowing) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
            return true
        }
        val r = smallRect() ?: run { lastTapAt = 0L; return false }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downInSmall = e.x >= r[0] && e.x < r[0] + r[2] && e.y >= r[1] && e.y < r[1] + r[3]
                downAt = e.eventTime; downX = e.x; downY = e.y
                if (downInSmall && lastTapAt != 0L && e.eventTime - lastTapAt < DOUBLE_TAP_MS &&
                    Math.hypot((e.x - lastTapX).toDouble(), (e.y - lastTapY).toDouble()) < slop * 2) {
                    lastTapAt = 0L; swallowing = true
                    onSwap()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downAt < TAP_MS && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) < slop
                lastTapAt = if (downInSmall && quick) e.eventTime else 0L
                lastTapX = e.x; lastTapY = e.y
            }
            MotionEvent.ACTION_CANCEL -> lastTapAt = 0L
        }
        return false
    }

    private companion object { const val DOUBLE_TAP_MS = 350L; const val TAP_MS = 300L }
}
