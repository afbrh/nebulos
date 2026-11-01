package com.noryan.romcore.core.azahar

import android.content.Context
import com.noryan.romcore.api.FourByThree

/**
 * "4:3 Fill" for 3DS games on a 4:3 screen (the Retroid Pocket Nova, 1280x960): the top screen is drawn as a native 4:3 picture that
 * fills the window instead of a 5:3 picture stretched taller (see AzaharScreenLayouts.Mode.FILL and the Azahar emulator change
 * `NativeLibrary.setFourThreeFill`, which gives 3D content 25% more vertical field of view and keeps 2D HUD content undistorted).
 * Works for every game without per-game patches, but needs the game to draw its top screen in one 400x240 pass.
 *
 * The choice is stored per game (key = title id for cartridge dumps, otherwise the file name) and defaults to ON on 4:3 screens
 * (same screen test as [FourByThree.defaultOn]) except for titles in [DENY] (known to look wrong with it). It applies live: the
 * menu's Screen Layout row cycles Single / Stacked / Stacked Full / 4:3 Fill and the last one is this per-game switch.
 */
object AzaharFourThree {
    /** Title ids (16 upper-case hex digits) for which the default is OFF, with why. The user can still turn it on. */
    val DENY: Map<String, String> = emptyMap()

    private const val PREFS = "romcore_azahar_state"
    private const val KEY_PREFIX = "fill43_"

    fun gameKey(titleId: String?, romPath: String): String = titleId ?: romPath.substringAfterLast('/')

    fun defaultOn(titleId: String?, widthPx: Int, heightPx: Int): Boolean =
        FourByThree.defaultOn(widthPx, heightPx) && (titleId == null || titleId !in DENY)

    /** Where the per-game choice is kept (SharedPreferences on the device; a map in tests). */
    interface Store {
        fun get(key: String): Boolean?
        fun put(key: String, value: Boolean)
    }

    fun isEnabled(store: Store, gameKey: String, titleId: String?, widthPx: Int, heightPx: Int): Boolean =
        store.get(KEY_PREFIX + gameKey) ?: defaultOn(titleId, widthPx, heightPx)

    fun setEnabled(store: Store, gameKey: String, on: Boolean) = store.put(KEY_PREFIX + gameKey, on)

    /** The mode in force: the per-game 4:3 Fill when on, otherwise the global [AzaharScreenLayouts] choice. */
    fun effectiveMode(fillOn: Boolean, global: AzaharScreenLayouts.Mode): AzaharScreenLayouts.Mode =
        if (fillOn) AzaharScreenLayouts.Mode.FILL else global

    /** What the Screen Layout row does when cycled from [current]: the new mode plus the new per-game fill flag and global mode. */
    data class Step(val mode: AzaharScreenLayouts.Mode, val fillOn: Boolean, val global: AzaharScreenLayouts.Mode)

    fun step(current: AzaharScreenLayouts.Mode, global: AzaharScreenLayouts.Mode, forward: Boolean): Step {
        val m = if (forward) current.next() else current.previous()
        return if (m == AzaharScreenLayouts.Mode.FILL) Step(m, true, global) else Step(m, false, m)
    }

    fun prefsStore(ctx: Context): Store = object : Store {
        private val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        override fun get(key: String) = runCatching { if (p.contains(key)) p.getBoolean(key, false) else null }.getOrNull()
        override fun put(key: String, value: Boolean) { p.edit().putBoolean(key, value).apply() }
    }
}
