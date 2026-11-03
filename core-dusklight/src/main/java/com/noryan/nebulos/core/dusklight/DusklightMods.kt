package com.noryan.nebulos.core.dusklight

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Bundled Dusklight mod: "Twilight HD" (github.com/OTPR26/twilight-hd-hud, MIT, v2.7.6) - a TPHD / Wii U style HUD with
 * Wii U style button prompts. Shipped unmodified as the asset [ASSET]; copied to `<userDir>/mods/` (the engine's mod search
 * dir is `<--user-dir>/mods`) before the native library starts. A version marker re-copies it when [VERSION] changes.
 * Its settings are `mod.org_twilight_hd_hud.*` config keys, pinned by [DusklightArgs].
 */
internal object DusklightMods {
    const val ASSET = "twilight_hd_hud.dusk"
    const val VERSION = "2.7.6-1"          // bump when the asset changes
    private const val TAG = "DusklightMods"

    fun install(ctx: Context, userDir: File) = runCatching {
        val dir = File(userDir, "mods").apply { mkdirs() }
        val target = File(dir, ASSET)
        val marker = File(dir, ".nebulos_twilight_hd_version")
        if (target.exists() && marker.exists() && marker.readText() == VERSION) return@runCatching
        val tmp = File(dir, "$ASSET.tmp")
        ctx.assets.open(ASSET).use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "rename failed" }
        marker.writeText(VERSION)
        Log.i(TAG, "installed $ASSET $VERSION -> ${target.absolutePath}")
    }.onFailure { Log.e(TAG, "mod install failed: $it") }
}
