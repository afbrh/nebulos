package com.noryan.nebulos.core.eden

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import org.json.JSONObject
import org.yuzu.yuzu_emu.NativeLibrary
import java.io.File

/**
 * Dumps a Switch game's files into plain folders for native ports: `<outDir>/exefs` (the ExeFS as the loader sees it, patched by
 * the installed update, raw: `main`/`rtld`/`sdk`/`subsdk*` stay NSO files, `main.npdm`) and `<outDir>/romfs` (full RomFS tree with the
 * update applied, LayeredFS/mods NOT baked in). Uses Eden's own keys and installed update (eden-src patch 0009, native_dump.cpp).
 *
 * MUST run in the `:eden` process (that is where libyuzu-android.so, the keys and the NAND live): call [dump] from there on a worker
 * thread, or start [EdenDumpService] from anywhere. Finished files are skipped on a re-run, so an interrupted dump resumes.
 */
object EdenGameDump {
    data class Result(
        val ok: Boolean, val error: String?, val titleId: String?, val name: String?,
        /** Packed version of the update (or base) as installed, e.g. "1.6.0" for BotW. */
        val version: String?, val versionRaw: Long, val displayVersion: String?, val updateSource: String?,
        val updateAppliedExefs: Boolean, val updateAppliedRomfs: Boolean,
        val exefsFiles: Long, val romfsFiles: Long, val romfsBytes: Long, val seconds: Double,
        val outDir: String?, val raw: String,
    )

    /** `<save root>/native-dumps`, the same `_Nebulos` root every core uses (SaveRoot), so it follows the ROM card. */
    fun baseDir(ctx: Context): File = File(SaveRoot.current(ctx), "native-dumps").also { it.mkdirs() }

    /** Staging folder for [gamePath] while the title id is not known yet (stable name, so a re-run resumes). */
    fun stagingDir(ctx: Context, gamePath: String): File =
        File(baseDir(ctx), ".incoming-" + SaveRoot.sanitizeSegment(gamePath.substringAfterLast('/').substringBeforeLast('.')))

    /**
     * Blocking. [outDir] null = `<baseDir>/<TITLEID>`. [progress] gets (bytesDone, bytesTotal) about twice a second.
     * [cancel] can be flipped from another thread (or call [cancel]).
     */
    fun dump(ctx: Context, gamePath: String, outDir: File? = null, progress: (Long, Long) -> Unit = { _, _ -> }): Result {
        val target = outDir ?: stagingDir(ctx, gamePath)
        val poller = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val p = NativeLibrary.getDumpProgress()
                    if (p[0] in 2..3) progress(p[1], p[2])
                    Thread.sleep(500)
                }
            } catch (_: InterruptedException) {}
        }.also { it.isDaemon = true; it.start() }
        val json = try { NativeLibrary.dumpGameFiles(gamePath, target.absolutePath) } finally { poller.interrupt() }
        var r = parse(json, target.absolutePath)
        if (r.ok && outDir == null && r.titleId != null) {
            val final = File(baseDir(ctx), r.titleId)
            if (final.exists()) {
                r = r.copy(ok = false, error = "${final.path} already exists; staged dump left in ${target.path}")
            } else if (target.renameTo(final)) {
                r = r.copy(outDir = final.absolutePath)
            }
        }
        return r
    }

    fun cancel() = NativeLibrary.cancelDump()

    /** Pure JSON mapping (unit-tested). */
    fun parse(json: String, outDir: String?): Result {
        val o = try { JSONObject(json) } catch (e: Exception) {
            return Result(false, "bad result: ${e.message}", null, null, null, 0, null, null, false, false, 0, 0, 0, 0.0, outDir, json)
        }
        return Result(
            o.optBoolean("ok"), o.optString("error").ifEmpty { null }, o.optString("titleId").ifEmpty { null },
            o.optString("name").ifEmpty { null }, o.optString("version").ifEmpty { null }, o.optLong("versionRaw"),
            o.optString("displayVersion").ifEmpty { null }, o.optString("updateSource").ifEmpty { null },
            o.optBoolean("updateAppliedExefs"), o.optBoolean("updateAppliedRomfs"),
            o.optLong("exefsFiles"), o.optLong("romfsFiles"), o.optLong("romfsBytes"), o.optDouble("seconds"), outDir, json,
        )
    }
}
