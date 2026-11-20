package com.noryan.nebulos.core.ico

import android.content.Context
import android.system.Os
import android.util.Log
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File

/** Before the native side starts: the first-run settings and the game's save folders in the `_Nebulos` folder. */
internal object IcoSetup {
    private const val TAG = "IcoSetup"

    /** The folder ico-pc keeps config.toml, the archive, memory cards and states in (`getExternalFilesDir`, see android_paths.c). */
    fun filesDir(ctx: Context): File? = ctx.getExternalFilesDir(null)

    /**
     * The first-run settings (a config.toml the game has not written yet): 2x resolution, FSR 1 upscaling at full sharpness, the analogue
     * stick fix (run in every direction) and the 60 fps cap. Keys not listed keep their defaults; the game's Options menu changes them later.
     */
    fun seedConfig(files: File) {
        runCatching {
            val f = File(files, "config.toml")
            if (f.exists()) return
            files.mkdirs()
            f.writeText(
                "version = 1\n\n[video]\nresolution = \"2x\"\nupscaling = \"fsr1\"\nupscaling_sharpness = 100\nframerate = \"60\"\n\n" +
                    "[gameplay]\nstick_fix = true\n"
            )
        }.onFailure { Log.e(TAG, "seeding config.toml failed: $it") }
    }

    /**
     * Memory cards and save states: `<files>/memcard` and `<files>/states` become symlinks to `<_Nebulos>/ico-core/<name>` (a symlink from an
     * app folder to a /sdcard path works, SAVEDATA.md rule 2). Real folders from before are moved into the `_Nebulos` folder first.
     */
    fun linkSaves(ctx: Context, files: File) {
        for (name in listOf("memcard", "states")) runCatching {
            val target = SaveRoot.coreSubDir(ctx, "ico", name)
            val link = File(files, name)
            val current = runCatching { Os.readlink(link.absolutePath) }.getOrNull()
            if (current == target.absolutePath) return@runCatching
            if (current != null) {
                link.delete()
            } else if (link.isDirectory) {
                link.copyRecursively(target, overwrite = false)
                if (!link.deleteRecursively()) { Log.e(TAG, "could not move ${link.path}: leaving the saves where they are"); return@runCatching }
            }
            files.mkdirs()
            Os.symlink(target.absolutePath, link.absolutePath)
            Log.i(TAG, "${link.path} -> ${target.path}")
        }.onFailure { Log.e(TAG, "linking $name failed: $it") }
    }
}
