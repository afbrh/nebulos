package com.noryan.nebulos.core.watermelonds

import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stick / d-pad movement for The Legend of Zelda: Phantom Hourglass and Spirit Tracks.
 *
 * Both games move Link toward the stylus: touching the screen at a point makes him walk or run there, and the further the
 * touch is from him the faster he goes. The camera keeps Link near the middle of the touch screen, so holding a direction
 * on the stick / d-pad is turned into a stylus touch the same direction from the middle of the screen, with a distance that
 * follows how far the stick is pushed (small push = walk, full push = touch at the edge of the screen (the furthest, fastest run; diagonals stop short of the corner buttons)). Nothing is patched or read from the game: the
 * game just sees a stylus, so it works for every region and ROM version.
 *
 * A button: a short stylus tap on the spot just in front of Link, in the direction he was last pushed. The game treats it exactly like tapping
 * the thing in front of him (open the door or chest, read the sign, talk, pick up, swing at the enemy); with nothing there it is a small step.
 *
 * Holding SELECT hands the d-pad (and A) back to the game untouched (menus, item shortcuts), like the community d-pad patches do.
 */
internal class ZeldaStickMove(private val touch: (Int, Int) -> Unit, private val release: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val stick = FloatArray(4)        // up, down, left, right (analog 0..1)
    private val dpad = BooleanArray(4)
    @Volatile private var selectHeld = false
    private var touching = false
    private var lastX = -1
    private var lastY = -1
    /** Unit vector of the direction Link was last pushed (screen space); the A button taps this way from him. Starts facing down (toward the camera). */
    private var faceX = 0f
    private var faceY = 1f
    private var tapping = false

    /** True while this class owns the d-pad / stick (the session must not also forward them as buttons). */
    fun owns(): Boolean = !selectHeld

    fun select(down: Boolean) {
        selectHeld = down
        if (down) update(forceRelease = true)
    }

    fun dpad(index: Int, down: Boolean) { dpad[index] = down; update() }
    fun axis(index: Int, value: Float) { stick[index] = value.coerceIn(0f, 1f); update() }

    private fun update(forceRelease: Boolean = false) = main.post {
        val x = (if (dpad[3]) 1f else 0f) - (if (dpad[2]) 1f else 0f) + stick[3] - stick[2]
        val y = (if (dpad[1]) 1f else 0f) - (if (dpad[0]) 1f else 0f) + stick[1] - stick[0]
        val mag = min(1f, hypot(x, y))
        val len = hypot(x, y)
        if (mag >= DEAD && len > 0f) { faceX = x / len; faceY = y / len }   // the direction an A-button tap goes
        // An A-button tap overrides the stick while it is down; [endTap] puts the walk back afterwards.
        if (tapping && !forceRelease) return@post
        if (forceRelease || mag < DEAD || selectHeld) {
            if (touching) { touching = false; lastX = -1; lastY = -1; release() }
            return@post
        }
        // d-pad = full run; analog scales from walk to run
        val dx = x / len; val dy = y / len
        val edge = edgeDistance(dx, dy)
        var dist = WALK + (edge - WALK) * ((mag - DEAD) / (1f - DEAD)).coerceIn(0f, 1f)
        // The game's Menu button (and the Save button of its bar) sits in the bottom-left corner: stop short of it, keeping the direction.
        while (dist > WALK && CENTER_X + dx * dist < MENU_RIGHT && CENTER_Y + dy * dist > MENU_TOP) dist -= 1f
        val px = (CENTER_X + dx * dist).toInt().coerceIn(0, 255)
        val py = (CENTER_Y + dy * dist).toInt().coerceIn(0, 191)
        if (!touching || px != lastX || py != lastY) { touching = true; lastX = px; lastY = py; touch(px, py) }
    }

    /**
     * A button pressed: tap just in front of Link, in the direction he was last pushed. It overrides the stick: if the stick is held, the walk touch is replaced
     * by the tap for [TAP_MS] and then resumes (see [endTap]).
     */
    fun interact() { main.post {
        if (tapping || selectHeld) return@post
        val px = (LINK_X + faceX * INTERACT).toInt().coerceIn(0, 255)
        val py = (LINK_Y + faceY * INTERACT).toInt().coerceIn(0, 191)
        tapping = true
        touch(px, py)
        main.postDelayed({ endTap() }, TAP_MS)
    } }

    private fun endTap() {
        tapping = false
        release()
        lastX = -1; lastY = -1   // a held stick touches again; a released one does nothing more
        update()
    }

    fun stop() = update(forceRelease = true)

    companion object {
        private const val DEAD = 0.2f
        private const val CENTER_X = 128f
        private const val CENTER_Y = 96f
        private const val MENU_RIGHT = 70f   // touch-screen pixels: Menu button spans x 0-54 / y 169-192; its open bar's Save button x 22-62 / y 165-186
        private const val MENU_TOP = 156f
        private const val WALK = 22f
        // Where Link's middle is on the touch screen in open areas, measured from his sprite: left of and a little above the screen centre (the walk touches above use the centre).
        private const val LINK_X = 117f
        private const val LINK_Y = 88f
        private const val INTERACT = 24f   // distance from Link's middle to the A-button tap, touch-screen pixels (about one tile)
        private const val TAP_MS = 140L

        /**
         * Distance from the middle of the screen to the edge of the ellipse with radii 128 x 96 in direction ([dx], [dy]) (unit vector):
         * a full push straight up/down/left/right touches the very edge of the screen, and a diagonal one stops short of the corners,
         * where the game's on-screen buttons (Menu, items) sit.
         */
        fun edgeDistance(dx: Float, dy: Float): Float {
            val a = CENTER_Y * dx; val b = CENTER_X * dy
            return CENTER_X * CENTER_Y / sqrt(a * a + b * b)
        }

        /** 4-char game codes (ROM header 0x0C) of Phantom Hourglass (AZE*) and Spirit Tracks (BKI*), every region. */
        fun isSupportedGameCode(code: String?): Boolean =
            code != null && code.length == 4 && (code.startsWith("AZE") || code.startsWith("BKI"))

        fun gameCode(romPath: String): String? = runCatching {
            val f = File(romPath.removePrefix("file://"))
            if (!f.isFile) return null
            f.inputStream().use { s -> val b = ByteArray(16); if (s.read(b) < 16) null else String(b, 12, 4, Charsets.US_ASCII) }
        }.getOrNull()
    }
}
