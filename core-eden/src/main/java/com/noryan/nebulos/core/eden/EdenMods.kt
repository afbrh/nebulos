package com.noryan.nebulos.core.eden

import android.content.Context
import java.io.File

/**
 * Game mods Nebulos ships for Eden, copied into Eden's per-game mod folder `<externalFilesDir>/load/<TITLE ID>/<mod>/` (romfs/ and
 * exefs/ inside the mod; Eden's own mod loader does the rest). Source files are in assets/eden_mods/ with the same layout.
 *
 * Today: "NoDOF" (blur removal) for Link's Awakening (shader swap) and Echoes of Wisdom (one build-id-checked patch per game
 * version).
 *
 * Each mod is copied once: a mod folder the user deletes (to turn it off) stays deleted, a folder that is already there is left
 * alone, and a mod added in a later Nebulos version is copied the first time it is seen.
 */
object EdenMods {
    private const val ROOT = "eden_mods"
    private const val PREFS = "nebulos_eden_mods"
    private const val KEY = "installed_mods"

    /** A game with a shipped "4:3 Aspect Ratio" mod: [title] matches the library title, [dir] is the mod folder under load/. */
    private class AspectGame(val title: Regex, val dir: String)

    private val ASPECT_GAMES = listOf(
        AspectGame(Regex("mario\\s*odyssey", RegexOption.IGNORE_CASE), "0100000000010000/4-3 Aspect Ratio"),
        AspectGame(Regex("mario\\s*bros\\.?\\s*wonder", RegexOption.IGNORE_CASE), "010015100B514000/4-3 Aspect Ratio"),
        AspectGame(Regex("skyward\\s*sword", RegexOption.IGNORE_CASE), "01002DA013484000/4-3 Aspect Ratio"),
        AspectGame(Regex("mario\\s*3d\\s*world", RegexOption.IGNORE_CASE), "010028600EBDA000/4-3 Aspect Ratio"),
        AspectGame(Regex("link.?s\\s*awakening", RegexOption.IGNORE_CASE), "01006BB00C6F0000/4-3 Aspect Ratio"),
        AspectGame(Regex("echoes\\s*of\\s*wisdom", RegexOption.IGNORE_CASE), "01008CF01BAAC000/4-3 Aspect Ratio"),
        AspectGame(Regex("metroid.*prime\\s*4", RegexOption.IGNORE_CASE), "010019A01E2F2000/4-3 Aspect Ratio"),
        AspectGame(Regex("breath\\s*of\\s*the\\s*wild", RegexOption.IGNORE_CASE), "01007EF00011E000/4-3 Aspect Ratio"),
        AspectGame(Regex("tears\\s*of\\s*the\\s*kingdom", RegexOption.IGNORE_CASE), "0100F2C0115B6000/4-3 Aspect Ratio"),
        AspectGame(Regex("animal\\s*crossing", RegexOption.IGNORE_CASE), "01006F8002326000/4-3 Aspect Ratio"),
        AspectGame(Regex("metroid\\W*dread", RegexOption.IGNORE_CASE), "010093801237C000/4-3 Aspect Ratio"),
        AspectGame(Regex("pok.mon.*brilliant\\s*diamond", RegexOption.IGNORE_CASE), "0100000011D90000/4-3 Aspect Ratio"),
        AspectGame(Regex("mario\\s*bros\\.?\\s*u\\s*deluxe", RegexOption.IGNORE_CASE), "0100EA80032EA000/4-3 Aspect Ratio"),
    )

    /**
     * The launch title is the raw file name minus extension. The game regexes use `\\s*` between words, so "Super_Mario_Odyssey" or
     * "Super Mario Odyssey (USA) [v1.3.0]" must be cleaned the way the library's own title is: brackets removed, `_` as a space.
     */
    internal fun matchTitle(raw: String): String = com.noryan.nebulos.api.TitleMatch.clean(raw)

    /** Games that run at a fixed rate and never fast-forward: Pokemon Brilliant Diamond is presented at 60 FPS by the emulator itself (swap interval forced to 1). */
    private val NO_FAST_FORWARD = Regex("pok.mon.*brilliant\\s*diamond", RegexOption.IGNORE_CASE)
    fun noFastForward(gameTitle: String) = NO_FAST_FORWARD.containsMatchIn(matchTitle(gameTitle))

    /**
     * Link's Awakening (Switch) runs on dynarmic instead of NCE: with NCE its camera alternates between two positions every frame while
     * Link moves. Dynarmic is slower, but the picture is steady.
     */
    private val LINKS_AWAKENING = Regex("link.?s\\s*awakening", RegexOption.IGNORE_CASE)
    fun forceDynarmic(gameTitle: String) = LINKS_AWAKENING.containsMatchIn(matchTitle(gameTitle))

    private fun aspectGame(gameTitle: String) = matchTitle(gameTitle).let { t -> ASPECT_GAMES.firstOrNull { it.title.containsMatchIn(t) } }
    /** The 4:3 mod folder (under load/) a game title selects, or null. */
    internal fun aspectDirName(gameTitle: String): String? = aspectGame(gameTitle)?.dir
    private fun aspectDir(ctx: Context, g: AspectGame) = ctx.getExternalFilesDir(null)?.let { File(it, "load/${g.dir}") }

    /**
     * True when [gameTitle] has its 4:3 mod installed (always the case for a game in [ASPECT_GAMES], see [install]). The patch makes the
     * game draw a 4:3 view into Eden's fixed 16:9 picture, which looks stretched sideways; the host stretches the picture back up to the
     * full screen height so the proportions are right again.
     */
    fun fillsScreen(ctx: Context, gameTitle: String): Boolean =
        aspectGame(gameTitle)?.let { aspectDir(ctx, it)?.isDirectory == true } == true

