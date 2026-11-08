package com.noryan.nebulos.core.watermelonds

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noryan.nebulos.api.CoreDescriptor
import com.noryan.nebulos.api.CoreRegistry
import com.noryan.nebulos.api.LaunchRequest
import com.noryan.nebulos.api.System
import com.noryan.nebulos.api.save.SaveMigration
import com.noryan.nebulos.api.save.SaveRoot
import me.magnum.melonds.nebulos.NebulosBridge
import me.magnum.melonds.ui.emulator.EmulatorActivity

/**
 * Nintendo DS / DSi via WatermelonDS (melonDS-android fork, GPLv3). Runs in its own `:melonds` process.
 *
 * Boots with melonDS's built-in HLE BIOS/firmware: no BIOS/firmware is bundled and none is required for most games
 * (users may add real dumps through WatermelonDS' own settings store; Nebulos never ships them). DSi-mode needs real
 * DSi BIOS/firmware/NAND dumps supplied by the user.
 *
 * The host Application MUST extend [NebulosHiltApplication] (WatermelonDS uses Dagger Hilt, which needs a
 * @HiltAndroidApp Application in the process). See the module report / README.
 */
object WatermelonDsCore : CoreDescriptor {
    override val id = "watermelonds"
    override val displayName = "WatermelonDS (Nintendo DS)"
    override val systems = setOf(System.NDS)
    override val version = "WatermelonDS 0.8.0.rc2 (melonDS)"
    override val extensions = setOf("nds", "dsi", "ids", "cia")   // cia: only DSiWare ones (sniff), the rest stay 3DS

    const val EXTRA_ROM_PATH = "nebulos.romPath"
    const val EXTRA_RESUME = "nebulos.resume"

    /** A `.cia` is a DS game only when it is DSiWare (3DS title ID 00048004-<game code>); everything else is left to the 3DS core. */
    override fun sniff(path: String, header: ByteArray): System? =
        if (path.endsWith(".cia", ignoreCase = true) && DsiWareCia.isDsiWare(header)) System.NDS else null

    /** BiosImporter hook (reflective): copies the user's DS/DSi BIOS, firmware and NAND dumps into WatermelonDS' directories. */
    fun importBios(ctx: Context, files: List<java.io.File>): List<String> = DsiBios.import(ctx, files)

    override fun launchIntent(ctx: Context, request: LaunchRequest): Intent {
        WatermelonDsDefaults.applyIfNeeded(ctx)
        // DSiWare .cia: the game inside is extracted once to a plain .nds, which WatermelonDS boots in DSi mode.
        var romPath = request.romPath
        if (romPath.endsWith(".cia", ignoreCase = true)) {
            val cia = java.io.File(romPath)
            val out = DsiWareCia.extractedFile(ctx, cia)
            val err = DsiWareCia.extract(cia, out) ?: DsiWareCia.writeDsiTmd(cia, out, DsiWareCia.tmdDir(ctx))
            if (err == null) romPath = out.absolutePath else android.util.Log.e("NebulosDsi", "DSiWare extract failed: $err")
        }
        // The bridge (RomUris.romDocumentFor) is built around plain file paths: prefer a readable real path, and only
        // fall back to the shell's SAF uri when the file is not directly readable.
        val real = java.io.File(romPath)
        val uri = if (real.isFile && real.canRead()) Uri.fromFile(real)
        else request.romUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: Uri.fromFile(real)
        return Intent(ctx, EmulatorActivity::class.java).apply {
            data = uri
            putExtra(NebulosBridge.EXTRA_NEBULOS, true)
            putExtra(EXTRA_ROM_PATH, romPath)
            putExtra(EXTRA_RESUME, request.resumeState)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun register() {
        CoreRegistry.register(this)
        // old locations of cartridge saves -> <root>/watermelonds/saves (copy + verify + delete, see SAVEDATA.md)
        SaveMigration.register(id, "ds saves (app storage)", "saves") { WatermelonDsSaves.legacyAppSaves(it) }
        SaveMigration.register(id, "ds saves next to ROM", "saves") { WatermelonDsSaves.adoptRomSideSaves(it) }
    }
}

/**
 * WatermelonDS's own SharedPreferences (written from the main process in [WatermelonDsCore.launchIntent], i.e. BEFORE the
 * :melonds process exists, so the engine reads the final values at its first init; values applied later would only take
 * effect one launch late).
 *
 * v1: hide the touch overlay (a controller is expected). v2: renderer moved Vulkan -> compute (5x). On the AYN Thor the
 * Vulkan backend costs 24-30 ms CPU per frame on the emulator thread (17-40 fps); compute 5x holds ~59.9 fps and renders
 * like Vulkan. Plain OpenGL is just as fast but drops the blob shadows under characters (checked on Phantom Hourglass).
 * The renderer/resolution are ENFORCED on every launch (Nebulos has no WatermelonDS settings UI), so installs that
 * still carry the old Vulkan value are moved off it too. Test/diagnostic escape hatch: set string
 * `renderer_override` (vulkan|opengl|compute|software) in the `nebulos_watermelonds` prefs file.
 */
internal object WatermelonDsDefaults {
    private const val MARK = "nebulos_defaults_v2"
    private const val RENDERER = "compute"
    private const val RESOLUTION = "5"

    fun applyIfNeeded(ctx: Context) {
        val mark = ctx.getSharedPreferences("nebulos_watermelonds", Context.MODE_PRIVATE)
        val renderer = mark.getString("renderer_override", null)?.takeIf { it.isNotBlank() } ?: RENDERER
        val prefs = ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE)
        val firstRun = !mark.getBoolean(MARK, false)
        // Portable saves: WatermelonDS keeps cartridge saves (<rom>.sav) in `sram_dir` when `use_rom_dir` is off. Enforced on
        // every launch (the save root can change); written here in the MAIN process, before :melonds reads the prefs.
        val saveDir = SaveRoot.coreSubDir(ctx, WatermelonDsCore.id, "saves")
        val saveUri = android.net.Uri.fromFile(saveDir).toString()
        val savesOk = !prefs.getBoolean("use_rom_dir", true) && prefs.getStringSet("sram_dir", null)?.singleOrNull() == saveUri
        // DSi mode (DSiWare) reads its BIOS/firmware/NAND from directories of the user's own dumps; set only once they are imported.
        // DS games keep the built-in BIOS: only a ROM that runs as DSi uses these.
        val dsUri = android.net.Uri.fromFile(DsiBios.dir(ctx, false)).toString()
        val dsiUri = android.net.Uri.fromFile(DsiBios.dir(ctx, true)).toString()
        val biosOk = !DsiBios.dsiReady(ctx) ||
            (prefs.getStringSet("bios_dir", null)?.singleOrNull() == dsUri && prefs.getStringSet("dsi_bios_dir", null)?.singleOrNull() == dsiUri)
        val needs = firstRun || !savesOk || !biosOk ||
            prefs.getString("video_renderer", null) != renderer ||
            prefs.getString("video_internal_resolution", null) != RESOLUTION
        if (!needs) return
        prefs.edit()
            .putString("soft_input_behaviour", "always_invisible")
            .putString("video_renderer", renderer)
            .putString("video_internal_resolution", RESOLUTION)
            .putBoolean("use_rom_dir", false)
            .putStringSet("sram_dir", setOf(saveUri))
            .apply { if (DsiBios.dsiReady(ctx)) putStringSet("bios_dir", setOf(dsUri)).putStringSet("dsi_bios_dir", setOf(dsiUri)) }
            .commit() // commit(): the :melonds process reads this file right after launch
        if (firstRun) mark.edit().putBoolean(MARK, true).commit()
    }
}
