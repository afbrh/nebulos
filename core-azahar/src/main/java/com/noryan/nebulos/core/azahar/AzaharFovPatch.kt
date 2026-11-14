package com.noryan.nebulos.core.azahar

import org.citra.citra_emu.NativeLibrary

/**
 * "4:3 Fill" for games that cull what they draw to the camera's original view: the emulator's own widening (vertex shader)
 * only shows MORE of what the game drew, so strips the game culled show up as holes. For these games the game's camera is
 * patched instead, so the game itself draws and culls the extra 25% of vertical view.
 *
 * Applied through the same guest-memory patch set as AzaharSixtyFps (verified against the original words first, switched
 * on only while 4:3 Fill is in force, put back when it is off). Flag address 1 tells the emulator the camera is patched,
 * so its shader stops widening 3D draws a second time (it still adjusts the 2D HUD) and the edge fade stays off.
 */
object AzaharFovPatch {
    private fun w(v: Long): Int = v.toInt()

    /** The Legend of Zelda: A Link Between Worlds (USA). Projection builder at 0x57F6C0 (see nebulos-albw-re notes): m11 = 2n/(t-b),
     *  m12 = (t+b)/(t-b) come from s5 = 1/(t-b) at 0x57F738. A cave in the unused end of the code segment multiplies s5 by 0.8. */
    private const val ALBW_USA = "00040000000EC300"
    private const val HOOK = 0x0057F738
    private const val CAVE = 0x0069211C

    private val ALBW_ON = intArrayOf(
        HOOK, w(0xEA044A77),    // b CAVE
        CAVE, w(0xEEC22A03),    // vdiv.f32 s5, s4, s6   (the instruction the hook replaced)
        CAVE + 4, w(0xEDDF3A01),  // vldr s7, [pc, #4]   (s7 is dead here)
        CAVE + 8, w(0xEE622AA3),  // vmul.f32 s5, s5, s7
        CAVE + 12, w(0xEAFBB583), // b 0x57F73C
        CAVE + 16, w(0x3F4CCCCD), // 0.8f
    )
    private val ALBW_OFF = intArrayOf(HOOK, w(0xEEC22A03))

    fun hasPatch(titleId: String?) = titleId == ALBW_USA

    /** Switches the camera patch on/off with the 4:3 Fill layout. No-op for games without one. */
    fun apply(titleId: String?, fillOn: Boolean) {
        if (!hasPatch(titleId)) return
        runCatching { NativeLibrary.setFramePatch(1, ALBW_ON, ALBW_OFF, IntArray(0), fillOn) }
    }
}
