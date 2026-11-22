package com.noryan.nebulos.data

import android.content.Context
import android.net.Uri

/**
 * Key / firmware / BIOS import helpers that individual cores expose, reached by reflection so :app still builds with a
 * subset of cores (-Pnebulos.cores=...). Anything a core does not have is simply not offered in Settings.
 */
object CoreSetup {
    /** One importable thing: [status] is shown on the right of its line, [accept] are the mime types for the picker. */
    data class Item(val coreId: String, val key: String, val label: String, val accept: Array<String>)

    val ITEMS = listOf(
        Item("eden", "keys", "IMPORT PROD.KEYS", arrayOf("*/*")),
        Item("eden", "firmware", "IMPORT FIRMWARE (.ZIP)", arrayOf("application/zip", "application/x-zip-compressed", "*/*")),
        Item("armsx2", "bios", "IMPORT PS2 BIOS", arrayOf("*/*")),
    )

    fun itemsFor(coreId: String) = ITEMS.filter { it.coreId == coreId }

    private fun obj(cls: String): Any? = runCatching { Class.forName(cls).getField("INSTANCE").get(null) }.getOrNull()

    private fun call(o: Any?, name: String, vararg a: Any?): Any? {
        if (o == null) return null
        val m = o.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == a.size } ?: return null
        return runCatching { m.invoke(o, *a) }.getOrNull()
    }

    private val eden get() = obj("com.noryan.nebulos.core.eden.EdenSetup")
    private val armsx2 get() = obj("com.noryan.nebulos.core.armsx2.Armsx2Core")

    /** "IMPORTED" / "MISSING" for an item (null = unknown). */
    fun status(ctx: Context, it: Item): String? {
        val ok = when (it.key) {
            "keys" -> call(eden, "isKeysImported", ctx)
            "firmware" -> call(eden, "isFirmwareImported", ctx)
            "bios" -> call(armsx2, "hasBios", ctx)
            else -> null
        } as? Boolean ?: return null
        return if (ok) "IMPORTED" else "MISSING"
    }

    fun import(ctx: Context, it: Item, uri: Uri): Boolean = when (it.key) {
        "keys" -> call(eden, "importKeys", ctx, uri, "prod.keys")
        "firmware" -> call(eden, "importFirmware", ctx, uri)
        "bios" -> call(armsx2, "importBios", ctx, uri, null)
        else -> null
    } as? Boolean ?: false

    // ---- BiosImporter hooks (file level, reflective like the rest) ----
    fun armsx2BiosDir(ctx: Context): java.io.File? = call(armsx2, "biosDir", ctx) as? java.io.File
    @Suppress("UNCHECKED_CAST")
    fun dsBiosImport(ctx: Context, files: List<java.io.File>): List<String> =
        call(obj("com.noryan.nebulos.core.watermelonds.WatermelonDsCore"), "importBios", ctx, files) as? List<String> ?: emptyList()
    fun edenPresent() = eden != null
    fun edenKeysFile(ctx: Context): java.io.File? = call(eden, "keysFile", ctx) as? java.io.File
    fun edenFirmwareDir(ctx: Context): java.io.File? = call(eden, "firmwareDir", ctx) as? java.io.File
    fun edenFirmwareInstalled(ctx: Context): Boolean? = call(eden, "isFirmwareImported", ctx) as? Boolean
    fun edenImportFirmware(ctx: Context, zip: Uri): Boolean = call(eden, "importFirmware", ctx, zip) as? Boolean ?: false

    /** Wii U unpacked-title-folder test (code + content + meta); falls back to the same rule if :core-cemu is absent. */
    fun isWiiUTitleFolder(childNames: Collection<String>): Boolean {
        val r = call(obj("com.noryan.nebulos.core.cemu.CemuCore"), "isTitleFolder", childNames) as? Boolean
        if (r != null) return r
        return false
    }

    fun cemuPresent() = obj("com.noryan.nebulos.core.cemu.CemuCore") != null

    /** PrimeHack's own title rule (covers "Metroid - Prime Trilogy", "Metroid Prime 2", ...). */
    fun isPrimeTitle(title: String): Boolean =
        (call(obj("com.noryan.nebulos.core.primehack.PrimeHackCore"), "isPrimeTitle", title) as? Boolean)
            ?: Regex("metroid\\s*[-:._ ]*\\s*prime|prime\\s*(2|3)\\b|prime trilogy", RegexOption.IGNORE_CASE).containsMatchIn(title)
}

/**
 * One-game cores: Dusklight (Twilight Princess) is recognised by its GameCube disc ID, Wind Waker HD by its title (a Wii U `.wua` has no
 * cheap disc ID). Each core's own descriptor decides (reflective, optional modules); the plain rules below are the fallback.
 */
