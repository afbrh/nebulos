package com.noryan.nebulos.core.smw

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Builds `smw_assets.dat` from the user's own Super Mario World (USA) ROM.
 *
 * Kotlin port of the asset builder in snesrev/smw (MIT): `assets/restool.py`,
 * `assets/compile_resources.py` and `assets/util.py`. The output is byte for byte what
 * `restool.py --no-include-rom` writes for the unmodified US ROM. Only that ROM is
 * accepted, so the Lunar Magic and ROM hack branches of the original are not ported;
 * the assets they would fill are written empty, as the original does for this ROM.
 *
 * Only addresses, sizes and format constants live here. All game data is read from the ROM.
 */
object SmwAssets {
    private const val ROM_SIZE = 524288
    private const val COPIER_HEADER_SIZE = 512
    private const val ROM_SHA1 = "6b47bb75d16514b6a476aa0c73a683a2a4c18765"

    /**
     * Returns the contents of `smw_assets.dat`. [rom] may carry a 512-byte copier header.
     * [progress] is called on the calling thread with rising values from 0 to 1.
     *
     * @throws IllegalArgumentException if [rom] is not the supported ROM; the message is
     * meant to be shown to the user.
     */
    fun build(rom: ByteArray, progress: (Float) -> Unit = {}): ByteArray {
        val image = when (rom.size) {
            ROM_SIZE -> rom
            ROM_SIZE + COPIER_HEADER_SIZE -> rom.copyOfRange(COPIER_HEADER_SIZE, rom.size)
            else -> throw IllegalArgumentException(
                "This file is not the right size. Choose an unmodified Super Mario World (USA) ROM (512 KB)."
            )
        }
        val sha1 = MessageDigest.getInstance("SHA-1").digest(image)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(sha1 == ROM_SHA1) {
            "This ROM is not supported. Choose an unmodified Super Mario World (USA) ROM."
        }
        return Builder(Rom(image), progress).build()
    }

    /** Read access by SNES LoROM address. */
    private class Rom(private val data: ByteArray) {
        fun byte(addr: Int): Int {
            check(addr and 0x8000 != 0) { "Not a ROM address: 0x${Integer.toHexString(addr)}" }
            return data[((addr shr 16) and 0x7f) * 0x8000 + (addr and 0x7fff)].toInt() and 0xff
        }

        fun word(addr: Int): Int = byte(addr) or (byte(addr + 1) shl 8)

        fun long(addr: Int): Int = word(addr) or (byte(addr + 2) shl 16)

        /** [n] bytes starting at [addr], continuing into the next bank at the end of one. */
        fun bytes(addr: Int, n: Int): ByteArray {
            var a = addr
            return ByteArray(n) {
                val value = byte(a).toByte()
                a = intoBank(a + 1)
                value
            }
        }

        /** [n] little-endian words starting at [addr], as raw bytes. */
        fun words(addr: Int, n: Int): ByteArray {
            var a = addr
            val out = ByteArray(n * 2)
            for (i in 0 until n) {
                out[i * 2] = byte(a).toByte()
                out[i * 2 + 1] = byte(a + 1).toByte()
                a = intoBank(a + 2)
            }
            return out
        }

        private fun intoBank(addr: Int): Int = if (addr and 0x8000 == 0) addr + 0x8000 else addr
    }

    /** [data] is the decompressed output; [compressedLength] counts the terminator. */
    private class Decompressed(val data: ByteArray, val compressedLength: Int)

    private class Asset(val name: String, val data: ByteArray)

    private class Builder(private val rom: Rom, private val progress: (Float) -> Unit) {
        private val assets = ArrayList<Asset>()

        fun build(): ByteArray {
            progress(0f)
            addGraphics()
            progress(0.35f)
            addImagesAndAudio()
            progress(0.45f)
            addMap16AndPalettes()
            addMiscTables()
            progress(0.5f)
            addLevels()
            progress(0.7f)
            addSprites()
            progress(0.8f)
            addOverworldEvents()
            addGameTables()
            addOverworldTables()
            addLunarMagicPlaceholders()
            progress(0.9f)
            val file = assemble()
            progress(1f)
            return file
        }