    fun install(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val load = File(ctx.getExternalFilesDir(null) ?: return, "load")
            val done = prefs.getStringSet(KEY, null)?.toMutableSet()
                // First two mods shipped under the old single "installed" flag.
                ?: (if (prefs.getInt("installed", 0) >= 1) mutableSetOf("01006BB00C6F0000/NoDOF", "01008CF01BAAC000/NoDOF") else mutableSetOf())
            val assets = ctx.assets
            for (title in assets.list(ROOT).orEmpty()) for (mod in assets.list("$ROOT/$title").orEmpty()) {
                val id = "$title/$mod"
                if (id in done || ASPECT_GAMES.any { it.dir == id }) continue
                val dest = File(load, id)
                if (!dest.exists()) copyTree(ctx, "$ROOT/$title/$mod", dest)
                done += id
            }
            prefs.edit().putStringSet(KEY, done).apply()
            // The 4:3 patches are always on and improve between versions: refresh them on every start.
            for (g in ASPECT_GAMES) aspectDir(ctx, g)?.let { copyTree(ctx, "$ROOT/${g.dir}", it) }
        }
    }

    private fun copyTree(ctx: Context, asset: String, dest: File) {
        val children = ctx.assets.list(asset).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            ctx.assets.open(asset).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dest.mkdirs()
            for (c in children) copyTree(ctx, "$asset/$c", File(dest, c))
        }
    }

    // --- Skyward Sword HD: the pause screen's frozen-scene backdrop ------------------------------------------------------------
    // The 4:3 mod makes the game render a 4:3 frame, but the pause menu shows that frame in a 16:9 picture pane (Layout/MenuPause.arc,
    // layout pauseBace_00, picture P_capture_02), which squashes it. Crop the picture to the middle 75% of the frame (4:3 -> 16:9) via
    // its texture coordinates. The archive is the game's own data, so it is never shipped: it is extracted from the player's copy on
    // the first launch and patched here, then used as a LayeredFS file of the 4:3 mod.

    private const val SSHD_ID = "01002DA013484000"
    private const val SSHD_MOD = "4-3 Aspect Ratio"
    private const val PAUSE_ARC = "Layout/MenuPause.arc"

    /** Runs in the game process right before the game loads ([org.yuzu.yuzu_emu.utils.NebulosBridge.beforeLoad]). */
    fun prepareRomfs(ctx: Context, gameTitle: String, gamePath: String) {
        if (aspectGame(gameTitle)?.dir?.startsWith(SSHD_ID) != true) return
        val modDir = File(ctx.getExternalFilesDir(null) ?: return, "load/$SSHD_ID/$SSHD_MOD")
        if (!modDir.isDirectory) return // the player deleted the mod: leave it off
        val target = File(modDir, "romfs/$PAUSE_ARC")
        if (target.exists()) return
        val tmp = File(ctx.cacheDir, "romfs_extract").also { it.deleteRecursively(); it.mkdirs() }
        try {
            val res = org.yuzu.yuzu_emu.NativeLibrary.dumpRomfsFile(gamePath, PAUSE_ARC, tmp.absolutePath)
            val src = File(tmp, "romfs/$PAUSE_ARC")
            if (!res.contains("\"ok\":true") || !src.isFile) return
            val bytes = src.readBytes()
            if (!cropPauseCapture(bytes)) return
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.writeBytes(bytes)
            part.renameTo(target)
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** Sets P_capture_02's texture coordinates to the middle 75% of the frame; false (nothing changed) if the file is not what we expect. */
    internal fun cropPauseCapture(b: ByteArray): Boolean {
        val pic = byteArrayOf('p'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte())
        val name = "P_capture_02".toByteArray()
        var i = 0
        while (i + 0x60 + 32 <= b.size) {
            // 'pic1' section: magic, size, 4 flag bytes, 16-byte name; texture coordinates follow the colours, material and count.
            if (b[i] == pic[0] && b[i + 1] == pic[1] && b[i + 2] == pic[2] && b[i + 3] == pic[3] && matches(b, i + 12, name)) {
                val t = i + 0x60
                val expect = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
                for (k in 0 until 8) if (readBeFloat(b, t + 4 * k) != expect[k]) return false
                writeBeFloat(b, t + 4, 0.125f); writeBeFloat(b, t + 12, 0.125f)
                writeBeFloat(b, t + 20, 0.875f); writeBeFloat(b, t + 28, 0.875f)
                return true
            }
            i++
        }
        return false
    }

    private fun matches(b: ByteArray, at: Int, s: ByteArray): Boolean {
        for (k in s.indices) if (b[at + k] != s[k]) return false
        return b[at + s.size].toInt() == 0
    }
    private fun readBeFloat(b: ByteArray, at: Int) =
        java.lang.Float.intBitsToFloat(((b[at].toInt() and 255) shl 24) or ((b[at + 1].toInt() and 255) shl 16) or ((b[at + 2].toInt() and 255) shl 8) or (b[at + 3].toInt() and 255))
    private fun writeBeFloat(b: ByteArray, at: Int, v: Float) {
        val x = java.lang.Float.floatToIntBits(v)
        b[at] = (x ushr 24).toByte(); b[at + 1] = (x ushr 16).toByte(); b[at + 2] = (x ushr 8).toByte(); b[at + 3] = x.toByte()
    }
}