object GameSpecificCores {
    private val TP_GC_IDS = setOf("GZ2E01", "GZ2P01", "GZ2J01")
    private val MM_TITLE = Regex("majora", RegexOption.IGNORE_CASE)
    private val OOT_TITLE = Regex("ocarina\\s*of\\s*time", RegexOption.IGNORE_CASE)
    private val WWHD_TITLE = Regex("wind\\s*waker", RegexOption.IGNORE_CASE)   // only reached for Wii U games (candidates are per system), where it is always the HD version

    /** Cores that can only run particular discs, id -> predicate on the 6-char disc game ID (null = unknown ID). */
    private val JAK_SERIALS = setOf("SCUS-97124", "SCES-50361", "SCPS-15021", "SCPS-56003", "SCUS-97265", "SCES-51608", "SCPS-15057",
        "SCKA-20010", "SCUS-97330", "SCUS-97516", "SCKA-20040", "SCES-52460")
    private val matchers: Map<String, (String?) -> Boolean> = mapOf("dusklight" to ::isTwilightPrincess, "jak" to ::isJakAndDaxter, "sotc" to ::isShadowOfTheColossus, "darkcloud" to ::isDarkCloud, "darkchronicle" to ::isDarkChronicle, "ico" to ::isIco, "galaxy" to ::isSuperMarioGalaxy, "sunshine" to ::isSuperMarioSunshine, "echoes" to ::isMetroidPrime2Echoes, "prime1" to ::isMetroidPrime1)

    /** Cores that can only run particular games recognised by name, id -> predicate on the game's title. */
    private val titleMatchers: Map<String, (String?) -> Boolean> = mapOf("wwhd" to ::isWindWakerHd, "shipwright" to ::isOcarinaOfTime, "twoship" to ::isMajorasMask, "botw" to ::isBotw, "totk" to ::isTotk, "smo" to ::isSmo, "dread" to ::isMetroidDread, "mp4" to ::isMetroidPrime4, "sm3dw" to ::isSuperMario3dWorld, "las" to ::isLinksAwakening, "sshd" to ::isSkywardSwordHd, "wonder" to ::isSuperMarioWonder, "eow" to ::isEchoesOfWisdom, "sm64" to ::isSuperMario64)