        private fun add(name: String, data: ByteArray) {
            assets.add(Asset(name, data))
        }

        private fun bytes(name: String, addr: Int, n: Int) = add(name, rom.bytes(addr, n))

        private fun words(name: String, addr: Int, n: Int) = add(name, rom.words(addr, n))

        private fun empty(vararg names: String) {
            for (name in names) add(name, ByteArray(0))
        }

        // ---- Compression formats ----

        private fun decompress(start: Int): Decompressed {
            var ea = start
            var out = ByteArray(0x4000)
            var size = 0
            fun next(): Int {
                val value = rom.byte(ea)
                ea++
                if (ea and 0xffff == 0) ea += 0x8000
                return value
            }
            fun put(value: Int) {
                if (size == out.size) out = out.copyOf(size * 2)
                out[size++] = value.toByte()
            }
            while (true) {
                val b = next()
                if (b == 0xff) {
                    return Decompressed(out.copyOf(size), (ea - start) and 0x7fff)
                }
                val command: Int
                var length: Int
                if (b and 0xe0 != 0xe0) {
                    command = b and 0xe0
                    length = b and 0x1f
                } else {
                    command = (b shl 3) and 0xe0
                    length = ((b and 3) shl 8) or next()
                }
                length++
                when {
                    command == 0x00 -> repeat(length) { put(next()) } // literal
                    command and 0x80 != 0 -> { // copy from earlier output (big-endian offset)
                        val high = next()
                        var offset = (high shl 8) or next()
                        repeat(length) {
                            check(offset < size) { "Bad back-reference in compressed data" }
                            put(out[offset++].toInt())
                        }
                    }
                    command == 0x20 -> { // byte fill
                        val value = next()
                        repeat(length) { put(value) }
                    }
                    command == 0x40 -> { // alternating byte fill
                        val first = next()
                        val second = next()
                        for (i in 0 until length) put(if (i and 1 == 0) first else second)
                    }
                    else -> { // incrementing fill
                        val value = next()
                        for (i in 0 until length) put(value + i)
                    }
                }
            }
        }

        /** The still-compressed bytes of the block at [addr]. */
        private fun compressedBlock(addr: Int): ByteArray =
            rom.bytes(addr, decompress(addr).compressedLength)

        private fun stripeImageLength(start: Int): Int {
            var ea = start
            while (true) {
                if (rom.byte(ea) and 0x80 != 0) return ea + 1 - start
                val p2 = rom.byte(ea + 2)
                val p3 = rom.byte(ea + 3)
                ea += 4
                ea += if (p2 and 0x40 != 0) 2 else (((p2 shl 8) or p3) and 0x3fff) + 1
            }
        }

        /** Source length of an RLE stream that ends with the word 0xFFFF. */
        private fun rleLength(start: Int): Int {
            var ea = start
            while (rom.word(ea) != 0xffff) {
                val x = rom.byte(ea++)
                ea += if (x and 0x80 != 0) 1 else (x and 0x7f) + 1
            }
            return ea + 2 - start
        }

        /** Source length of an RLE stream that expands to at least [size] bytes. */
        private fun rleLengthForSize(start: Int, size: Int): Int {
            var ea = start
            var produced = 0
            while (produced < size) {
                val x = rom.byte(ea++)
                val count = (x and 0x7f) + 1
                ea += if (x and 0x80 != 0) 1 else count
                produced += count
            }
            return ea - start
        }

