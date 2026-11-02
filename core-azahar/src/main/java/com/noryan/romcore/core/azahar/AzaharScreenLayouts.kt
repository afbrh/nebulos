package com.noryan.romcore.core.azahar

import android.content.Context

/**
 * RomCore's 3DS screen layouts for a single display, as Azahar "custom layout" rectangles (window pixels). Each screen always keeps
 * its own aspect ratio (top 400x240, bottom 320x240).
 *
 *  - [Mode.SINGLE]: one screen scaled up to fill the window left to right (or top to bottom if it is the limit), pinned to the top;
 *    the other screen is parked off the window. 1280x960: top 1280x768, bottom 1280x960.
 *  - [Mode.STACKED]: whole-number scales. The big screen at the largest whole-number scale that fits above the other screen at 1x,
 *    centred at the top, and the other screen at 1x centred below it. 1280x960: top 3x = 1200x720 over the bottom 320x240.
 *  - [Mode.FILL]: "4:3 fill" for 4:3 windows (the Retroid Pocket Nova). The shown screen gets the largest 4:3 box that fits the
 *    window, centred (the whole window when it is 4:3): the emulator draws the top screen as a native 4:3 picture in this mode
 *    (see AzaharFourThree), the bottom screen is 4:3 anyway. The other screen is parked off the window. This is a PER-GAME choice
 *    (AzaharFourThree), never the saved global mode ([loadMode] / [saveMode] never return / store it).
 *  - [Mode.FILL_PIP]: [Mode.FILL] with the other screen drawn on top of it as a picture-in-picture window at 1x (native size: bottom
 *    320x240, top 400x240 when swapped), in the upper right corner of the filled 4:3 box with a [PIP_GAP] px gap. Azahar draws the
 *    `bottom` slot after the `top` slot, so the small window is always above; touch maps to the bottom screen's rectangle, so touches
 *    outside the small window do nothing. Per game like FILL. When swapped (bottom screen big) the top screen is the small window
 *    and is drawn normally (the native 4:3 top picture is only used while the top screen is the big one).
 *  - [Mode.STACKED_FULL]: the big screen scaled up to fill the window left to right, touching the top; the other screen takes
 *    the space left below it, as large as that allows. 1280x960: top 1280x768 over the bottom 256x192.
 *
 * `bottomFocus` swaps which screen is the big / shown one (the L3 tap). Azahar's custom layout has two slots: `top` is where the top
 * screen is drawn, `bottom` where the bottom screen is drawn, and with "swap screens" on the two screens trade slots. So [Layout.topSlot]
 * is always the big/shown screen's rectangle and [Layout.bottomSlot] the other one's.
 */
object AzaharScreenLayouts {
    enum class Mode { SINGLE, STACKED, STACKED_FULL, FILL, FILL_PIP;
        fun next() = values()[(ordinal + 1) % values().size]
        fun previous() = values()[(ordinal + values().size - 1) % values().size]
        val label get() = when (this) { SINGLE -> "Single Screen"; STACKED -> "Stacked"; STACKED_FULL -> "Stacked Full"; FILL -> "4:3 Fill"; FILL_PIP -> "Nebulos" }
        /** Chosen per game (AzaharFourThree), never the saved global mode. */
        val perGame get() = this == FILL || this == FILL_PIP
        internal val key get() = when (this) { SINGLE -> "single"; STACKED -> "stacked"; STACKED_FULL -> "stacked_full"; FILL -> "fill"; FILL_PIP -> "fill_pip" }
    }
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)
    data class Layout(val topSlot: Rect, val bottomSlot: Rect)

    private const val TOP_W = 400
    private const val BOTTOM_W = 320
    private const val H = 240
    /** Gap between the picture-in-picture window and the edges of the filled picture, in window pixels. */
    const val PIP_GAP = 16

    /** True when the emulator should draw the top screen as the native 4:3 picture (FILL always; FILL_PIP only while the top screen is the big one). */
    fun usesNativeFourThree(mode: Mode, bottomFocus: Boolean) = mode == Mode.FILL || (mode == Mode.FILL_PIP && !bottomFocus)

    fun compute(mode: Mode, bottomFocus: Boolean, w: Int, h: Int): Layout {
        val bigW = if (bottomFocus) BOTTOM_W else TOP_W
        val otherW = if (bottomFocus) TOP_W else BOTTOM_W
        return when (mode) {
            Mode.SINGLE -> Layout(fitIn(0, 0, w, h, bigW), Rect(w, 0, otherW, H))   // the hidden screen sits just right of the window
            Mode.FILL -> Layout(fourByThreeBox(w, h), Rect(w, 0, otherW, H))
            Mode.FILL_PIP -> {
                val box = fourByThreeBox(w, h)
                Layout(box, Rect(maxOf(0, box.x + box.width - otherW - PIP_GAP), box.y + PIP_GAP, otherW, H))
            }
            Mode.STACKED -> {
                val k = minOf(w / bigW, (h - H) / H)
                if (k >= 1) {
                    val bw = bigW * k; val bh = H * k
                    Layout(Rect((w - bw) / 2, 0, bw, bh), Rect((w - otherW) / 2, bh, otherW, H))
                } else split(w, h, bigW, otherW, h * 2 / 3)      // too small for a whole-number scale
            }
            // The split line is where the top screen would end if it filled the width (192 px left on 1280x960), never more than 80%.
            Mode.STACKED_FULL -> split(w, h, bigW, otherW, minOf(w * H / TOP_W, h * 4 / 5))
        }
    }

    /** The largest 4:3 rectangle inside the window, centred (the window itself when it is 4:3). */
    internal fun fourByThreeBox(w: Int, h: Int): Rect {
        val bw = minOf(w, h * 4 / 3); val bh = bw * 3 / 4
        return Rect((w - bw) / 2, (h - bh) / 2, bw, bh)
    }

    /** The big screen fitted into the first [bigH] rows, the other into the rest; both centred across the width, touching the split. */
    private fun split(w: Int, h: Int, bigW: Int, otherW: Int, bigH: Int): Layout =
        Layout(fitIn(0, 0, w, bigH, bigW), fitIn(0, bigH, w, h - bigH, otherW))

    /** The largest rectangle of a screen [sw] x 240 inside the area, centred across it and pinned to its top. */
    private fun fitIn(x: Int, y: Int, aw: Int, ah: Int, sw: Int): Rect {
        val width = minOf(aw, ah * sw / H)
        val height = width * H / sw
        return Rect(x + (aw - width) / 2, y, width, height)
    }

    private const val PREFS = "romcore_azahar_state"
    private const val KEY = "screen_layout"
    fun loadMode(ctx: Context): Mode {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return Mode.values().firstOrNull { it.key == saved && !it.perGame } ?: Mode.STACKED
    }
    fun saveMode(ctx: Context, m: Mode) {
        if (m.perGame) return      // per game, see AzaharFourThree
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, m.key).apply()
    }
}
