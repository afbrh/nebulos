package com.noryan.nebulos.core.jak

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * OpenGOAL's game data for one game, produced ON THE DEVICE from the user's own disc image (never bundled, never shared between users).
 *
 * Layout under [projDir] (`<externalFilesDir>/jak`, OpenGOAL's `--proj-path`):
 *   game/assets, game/graphics/opengl_renderer/shaders_es, goal_src/user, user  <- support files copied from the APK assets (once per version)
 *   out/<game>/{iso,fr3,obj}                                                      <- extractor output (once per game, several minutes)
 * The extractor ships as `libjak_extractor.so` (a PIE executable in jniLibs; apps may exec files from their native library dir).
 */
internal object JakData {
    private const val TAG = "JakData"
    /** Bump when the APK's support files (assets/jak) or the extractor/engine data format change: re-copies assets, re-extracts games. */
    const val DATA_VERSION = "2"
    private const val SUPPORT_MARKER = ".nebulos_support_version"
    private const val GAME_MARKER = ".nebulos_extracted_version"
    /** Free space needed on top of what is already there before an import starts (extractor working data + output), per game. */
    private val NEED_BYTES = mapOf("jak1" to (5L shl 30), "jak2" to (14L shl 30), "jak3" to (14L shl 30))

    @Volatile private var running: Process? = null

    fun projDir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "jak").apply { mkdirs() }

    /**
     * Pins the game's aspect ratio to 4:3 by editing its pc-settings.gc (read by the shared GOAL pckernel):
     * `(aspect-state <game setting> <w> <h> <auto?>)`. Written before every launch; the file is created when absent.
     */
    fun forceAspect43(ctx: Context, game: String) {
        try {
            val version = when (game) { "jak1" -> "#x1000a00040000"; "jak2" -> "#x200000000"; else -> "#x300000000" }
            val f = File(projDir(ctx), "user/OpenGOAL/$game/settings/pc-settings.gc")
            f.parentFile?.mkdirs()
            val line = "(aspect-state aspect4x3 4 3 #f)"
            val text = if (f.isFile) f.readText() else "(settings $version\n  )\n"
            val re = Regex("\\(aspect-state[^)]*\\)")
            val out = if (re.containsMatchIn(text)) re.replace(text, line)
                      else text.replaceFirst(Regex("\\(settings[^\\n]*\\n"), "$0  $line\n")
            if (out != text || !f.isFile) f.writeText(out)
        } catch (e: Exception) { Log.w(TAG, "could not pin 4:3: $e") }
    }

    fun gameReady(ctx: Context, game: String): Boolean =
        File(projDir(ctx), "out/$game/$GAME_MARKER").let { it.isFile && it.readText().trim() == DATA_VERSION }

    /** Kill a running import (Close Game while it works): child processes are not ended with the app process. */
    fun cancel() { running?.let { runCatching { it.destroyForcibly() } }; running = null }

    /** Null when the game can start, otherwise a user-facing error. Blocks (call off the UI thread). */
    fun ensureReady(ctx: Context, game: String, iso: String?, progress: (String) -> Unit): String? {
        val proj = projDir(ctx)
        runCatching { installSupport(ctx, proj, progress) }.onFailure { return "Could not prepare the game files: ${it.message}" }
        if (gameReady(ctx, game)) return null
        if (iso == null || !File(iso).isFile) return "The disc image is not available: ${iso ?: "(no path)"}"
        val need = NEED_BYTES[game] ?: (14L shl 30)
        val free = runCatching { StatFs(proj.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < need) return "Not enough free storage to prepare this game: ${need shr 30} GB needed, ${free shr 30} GB free."
        return extract(ctx, proj, game, iso, progress)
    }

    private fun installSupport(ctx: Context, proj: File, progress: (String) -> Unit) {
        val marker = File(proj, SUPPORT_MARKER)
        if (marker.isFile && marker.readText().trim() == DATA_VERSION && File(proj, "game/assets").isDirectory) return
        progress("Preparing game files")
        copyAssetTree(ctx, "jak", proj)
        File(proj, "goal_src/user").mkdirs(); File(proj, "user").mkdirs()
        marker.writeText(DATA_VERSION)
    }

    private fun copyAssetTree(ctx: Context, assetPath: String, dest: File) {
        val names = ctx.assets.list(assetPath) ?: return
        if (names.isEmpty()) {   // a file
            dest.parentFile?.mkdirs()
            ctx.assets.open(assetPath).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        for (n in names) copyAssetTree(ctx, "$assetPath/$n", File(dest, n))
    }

    /** Extractor command line (see core-jak/README-jak.md): -e extract the disc, -d decompile, -c compile the game code (ARM64 backend). */
    private fun extractorCommand(ctx: Context, game: String, iso: String, proj: File): List<String> = listOf(
        File(ctx.applicationInfo.nativeLibraryDir, "libjak_extractor.so").absolutePath,
        "-g", game, "-e", "-d", "-c", "--instruction-set", "arm64", "--proj-path", proj.absolutePath, iso,
    )

    private fun extract(ctx: Context, proj: File, game: String, iso: String, progress: (String) -> Unit): String? {
        val exe = File(ctx.applicationInfo.nativeLibraryDir, "libjak_extractor.so")
        if (!exe.isFile) return "The game-data extractor is missing from this build."
        val title = JakCore.TITLES[game] ?: game
        progress("Preparing $title (first launch, takes several minutes)")
        val log = File(ctx.filesDir, "jak-extract-$game.log")
        val out = File(proj, "out/$game")
        File(out, GAME_MARKER).delete()
        val pb = ProcessBuilder(extractorCommand(ctx, game, iso, proj)).directory(proj).redirectErrorStream(true)
        pb.environment()["HOME"] = ctx.filesDir.absolutePath
        val p = try { pb.start() } catch (e: IOException) { return "Could not start the game-data extractor: ${e.message}" }
        running = p
        var lastShown = 0L
        var tail = ArrayDeque<String>()
        try {
            p.inputStream.bufferedReader().useLines { lines ->
                log.bufferedWriter().use { w ->
                    for (line in lines) {
                        w.appendLine(line)
                        tail.addLast(line); if (tail.size > 12) tail.removeFirst()
                        val t = progressText(line) ?: continue
                        val now = android.os.SystemClock.uptimeMillis()
                        if (line.contains("[PROGRESS]") || now - lastShown > 700) { lastShown = now; progress("Preparing $title: $t") }
                    }
                }
            }
        } catch (e: IOException) { Log.w(TAG, "extractor stream: $e") }
        val code = runCatching { p.waitFor() }.getOrDefault(-1)
        running = null
        Log.i(TAG, "extractor exit $code; log ${log.absolutePath}")
        if (code != 0) {
            val why = if (code == 137 || code == 9 + 128) "it was stopped by Android (out of memory?)" else "exit code $code"
            return "Preparing $title failed ($why).\n" + tail.takeLast(4).joinToString("\n")
        }
        if (!File(out, "fr3").isDirectory || !File(out, "obj").isDirectory) return "Preparing $title finished but the game data is incomplete."
        File(out, GAME_MARKER).writeText(DATA_VERSION)
        // The extractor's working copy of the disc contents is not needed any more.
        for (d in listOf("iso_data", "decompiler_out")) File(proj, d).takeIf { it.isDirectory }?.deleteRecursively()
        return null
    }

    /** One short progress phrase from an extractor log line, or null to ignore the line. */
    internal fun progressText(line: String): String? {
        val t = line.replace(Regex("""\u001B\[[0-9;]*m"""), "").trim()
        Regex("""\[PROGRESS]\s+stage=(\w+)""").find(t)?.let {
            return when (it.groupValues[1]) {
                "extract" -> "reading the disc"
                "decompile" -> "converting game data (the longest step)"
                "compile" -> "compiling the game"
                "done" -> "finishing"
                else -> it.groupValues[1]
            }
        }
        Regex("""^\[\s*(\d+)%]\s+\[goalc""").find(t)?.let { return "compiling the game ${it.groupValues[1]}%" }
        return null   // everything else is log noise
    }
}