        private fun levelLength(start: Int): Int {
            var ea = start + 5 // level header
            while (true) {
                val b0 = rom.byte(ea++)
                if (b0 == 0xff) break
                val b1 = rom.byte(ea++)
                val b2 = rom.byte(ea++)
                val id = (b1 shr 4) or ((b0 and 0x60) shr 1)
                ea += when {
                    id == 0 && b2 == 0 -> 1
                    id == 0x22 || id == 0x23 -> 1
                    id == 0x27 -> 2
                    else -> 0
                }
            }
            return ea - start
        }

        private fun spriteDataLength(start: Int): Int {
            var ea = start + 1
            while (rom.byte(ea) != 0xff) ea += 3
            return ea + 1 - start
        }

        // ---- Container formats ----

        /** An indexed array with identical entries stored once. */
        private fun packArrays(items: List<ByteArray>): ByteArray {
            if (items.isEmpty()) return ByteArray(0)
            check(items.size <= 4096)
            val indexOfContent = HashMap<ByteBuffer, Int>()
            val unique = ArrayList<ByteArray>()
            val itemIndex = IntArray(items.size) { i ->
                indexOfContent.getOrPut(ByteBuffer.wrap(items[i])) {
                    unique.add(items[i])
                    unique.size - 1
                }
            }
            // Start offsets of every unique entry but the first.
            val offsets = IntArray(unique.size - 1)
            var end = 0
            for (i in offsets.indices) {
                end += unique[i].size
                offsets[i] = end
            }
            var flags = items.size - 1
            val narrow = offsets.isEmpty() || offsets.last() < 65536
            if (narrow) flags = flags or 0x8000

            val out = Output()
            for (offset in offsets) if (narrow) out.u16(offset) else out.u32(offset)
            for (item in unique) out.raw(item)
            if (unique.size != items.size) {
                for (index in itemIndex) if (offsets.size <= 255) out.u8(index) else out.u16(index)
                out.u16(offsets.size)
                flags = flags or 0x4000
            }
            out.u16(flags)
            return out.toByteArray()
        }

        /** ROM ranges (start to exclusive end), merged and looked up by SNES address. */
        private fun packBlob(ranges: List<Pair<Int, Int>>): ByteArray {
            val merged = ArrayList<Pair<Int, Int>>()
            for (range in ranges.sortedWith(compareBy({ it.first }, { it.second }))) {
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.second) {
                    if (range.second > last.second) merged[merged.size - 1] = last.first to range.second
                } else {
                    merged.add(range)
                }
            }
            val out = Output()
            out.u16(merged.size)
            for ((a, _) in merged) out.u24(a)
            var offset = 2 + merged.size * 6
            for ((a, b) in merged) {
                out.u24(offset)
                offset += b - a
            }
            for ((a, b) in merged) out.raw(rom.bytes(a, b - a))
            return out.toByteArray()
        }

        private fun assemble(): ByteArray {
            val keys = Output()
            for (asset in assets) {
                keys.raw(asset.name.toByteArray(Charsets.UTF_8))
                keys.u8(0)
            }
            val keyBytes = keys.toByteArray()

            val out = Output()
            out.raw("Smw_v0        \n\u0000".toByteArray(Charsets.US_ASCII))
            out.raw(MessageDigest.getInstance("SHA-256").digest(keyBytes))
            out.raw(ByteArray(32))
            out.u32(assets.size)
            out.u32(keyBytes.size)
            for (asset in assets) out.u32(asset.data.size)
            out.raw(keyBytes)
            for (asset in assets) {
                out.alignTo4()
                out.raw(asset.data)
            }
            return out.toByteArray()
        }

        // ---- Assets. The order of the calls is part of the file format. ----

        private fun addGraphics() {
            val low = rom.bytes(0xB992, 50)
            val high = rom.bytes(0xB9C4, 50)
            val bank = rom.bytes(0xB9F6, 50)
            add("kGraphicsPtrs", packArrays(List(50) { i ->
                compressedBlock(
                    ((bank[i].toInt() and 0xff) shl 16) or
                        ((high[i].toInt() and 0xff) shl 8) or
                        (low[i].toInt() and 0xff)
                )
            }))
            add("kGfx32", decompress(0x80000 or rom.word(0xB8D8)).data)
            add("kGfx33", decompress(0x80000 or rom.word(0xB88B)).data)
        }