    fun isTwilightPrincess(gameId: String?): Boolean {
        if (gameId == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.dusklight.DusklightCore").let { c ->
                c.getMethod("isTwilightPrincessGameId", String::class.java).invoke(c.getField("INSTANCE").get(null), gameId) as? Boolean
            }
        }.getOrNull()
        return r ?: (gameId in TP_GC_IDS)
    }

    /** Jak and Daxter trilogy PS2 discs (OpenGOAL core), recognised by the serial the scanner reads from SYSTEM.CNF. */
    fun isJakAndDaxter(serial: String?): Boolean {
        if (serial == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.jak.JakCore").let { c ->
                c.getMethod("isJakSerial", String::class.java).invoke(c.getField("INSTANCE").get(null), serial) as? Boolean
            }
        }.getOrNull()
        return r ?: (serial in JAK_SERIALS)
    }

    /** Shadow of the Colossus, USA PS2 disc (Celosia core), recognised by the serial the scanner reads from SYSTEM.CNF. */
    fun isShadowOfTheColossus(serial: String?): Boolean {
        if (serial == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.sotc.SotcCore").let { c ->
                c.getMethod("isSotcSerial", String::class.java).invoke(c.getField("INSTANCE").get(null), serial) as? Boolean
            }
        }.getOrNull()
        return r ?: (serial == "SCUS-97472")
    }

    /** Dark Cloud (the first game), USA PS2 disc (Chronicle core), recognised by the serial the scanner reads from SYSTEM.CNF. */
    fun isDarkCloud(serial: String?): Boolean {
        if (serial == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.darkcloud.DarkCloudCore").let { c ->
                c.getMethod("isDarkCloudSerial", String::class.java).invoke(c.getField("INSTANCE").get(null), serial) as? Boolean
            }
        }.getOrNull()
        return r ?: (serial == "SCUS-97111")
    }

    /** Dark Cloud 2 / Dark Chronicle, PAL PS2 disc only (ChronicleTwo core), recognised by the serial the scanner reads from SYSTEM.CNF. */
    fun isDarkChronicle(serial: String?): Boolean {
        if (serial == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.darkchronicle.DarkChronicleCore").let { c ->
                c.getMethod("isDarkChronicleSerial", String::class.java).invoke(c.getField("INSTANCE").get(null), serial) as? Boolean
            }
        }.getOrNull()
        return r ?: (serial == "SCES-51190")
    }

    /** ICO (any of the seven discs ico-pc accepts), recognised by the serial the scanner reads from SYSTEM.CNF. */
    fun isIco(serial: String?): Boolean {
        if (serial == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.ico.IcoCore").let { c ->
                c.getMethod("isIcoSerial", String::class.java).invoke(c.getField("INSTANCE").get(null), serial) as? Boolean
            }
        }.getOrNull()
        return r ?: (serial == "SCUS-97113")
    }

    /** Super Mario Galaxy (revision 0: RMGE01 / RMGP01 / RMGJ01; Nebula core), recognised by the Wii disc's game ID. */
    fun isSuperMarioGalaxy(gameId: String?): Boolean {
        if (gameId == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.galaxy.GalaxyCore").let { c ->
                c.getMethod("isGalaxyGameId", String::class.java).invoke(c.getField("INSTANCE").get(null), gameId) as? Boolean
            }
        }.getOrNull()
        return r ?: (gameId.uppercase() in setOf("RMGE01", "RMGP01", "RMGJ01"))
    }

    /** Super Mario Sunshine (US revision 0: GMSE01; PC port core), recognised by the GameCube disc's game ID. */
    fun isSuperMarioSunshine(gameId: String?): Boolean {
        if (gameId == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.sunshine.SunshineCore").let { c ->
                c.getMethod("isSunshineGameId", String::class.java).invoke(c.getField("INSTANCE").get(null), gameId) as? Boolean
            }
        }.getOrNull()
        return r ?: (gameId.uppercase() == "GMSE01")
    }

    /** Metroid Prime 2: Echoes (G2ME01 / G2MP01, and the Trilogy discs R3ME01 / R3MP01 the port plays as Echoes; PC port core), recognised by the disc's game ID. */
    fun isMetroidPrime2Echoes(gameId: String?): Boolean {
        if (gameId == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.echoes.EchoesCore").let { c ->
                c.getMethod("isEchoesGameId", String::class.java).invoke(c.getField("INSTANCE").get(null), gameId) as? Boolean
            }
        }.getOrNull()
        return r ?: (gameId.uppercase() in setOf("G2ME01", "G2MP01"))
    }

    /** Metroid Prime (GM8E01 USA / GM8P01 PAL, and the Trilogy discs R3ME01 / R3MP01 whose Metroid Prime the port plays; PC port core), recognised by the disc's game ID. The Japanese disc (GM8J01) is not supported by the port. */
    fun isMetroidPrime1(gameId: String?): Boolean {
        if (gameId == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.prime1.Prime1Core").let { c ->
                c.getMethod("isPrime1GameId", String::class.java).invoke(c.getField("INSTANCE").get(null), gameId) as? Boolean
            }
        }.getOrNull()
        return r ?: (gameId.uppercase() in setOf("GM8E01", "GM8P01"))
    }

    /** Ship of Harkinian runs only the N64 Ocarina of Time ROMs (recognised by title: an N64 ROM has no disc ID the scanner stores). */
    fun isOcarinaOfTime(title: String?): Boolean {
        if (title == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.shipwright.ShipwrightCore").let { c ->
                c.getMethod("isOcarinaOfTimeTitle", String::class.java).invoke(c.getField("INSTANCE").get(null), title) as? Boolean
            }
        }.getOrNull()
        return r ?: OOT_TITLE.containsMatchIn(title)
    }

    /** 2 Ship 2 Harkinian runs only the N64 Majora's Mask ROMs (recognised by title, like Ocarina of Time). */
    fun isMajorasMask(title: String?): Boolean {
        if (title == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.twoship.TwoshipCore").let { c ->
                c.getMethod("isMajorasMaskTitle", String::class.java).invoke(c.getField("INSTANCE").get(null), title) as? Boolean
            }
        }.getOrNull()
        return r ?: MM_TITLE.containsMatchIn(title)
    }

    fun isWindWakerHd(title: String?): Boolean {
        if (title == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.wwhd.WwhdCore").let { c ->
                c.getMethod("isWindWakerHdTitle", String::class.java).invoke(c.getField("INSTANCE").get(null), title) as? Boolean
            }
        }.getOrNull()
        return r ?: WWHD_TITLE.containsMatchIn(title)
    }

    /** Super Mario 64 (N64; Ghostship core), recognised by title (the N64 list has no disc ID); the ROM hash is checked when the game starts. */
    fun isSuperMario64(title: String?): Boolean {
        if (title == null) return false
        val r = runCatching {
            Class.forName("com.noryan.nebulos.core.sm64.Sm64Core").let { c ->
                c.getMethod("isSuperMario64Title", String::class.java).invoke(c.getField("INSTANCE").get(null), title) as? Boolean
            }
        }.getOrNull()
        return r ?: Regex("super\\s*mario\\s*64(?!\\s*ds)", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }

    fun isBotw(title: String?): Boolean = title != null && Regex("breath\\s*of\\s*the\\s*wild", RegexOption.IGNORE_CASE).containsMatchIn(title)

    fun isSmo(title: String?): Boolean = title != null && Regex("super\\s*mario\\s*odyssey", RegexOption.IGNORE_CASE).containsMatchIn(title)

    fun isMetroidDread(title: String?): Boolean = title != null && Regex("metroid\\W*dread", RegexOption.IGNORE_CASE).containsMatchIn(title)

    /** Metroid Prime 4: Beyond (Switch; its own core, the Prime 4 runtime), recognised by title. */
    /** The Legend of Zelda: Echoes of Wisdom (Switch; its own core, the Odyssey runtime), recognised by title (not Metroid Prime 2 Echoes: that core matches the GameCube disc ID). */
    fun isEchoesOfWisdom(title: String?): Boolean = title != null && Regex("echoes\\W*of\\W*wisdom", RegexOption.IGNORE_CASE).containsMatchIn(title)

    /** The Legend of Zelda: Skyward Sword HD (Switch; its own core, the Odyssey runtime), recognised by title (the Wii game is another system). */
    fun isSkywardSwordHd(title: String?): Boolean = title != null && Regex("skyward\\W*sword", RegexOption.IGNORE_CASE).containsMatchIn(title)

    /** The Legend of Zelda: Link's Awakening (Switch remake; the native runtime core claims it by title, Eden stays selectable). */
    fun isLinksAwakening(title: String?): Boolean = title != null && Regex("link.?s\\W*awakening", RegexOption.IGNORE_CASE).containsMatchIn(title)

    fun isSuperMario3dWorld(title: String?): Boolean = title != null && Regex("3d\\W*world|bowser", RegexOption.IGNORE_CASE).containsMatchIn(title)
    fun isMetroidPrime4(title: String?): Boolean = title != null && Regex("metroid\\W*prime\\W*4", RegexOption.IGNORE_CASE).containsMatchIn(title)
    /** Super Mario Bros. Wonder (Switch; its own core, the Odyssey runtime), recognised by title. */
    fun isSuperMarioWonder(title: String?): Boolean = title != null && Regex("mario\\W*bros\\W*wonder", RegexOption.IGNORE_CASE).containsMatchIn(title)

    fun isTotk(title: String?): Boolean = title != null && Regex("tears\\s*of\\s*the\\s*kingdom", RegexOption.IGNORE_CASE).containsMatchIn(title)

    /** Cores that run a game as a native port / recompilation instead of emulating the console (their games are tinted cyan in the list). */
    private val NATIVE_PORTS = setOf("darkcloud", "darkchronicle", "ico", "smo", "jak", "sotc", "botw", "totk", "wwhd", "dusklight", "shipwright", "twoship", "galaxy", "sunshine", "dread", "mp4", "sshd", "echoes", "prime1", "sm64", "wonder", "sm3dw", "las", "eow")
    fun isNativePort(coreId: String) = coreId in NATIVE_PORTS

    fun isGameSpecific(coreId: String) = coreId in matchers || coreId in titleMatchers

    /** True when [coreId] may run this game (cores that are not game-specific can run anything). [title] is the game's name. */
    fun canRun(coreId: String, gameId: String?, title: String? = null): Boolean =
        matchers[coreId]?.invoke(gameId) ?: titleMatchers[coreId]?.invoke(title) ?: true

    /** True when [coreId] is a one-game core and this IS its game: it then wins the automatic choice. */
    fun claims(coreId: String, gameId: String?, title: String?): Boolean =
        // Echoes can also play the Metroid Prime Trilogy discs (R3ME01 / R3MP01, as Echoes), but only Echoes' own discs are claimed: the Trilogy stays with PrimeHack by default.
        if (coreId == "echoes") gameId != null && gameId.uppercase() in setOf("G2ME01", "G2MP01")
        // Same for the Metroid Prime port: only the GameCube discs are claimed, the Trilogy (R3ME01 / R3MP01) stays with PrimeHack by default and the port is a per-game choice for it.
        else if (coreId == "prime1") gameId != null && gameId.uppercase() in setOf("GM8E01", "GM8P01")
        else matchers[coreId]?.let { gameId != null && it(gameId) } ?: titleMatchers[coreId]?.invoke(title) ?: false
}
