package com.noryan.nebulos.core.jak

import android.content.Context
import android.system.Os
import android.util.Log
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File

/**
 * Portable save data (nebulos-api/SAVEDATA.md): the games' memory-card saves live in `<root>/jak-core/<jak1|jak2|jak3>/saves`.
 * OpenGOAL reads `OPENGOAL_SAVE_DIR` (patched common/util/FileUtil.cpp); it is exported BEFORE the native library starts.
 * Settings (controller bindings, graphics) stay local in `<proj>/user`.
 */
internal object JakSaveData {
    const val CORE_ID = "jak"
    const val ENV_SAVE_DIR = "OPENGOAL_SAVE_DIR"
    private const val TAG = "NebulosSave"

    fun saveDir(ctx: Context): File = SaveRoot.coreDir(ctx, CORE_ID)

    fun prepare(ctx: Context): File {
        val dir = saveDir(ctx)
        runCatching { Os.setenv(ENV_SAVE_DIR, dir.absolutePath, true) }.onFailure { Log.e(TAG, "setenv failed: $it") }
        Log.i(TAG, "jak saves: ${dir.absolutePath}")
        return dir
    }
}