        private fun addImagesAndAudio() {
            fun stripeImage(p: Int) = rom.bytes(p, stripeImageLength(p))

            add("kLoadStripeImagePtrs", packArrays(List(86) { i ->
                if (i == 0) ByteArray(0) else stripeImage(rom.long(0x84D0 + i * 3))
            }))
            add("kLayer3ImagePtrs", packArrays(List(45) { i -> stripeImage(rom.long(0x59000 + i * 3)) }))

            bytes("kSpcCreditsMusicBank", 0x3E400, 6624)
            bytes("kSpcLevelMusicBank", 0xEAED6, 16899)
            add("kSpcEngine", rom.bytes(0xE8000, 6321).copyOf(6321 + 2)) // two zero bytes appended
            bytes("kSpcSamples", 0xF8000, 28538)
            bytes("kSpcOverworldMusicBank", 0xE98B1, 5667)
        }

        private fun addMap16AndPalettes() {
            words("kMap16Data_OverworldLayer1", 0x05D000, 772)
            words("kMap16Data", 0xD8000, (0xA100 - 0x8000) / 2)
            words("kMap16Data_Castle", 0xDBC00, 712)
            words("kMap16Data_Rope", 0xDC800, 712)
            words("kMap16Data_Underground", 0xDD400, 712)
            words("kMap16Data_GhostHouse", 0xDE300, 712)

            words("kGlobalPalettes_Sky", 0x00B0A0, 16)
            words("kGlobalPalettes_Background", 0x00B0B0, 96)
            words("kGlobalPalettes_Layer3", 0xB170, 16)
            words("kGlobalPalettes_Foreground", 0x00B190, 96)
            words("kGlobalPalettes_Objects", 0x00B250, 60)
            words("kPlayerPalettes", 0x00B2C8, 40)
            words("kGlobalPalettes_Sprites", 0x00B318, 84)
            words("kGlobalPalettes_YoshiBerry", 0x00B674, 21)
            words("kGlobalPalettes_Flashing", 0x00B60C, 16)

            words("kGlobalPalettes_OW_Objects", 0xB528, 42)
            words("kGlobalPalettes_OW_Sprites", 0xB57C, 56)
            words("kGlobalPalettes_B5EC", 0xB5EC, 16)
            words("kGlobalPalettes_OW_Areas", 0xB3D8, 168)
            words("kGlobalPalettes_OW_AreasPassed", 0xB732, 168)
            words("kGlobalPalettes_Bowser", 0xB69E, 56)
            words("kGlobalPalettes_Layer3Smasher", 0xB66C, 4)
        }

        private fun addMiscTables() {
            bytes("kGameMode1B_EndingCinema_Tilemaps", 0xC95C7, 1873)
            words("kGameMode1B_EndingCinema_RowPointers", 0xC9D18, 202)

            bytes("kLevelInfo_05F000", 0x5F000, 0x200)
            bytes("kLevelInfo_05F200", 0x5F200, 0x200)
            bytes("kLevelInfo_05F400", 0x5F400, 0x200)
            bytes("kLevelInfo_05F600", 0x5F600, 0x200)

            bytes("kLoadLevel_DATA_05D608", 0x5D608, 0x100)
            bytes("kDisplayMessage_DATA_05A5D9", 0x5A5D9, 2854)
            bytes("kOverworldLightningAndRandomCloudSpawning", 0x4F708, 128)
            words("kLevelNames", 0x4A0FC, 256)
            bytes("kLineGuideSpeedTableData", 0x7F9DB, 536)
        }

