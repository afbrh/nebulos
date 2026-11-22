package com.noryan.nebulos.core.smo

import com.smorecompandroid.BuildConfig

/**
 * Which native runtime library a Switch game loads: `smo_main` (the live smorecomp-src tree) unless the game is frozen in
 * `frozen-runtimes.properties`, then `smo_main_<tag>` (built from that tag's worktree). See FREEZING.md.
 */
object RuntimeLib {
    private val frozen: Map<String, String> = BuildConfig.FROZEN_LIBS.split(";").filter { "=" in it }
        .associate { it.substringBefore("=") to it.substringAfter("=") }

    /** The `getLibraries()` answer for [game] (the core name: smo, dread, wonder, sm3dw ...). */
    @JvmStatic fun forGame(game: String): Array<String> = arrayOf("shaderc_shared", frozen[game] ?: "smo_main")
}
