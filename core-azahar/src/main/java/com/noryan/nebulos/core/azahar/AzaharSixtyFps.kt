package com.noryan.nebulos.core.azahar

import org.citra.citra_emu.NativeLibrary

/**
 * 60 fps for 30 fps games whose engine has no real 60 fps mode, per game and switchable at any time (menu row "60 FPS").
 * Two halves, both driven by one native patch set (azahar-src video_core/frame_interp.cpp):
 *
 *  - Game side: the frame divisor is forced to 1 so the game loops at 60 Hz, and the world update (which assumes 30 Hz and
 *    would run at 2x speed) is skipped on every other loop, so every game state is drawn twice in a row. Small code
 *    caves at the end of the code image do the skipping; effects that step with a fixed 1/30 s are patched to 1/60.
 *  - Emulator side: every draw is blended with the same draw of the previous frame (vertex-shader matrices), so the first
 *    frame of each pair is the midpoint between two states: 60 distinct, evenly spaced pictures a second at normal speed.
 *
 * Turning it off enforces the original words again (every frame, only written when different, so even a save state made
 * with it on cannot leave it half on) and puts the divisor back to 2. The patch also refuses to switch on unless the
 * words it patches still hold the values of the build it was made for.
 *
 * Findings and method: zelda3d-re/NOTES.md (outside the repo).
 */
object AzaharSixtyFps {
    class Spec(val flagAddr: Int, val on: IntArray, val off: IntArray, val once: IntArray)

    /** A 32-bit word given as the unsigned hex it is written in everywhere else. */
    private fun w(v: Long): Int = v.toInt()

    private const val MM3D_USA = "0004000000125500"

    private val SPECS = mapOf(
        // The Legend of Zelda: Majora's Mask 3D (USA, the build the addresses were taken from).
        MM3D_USA to Spec(
            flagAddr = 0x006A3A14, // frame divisor: 1 = 60 Hz loop, 2 = 30 Hz loop
            on = intArrayOf(
                0x006A3A14, 0x00000001,
                0x001072C0, 0x3C888889, // effects manager step 1/30 s -> 1/60 s
                0x0061CFB8, w(0xEE000A10), // cave A (from 0x104D30): vmov s0,r0; r1=&mailbox; mailbox.byte=r8 (loop runs the update); mailbox.word=iteration
                0x0061CFBC, w(0xE59F100C),
                0x0061CFC0, w(0xE5C18000),
                0x0061CFC4, w(0xE59D2060),
                0x0061CFC8, w(0xE5812004),
                0x0061CFCC, w(0xEAEB9F58),
                0x0061CFD0, 0x007CEF00,
                0x0061CFD4, w(0xE59F1010), // cave B (from 0x14F5C8): run the world update (0x15F4FC) only when the mailbox byte is set
                0x0061CFD8, w(0xE5D11000),
                0x0061CFDC, w(0xE3510000),
                0x0061CFE0, 0x0A000000,
                0x0061CFE4, w(0xEBED0944),
                0x0061CFE8, w(0xEAECC977),
                0x0061CFEC, 0x007CEF00,
                0x00104D30, w(0xEA1460A0), // branch into cave A
                0x0014F5C8, w(0xEA133681), // branch into cave B
            ),
            off = intArrayOf(
                0x001072C0, 0x3D088889,
                0x00104D30, w(0xEE000A10),
                0x0014F5C8, w(0xEB003FCB),
            ),
            once = intArrayOf(0x006A3A14, 0x00000002),
        ),
    )

    fun specFor(titleId: String?): Spec? = titleId?.let(SPECS::get)

    private const val KEY_PREFIX = "sixty_fps_"

    /** On by default for games that have the patch; the menu row turns it off. */
    fun isEnabled(store: AzaharFourThree.Store, gameKey: String): Boolean = store.get(KEY_PREFIX + gameKey) ?: true
    fun setEnabled(store: AzaharFourThree.Store, gameKey: String, on: Boolean) = store.put(KEY_PREFIX + gameKey, on)

    /** Hands the patch set to the emulator; takes effect at the next frame (also while the menu has the game paused). */
    fun apply(spec: Spec, on: Boolean): Boolean = try {
        NativeLibrary.setFramePatch(spec.flagAddr, spec.on, spec.off, spec.once, on)
        true
    } catch (_: Throwable) { false }

    /** Switched off for everything else, e.g. at the start of a game that has no patch. */
    fun clear() { runCatching { NativeLibrary.setFramePatch(0, null, null, null, false) } }
}