        private fun addLevels() {
            // Foreground layers: tables of 24-bit pointers, or of 16-bit pointers in one bank.
            fun layer1(name: String, count: Int, pointer: (Int) -> Int) {
                add(name, packArrays(List(count) { i ->
                    val ea = pointer(i)
                    rom.bytes(ea, levelLength(ea))
                }))
            }
            layer1("kLevelData_Layer1", 0x200) { rom.long(0x5E000 + it * 3) }
            layer1("kEntranceData_Layer1", 6) { rom.long(0x5D766 + it * 3) }
            layer1("kChoclateIsland2_Layer1", 9) { rom.word(0x5DB08 + it * 2) or (0x06 shl 16) }
            layer1("kRollCallData_Layer1", 13) { rom.word(0xCAD58 + it * 2) or (0x0C shl 16) }

            // Second layers: bank 0xFF marks an RLE background that really lives in bank 0x0C.
            fun layer2(name: String, count: Int, pointer: (Int) -> Int) {
                val flags = ByteArray(count)
                val data = List(count) { i ->
                    var ea = pointer(i)
                    var flag = 0
                    if (ea and 0xff0000 == 0xff0000) {
                        ea = (ea and 0xffff) or 0x0c0000
                        flag = if (ea and 0xffff >= 0xE8FE) 0x12 else 0x02
                    }
                    flags[i] = flag.toByte()
                    rom.bytes(ea, if (flag and 2 != 0) rleLength(ea) else levelLength(ea))
                }
                add(name, packArrays(data))
                add(name + "_IsBg", flags)
            }
            layer2("kLevelData_Layer2", 0x200) { rom.long(0x5E600 + it * 3) }
            layer2("kEntranceData_Layer2", 6) { rom.long(0x5D778 + it * 3) }
            layer2("kChoclateIsland2_Layer2", 9) { rom.word(0x5DB2C + it * 2) or 0xff0000 }
            layer2("kRollCallData_Layer2", 13) {
                rom.word(0xCAD72 + it * 2) or (if (it != 12) 0xff0000 else 0x0c0000)
            }
            layer2("kBufferCreditsBackgrounds_Layer2", 7) { rom.word(0xC93C1 + it * 2) or 0xff0000 }
        }

        private fun addSprites() {
            val ranges = ArrayList<Pair<Int, Int>>()
            fun touch(ea: Int) {
                ranges.add(ea to ea + spriteDataLength(ea))
            }
            // The unmodified game keeps all level sprite data in bank 7.
            val banks = ByteArray(0x200) { 7 }
            for (i in 0 until 0x200) {
                touch(rom.word(0x5EC00 + i * 2) or ((banks[i].toInt() and 0xff) shl 16))
            }
            touch(0x7C3EE)
            for (i in 0 until 9) touch(rom.word(0x5DB1A + i * 2) or 0x70000)

            add("kLvlSprBlob", packBlob(ranges))
            add("kLmSpritePtrBankByte", banks)

            words("kLoadLevel_SpriteDataPtrs", 0x5EC00, 0x200)
            bytes("kFileSelectText_EraseFile", 0x5B6FE, 203 + 204)
            bytes("kInitializeMode7TilemapsAndPalettes_TilemapData", 0x3D9DE, 912)
        }

        private fun addOverworldEvents() {
            // Tables reached through a pointer in the code, which must be the original one.
            fun original(pointer: Int, expected: Int): Int {
                check(pointer == expected) { "Unexpected table pointer in ROM" }
                return pointer
            }
            fun tableWords(name: String, pointerAt: Int, expected: Int, n: Int) =
                words(name, original(rom.long(pointerAt), expected), n)
            fun tableBytes(name: String, pointerAt: Int, expected: Int, n: Int) =
                bytes(name, original(rom.long(pointerAt), expected), n)

            tableWords("kLayer2EventData_TileEntries", 0x4E49F, 0x4DD8D, 742)
            tableWords("kChangingLayer1OverworldTiles_Layer1TileLocation", 0x4EC8C, 0x4D85D, 112)
            tableWords("kOwEventProcess01_DestroyTileAnimation_DATA_04E587", 0x4EEC9, 0x4E587, 16)
            tableWords("kCheckIfDestroyTileEventIsActive_DATA_04E5B6", 0x4E69C, 0x4E5B6, 16)
            tableWords("kOwEventProcess01_DestroyTileAnimation_DATA_04D93D", 0x4EDB8, 0x4D93D, 112)

            val silent = "kOwEventProcess07_SilentEventsAndEndOfEvent_SilentEventTiles"
            tableBytes(silent, 0x4E9F4, 0x4E8E4, 44)
            tableBytes(silent + "_TileLayer", 0x4EA27, 0x4E910, 44)
            tableWords(silent + "_TileNum", 0x4EA32, 0x4E994, 44)
            tableWords(silent + "_TilemapLocation", 0x4EA38, 0x4E93C, 44)

            bytes("kOwDestruction_TileToIdx_04E5A7", 0x4E5A7, 5)
            bytes("kOwDestruction_TopTile_04E5AC", 0x4E5AC, 5)
            bytes("kOwDestruction_BottomTile_04E5B1", 0x4E5B1, 5)
            tableBytes("kOwDestruction_TriggerEvent_04E5D6", 0x4E67C, 0x4E5D6, 16)

            empty("kLmEventStuff1", "kLmEventStuff2", "kLmEventStuff3", "kLmEventStuff4")

            tableBytes("kOverworldLayer2EventTilemap_Tiles", 0x4EAF5, 0x0C8000, 3328)

            fun layer2Tilemap(name: String, pointerAt: Int) {
                val p = rom.word(pointerAt) or (rom.byte(0x4DC79) shl 16)
                bytes(name, p, rleLengthForSize(p, 0x2000))
            }
            layer2Tilemap("kLoadOverworldLayer2AndEventsTilemaps_OverworldLayer2Tilemap", 0x4DC72)
            layer2Tilemap("kLoadOverworldLayer2AndEventsTilemaps_OverworldLayer2Tilemap_Prop", 0x4DC8D)

            val prop = rom.word(0x4DD45) or (rom.byte(0x4DD4A) shl 16)
            bytes("kOverworldLayer2EventTilemap_Prop", original(prop, 0x0C8D00), 1642)
        }

        private fun addGameTables() {
            bytes("kLoadOverworldLayer1AndEvents_DATA_0CF7DF", 0xCF7DF, 0x800)
            empty("kRom") // the ROM itself is never included
            bytes("kUpdateLevelName_LevelNameStrings", 0x49AC5, 460)
            bytes("kGameMode25_ShowEnemyRollcallScreen_TileData", 0xCAF11, 1681)
            empty("kLmPalettes")
            bytes("kPlayerGFXRt_HeadTilePointerIndex", 0xE00C, 192)
            bytes("kPlayerGFXRt_BodyTilePointerIndex", 0xE0CC, 192)
            bytes("kLvlInitialFlags", 0x5DDA0, 96)
            bytes("kLoadOverworldSprites_SpriteSlotData", 0x4F625, 65)
            bytes("kChangingLayer1OverworldTiles_TilesThatChange", 0x4DA1D, 22)
            bytes("kChangingLayer1OverworldTiles_TilesToBecome", 0x4DA33, 22)
            bytes("kOverworldEventProcess01_DestroyTileAnimation_DATA_04EE7A", 0x4EE7A, 48)
            words("kLevelTileAnimations_FrameData", 0x5B999, 208)
            bytes("kSetPlayerPose_WalkingPoseCount", 0xDC78, 4)
            bytes("kDrawLoadingLetters_TileData", 0x90D1, 52)
            bytes("kDrawLoadingLetters_TileData_BottomTiles", 0x9105, 52)
            bytes("kDrawLoadingLetters_TileData_TopProp", 0x9139, 52)
            bytes("kDrawLoadingLetters_TileData_BottomProp", 0x916A, 52)
        }

        private fun addOverworldTables() {
            words("kOwTileAnimations_WaterTileNumbers", 0x48000, 3)
            words("kOwTileAnimations_TileNumbers", 0x48006, 64)
            words("kOwStarPipeWarp_SrcX_048431", 0x48431, 27)
            words("kOwStarPipeWarp_SrcY_048467", 0x48467, 27)
            words("kOwStarPipeWarp_DstX_04849D", 0x4849D, 27)
            words("kOwStarPipeWarp_DstY_0484D3", 0x484D3, 27)
            words("kOwLevelsForcedMusicChange_048D74", 0x48D74, 11)
            bytes("kOwSubmapMusic_048D8A", 0x48D8A, 7)
            words("kOw_KoopaKidTeleportXYPos_048E49", 0x48E49, 6)
            bytes("kOwTriggerSaveTiles_048F7F", 0x48F7F, 8)
            words("kOwNoAutoMoveLevels_04906C", 0x4906C, 6)
            bytes("kOwHardcodedPathLevel_049078", 0x49078, 10)
            words("kOwHardcodedPathChocolateIsland2_049082", 0x49082, 2)
            bytes("kOwHardcodedPathTiles_049086", 0x49086, 68)
            bytes("kOwHardcodedPathDirs_0490CA", 0x490CA, 68)
            bytes("kOwHardcodedPathStartIndex_04910E", 0x4910E, 10)
            bytes("kOwExitLevelTiles_049426", 0x49426, 10)
            words("kUpdateLevelName_DATA_049C91", 0x49C91, 31)
            words("kUpdateLevelName_DATA_049CCF", 0x49CCF, 15)
            words("kUpdateLevelName_DATA_049CED", 0x49CED, 13)
            bytes("kOwExitSource_049964", 0x49964, 70)
            bytes("kOwExitDest_0499AA", 0x499AA, 70)
            bytes("kOwExitExtra_0499F0", 0x499F0, 28)
            words("kOwExitLayerPosition_049A0C", 0x49A0C, 12)
            bytes("kOwUnknownTableA_From_04A03C", 0x4A03C, 24)
            words("kOwUnknownTableA_Alpha_04A054", 0x4A054, 24)
            words("kOwUnknownTableA_XY_04A084", 0x4A084, 48)
            bytes("kOwUnknownTableA_Direction_04A0E4", 0x4A0E4, 24)
            bytes("kOwDirectionAfterBeatingLevel_04D678", 0x4D678, 113)
            bytes("kOwSubmapTileset_04DC02", 0x4DC02, 7)
            words("kLayer2EventData_Ptrs_04E359", 0x4E359, 121)

            empty("kLmInitSaveData")
            bytes("kInitializeSaveData_InitialOWPlayerPos", 0x9EF0, 22)
            words("kOWSpr07_Smoke_DATA_04FC1E", 0x4FC1E, 4)
            words("kLoadOverworldSprites_SubmapBooXPosOffset", 0x4F666, 3)
            words("kLoadOverworldSprites_SubmapBooYPosOffset", 0x4F66C, 3)
            bytes("kLoadLevelHeader_LevelMusicTable", 0x584DB, 8)

            add(
                "kLevelsThatTriggerCutscenes",
                rom.bytes(0xC9A7, 8) + byteArrayOf(rom.byte(0xCA0C).toByte(), rom.byte(0xCA13).toByte())
            )
        }

        private fun addLunarMagicPlaceholders() {
            empty(
                "kLmExgfx", "kLmSuperExgfx", "kLmGraphicsRemapped",
                "kLmModifyMap16Ids", "kLm5DE00", "kLm6FC00", "kLm6FE00", "kLm10B8BC",
                "kLmLevelData3FE00", "kLmLevelData5DC85", "kLmLevelData5DC8A", "kLm5FE00",
                "kLevelInfo_05F800", "kLevelInfo_05FA00", "kLevelInfo_05FC00",
                "kMap16_0", "kMap16_1", "kMap16_2", "kMap16_3", "kMap16_4",
                "kMap16_5", "kMap16_6", "kMap16_7", "kMap16_TS",
                "kLmLvlExAnim"
            )
            add("kLmExanimBlob", packBlob(emptyList()))
            empty(
                "kLmOverworldPal", "kLmDisplayMessage_Tab0", "kLmDisplayMessage_3BC7F",
                "kLmDisplayMessage_3BE80", "kLmDisplayMessage_Tab1", "kLmTitleScreenMoves",
                "kLmLevelNamesPatch", "kOwLayer1AndEvents", "kOwLayer1AndEvents2",
                "kLmLvlInfo", "kLmCustomMap16Bg", "kLmSprExtraSize"
            )
            add("kLmFeatures", lunarMagicFeatures())
        }

        /**
         * Four words: feature flags, two level info addresses (Lunar Magic only) and hack flags.
         * The game patches tested here are read from the ROM; some are set even without
         * Lunar Magic.
         */
        private fun lunarMagicFeatures(): ByteArray {
            var flags = 0
            fun flag(bit: Int, set: Boolean) {
                if (set) flags = flags or (1 shl bit)
            }
            val owDecompressCall = rom.bytes(0xA149, 4)
            val originalCall = byteArrayOf(0x22, 0x28, 0xBA.toByte(), 0x00)
            val nops = ByteArray(4) { 0xEA.toByte() }

            flag(2, owDecompressCall.contentEquals(nops)) // SkipOverworldDecompress
            flag(3, rom.byte(0x480D0) == 0x60) // OverworldTiles4bpp
            flag(4, rom.byte(0xA5E1) == 0xEA) // Copy512colors
            flag(5, rom.byte(0xAF71) == 0x22) // WeirdPalette
            flag(6, rom.byte(0xEF570) != 0xC2) // SkipLoadPaletteHook
            flag(7, !rom.bytes(0xAA6B, 4).contentEquals(originalCall)) // GfxUpload
            flag(9, rom.byte(0xAACE) == 0x10) // 4bppgfx
            flag(12, rom.byte(0x05B15D) == 0xEA) // DontSetYposForIntroMarch
            flag(17, rom.byte(0x5855C) == 0x8D) // MusicRegTweak
            flag(18, rom.byte(0xA045) == 0x22) // TideWaterTweak
            flag(19, rom.byte(0x194B6) == 0x5C) // EnemyCollTweak
            flag(20, !owDecompressCall.contentEquals(originalCall)) // Ow4bppGfx
            flag(21, rom.byte(0xA0A0) == 0xEA) // DontResetOwPlayersMap
            flag(22, rom.byte(0xAA8D) != 0x08) // NonStdGfxAA8D
            flag(23, rom.byte(0x58E24) == 0x8F) // TimerTweaks
            flag(24, rom.byte(0x3BA26) == 0) // NoDefaultSavePrompts

            val hacks = if (rom.long(0xA2A1) != 0x86F122) 1 else 0 // wall jump

            return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(flags).putInt(0).putInt(0).putInt(hacks)
                .array()
        }
    }

    /** Growable little-endian byte writer. */
    private class Output {
        private var buffer = ByteArray(1024)
        private var size = 0

        private fun reserve(n: Int) {
            if (size + n > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + n))
        }

        fun u8(value: Int) {
            reserve(1)
            buffer[size++] = value.toByte()
        }

        fun u16(value: Int) {
            u8(value)
            u8(value shr 8)
        }

        fun u24(value: Int) {
            u16(value)
            u8(value shr 16)
        }

        fun u32(value: Int) {
            u16(value)
            u16(value shr 16)
        }

        fun raw(data: ByteArray) {
            reserve(data.size)
            data.copyInto(buffer, size)
            size += data.size
        }

        fun alignTo4() {
            while (size and 3 != 0) u8(0)
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
