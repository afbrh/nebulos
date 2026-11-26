package com.noryan.nebulos.core.zelda3

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.PriorityQueue

/**
 * Builds `zelda3_assets.dat` straight from the user's own US ROM.
 *
 * On-device replacement for the asset builder of snesrev/zelda3 (MIT, assets/restool.py): the output is byte for byte
 * what `extract_resources.py` followed by `compile_resources.py` writes for the default US build, without the
 * YAML / PNG / text intermediates. Only addresses, sizes and format constants live here; all data is read from the ROM.
 */
object Zelda3Assets {
    private const val ROM_SIZE = 0x100000
    private const val COPIER_HEADER = 0x200
    private const val ROM_SHA1 = "6d4f10a8b10e10dbe624cb23cf03b88bb8252973"

    /** Returns the exact bytes of zelda3_assets.dat. Throws IllegalArgumentException if [rom] is not the US ROM. */
    fun build(rom: ByteArray, progress: (Float) -> Unit = {}): ByteArray {
        val data = when (rom.size) {
            ROM_SIZE -> rom
            ROM_SIZE + COPIER_HEADER -> rom.copyOfRange(COPIER_HEADER, rom.size)
            else -> throw IllegalArgumentException(
                "This is not the US version of A Link to the Past (the file size is wrong).",
            )
        }
        val sha1 = MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(sha1 == ROM_SHA1) {
            "This ROM is not supported. An unmodified US copy of A Link to the Past is required."
        }
        return Builder(Rom(data)).run(progress)
    }

    // ---------------------------------------------------------------------------------------------------------------
    // ROM access (LoROM addresses)

    private class Rom(private val data: ByteArray) {
        fun byte(addr: Int): Int {
            check(addr and 0x8000 != 0) { "Bad ROM address" }
            return data[((addr shr 16) and 0x7f) * 0x8000 + (addr and 0x7fff)].toInt() and 0xff
        }

        fun int8(addr: Int): Int = byte(addr).toByte().toInt()
        fun word(addr: Int): Int = byte(addr) or (byte(addr + 1) shl 8)
        fun int16(addr: Int): Int = word(addr).toShort().toInt()
        fun long(addr: Int): Int = word(addr) or (byte(addr + 2) shl 16)

        /** [n] bytes starting at [addr], continuing in the next bank when one ends. */
        fun bytes(addr: Int, n: Int): ByteArray {
            var a = addr
            return ByteArray(n) {
                val v = byte(a).toByte()
                a++
                if (a and 0x8000 == 0) a += 0x8000
                v
            }
        }

        fun words(addr: Int, n: Int): IntArray {
            var a = addr
            return IntArray(n) {
                val v = word(a)
                a += 2
                if (a and 0x8000 == 0) a += 0x8000
                v
            }
        }
    }

    private class Decompressed(val data: ByteArray, val compressedLength: Int)

    /** The game's LZ-style compression. [bigEndianOffsets] selects the byte order of back-reference offsets. */
    private fun decompress(rom: Rom, start: Int, bigEndianOffsets: Boolean): Decompressed {
        var ea = start
        fun next(): Int {
            val r = rom.byte(ea)
            ea++
            if (ea and 0xffff == 0) ea += 0x8000
            return r
        }

        val out = ByteList()
        while (true) {
            val b = next()
            if (b == 0xff) return Decompressed(out.toByteArray(), (ea - start) and 0x7fff)
            val cmd: Int
            var len: Int
            if (b and 0xe0 != 0xe0) {
                len = b and 0x1f
                cmd = b and 0xe0
            } else {
                cmd = (b shl 3) and 0xe0
                len = ((b and 3) shl 8) or next()
            }
            len++
            if (cmd == 0x00) { // literal
                repeat(len) { out.add(next()) }
            } else if (cmd and 0x80 != 0) { // copy from the output
                val first = next()
                val second = next()
                var offset = if (bigEndianOffsets) (first shl 8) or second else (second shl 8) or first
                repeat(len) { out.add(out[offset++]) }
            } else if (cmd and 0x40 == 0) { // byte fill
                val v = next()
                repeat(len) { out.add(v) }
            } else if (cmd and 0x20 == 0) { // word fill
                val v1 = next()
                val v2 = next()
                for (i in 0 until len) out.add(if (i and 1 == 0) v1 else v2)
            } else { // incrementing fill
                val v = next()
                for (i in 0 until len) out.add((v + i) and 0xff)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Output helpers

    /** Growable byte buffer; values are range checked. */
    private class ByteList {
        private var buf = ByteArray(64)
        var size = 0
            private set

        fun add(v: Int) {
            check(v in 0..255) { "Value out of byte range" }
            if (size == buf.size) buf = buf.copyOf(size * 2)
            buf[size++] = v.toByte()
        }

        fun add(vararg values: Int) {
            for (v in values) add(v)
        }

        fun addAll(values: ByteArray) {
            for (v in values) add(v.toInt() and 0xff)
        }

        operator fun get(i: Int): Int {
            check(i in 0 until size)
            return buf[i].toInt() and 0xff
        }

        operator fun set(i: Int, v: Int) {
            check(i in 0 until size && v in 0..255)
            buf[i] = v.toByte()
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private fun u8Bytes(values: IntArray): ByteArray = ByteArray(values.size) {
        check(values[it] in 0..255) { "Value out of uint8 range" }
        values[it].toByte()
    }

    private fun i8Bytes(values: IntArray): ByteArray = ByteArray(values.size) {
        check(values[it] in -128..127) { "Value out of int8 range" }
        values[it].toByte()
    }

    private fun wordBytes(values: IntArray, range: IntRange): ByteArray {
        val r = ByteArray(values.size * 2)
        for (i in values.indices) {
            val v = values[i]
            check(v in range) { "Value out of 16-bit range" }
            r[i * 2] = v.toByte()
            r[i * 2 + 1] = (v shr 8).toByte()
        }
        return r
    }

    private fun u16Bytes(values: IntArray) = wordBytes(values, 0..65535)
    private fun i16Bytes(values: IntArray) = wordBytes(values, -32768..32767)

    private fun ByteArrayOutputStream.le16(v: Int) {
        write(v and 0xff)
        write((v shr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.le32(v: Int) {
        le16(v)
        le16(v ushr 16)
    }

    /** Several arrays in one blob: end offsets, the data, then the count (which also tells the offset width). */
    private fun packArrays(arrays: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        if (arrays.isEmpty()) return out.toByteArray()
        var offset = 0
        val offsets = IntArray(arrays.size - 1) {
            offset += arrays[it].size
            offset
        }
        val small = offset < 65536 && arrays.size <= 8192
        for (o in offsets) if (small) out.le16(o) else out.le32(o)
        for (a in arrays) out.write(a)
        out.le16(if (small) arrays.size - 1 else 8192 + arrays.size - 1)
        return out.toByteArray()
    }

    // ---------------------------------------------------------------------------------------------------------------

    private class Builder(private val rom: Rom) {
        /** Insertion order is part of the file format. */
        private val assets = LinkedHashMap<String, ByteArray>()

        private fun add(name: String, data: ByteArray) {
            check(!assets.containsKey(name)) { "Duplicate asset $name" }
            assets[name] = data
        }

        private fun u8(name: String, values: IntArray) = add(name, u8Bytes(values))
        private fun i8(name: String, values: IntArray) = add(name, i8Bytes(values))
        private fun u16(name: String, values: IntArray) = add(name, u16Bytes(values))
        private fun packed(name: String, arrays: List<ByteArray>) = add(name, packArrays(arrays))
        private fun romBytes(name: String, addr: Int, n: Int) = add(name, rom.bytes(addr, n))
        private fun romWords(name: String, addr: Int, n: Int) = u16(name, rom.words(addr, n))

        fun run(progress: (Float) -> Unit): ByteArray {
            val steps: List<() -> Unit> = listOf(
                ::soundBanks,
                ::dungeonRooms,
                ::enemyDamageData,
                ::linkGraphics,
                ::dungeonSprites,
                ::map32ToMap16,
                ::images,
                ::misc,
                ::dialogue,
                ::dungeonMap,
                ::tilemaps,
                ::overworld,
                ::overworldTables,
            )
            progress(0f)
            for ((i, step) in steps.withIndex()) {
                step()
                progress((i + 1f) / (steps.size + 1))
            }
            val file = assetFile()
            progress(1f)
            return file
        }

        private fun assetFile(): ByteArray {
            val keys = ByteArrayOutputStream()
            for (name in assets.keys) {
                keys.write(name.toByteArray(Charsets.UTF_8))
                keys.write(0)
            }
            val keySig = keys.toByteArray()
            val out = ByteArrayOutputStream(1 shl 20)
            out.write("Zelda3_v0     \n\u0000".toByteArray(Charsets.US_ASCII))
            out.write(MessageDigest.getInstance("SHA-256").digest(keySig))
            out.write(ByteArray(32))
            out.le32(assets.size)
            out.le32(keySig.size)
            for (a in assets.values) out.le32(a.size)
            out.write(keySig)
            for (a in assets.values) {
                while (out.size() and 3 != 0) out.write(0)
                out.write(a)
            }
            return out.toByteArray()
        }

        // -----------------------------------------------------------------------------------------------------------
        // Sound banks

        private fun soundBanks() {
            add("kSoundBank_intro", SoundBank(rom, 0x998000).intro())
            add("kSoundBank_indoor", SoundBank(rom, 0x9b8000).indoor())
            add("kSoundBank_ending", SoundBank(rom, 0x9ad380).ending())
        }

        // -----------------------------------------------------------------------------------------------------------
        // Dungeons

        /** One object layer (and its door list) of a room; [end] is the address after it. */
        private class Layer(val end: Int, val objects: ByteArray, val doors: ByteArray?)

        private fun readLayer(start: Int): Layer {
            var p = start
            val objects = ByteList()
            while (true) {
                val p0 = rom.byte(p)
                val p1 = rom.byte(p + 1)
                val p2 = rom.byte(p + 2)
                val a = p0 or (p1 shl 8)
                if (a == 0xffff) return Layer(p + 2, objects.toByteArray(), null)
                if (a == 0xfff0) {
                    p += 2
                    break
                }
                objects.add(p0, p1, p2)
                p += 3
            }
            val doors = ByteList()
            while (true) {
                val p0 = rom.byte(p)
                val p1 = rom.byte(p + 1)
                if (p0 == 0xff && p1 == 0xff) return Layer(p + 2, objects.toByteArray(), doors.toByteArray())
                // Only the direction (bits 0-1) and position (bits 4-7) of the first byte are kept.
                doors.add(p0 and 0xf3, p1)
                p += 2
            }
        }

        /** Appends a layer; returns the offset of its door list if one was written. */
        private fun writeLayer(out: ByteList, objects: ByteArray, doors: ByteArray?): Int {
            var doorOffset = 0
            out.addAll(objects)
            if (doors != null) {
                out.add(0xf0, 0xff)
                doorOffset = out.size
                out.addAll(doors)
            }
            out.add(0xff, 0xff)
            return doorOffset
        }

        private fun roomHeader(room: Int): IntArray {
            var p = 0x40000 or rom.word(0x4f502 + room * 2)
            if (p == 0x4ffef) p = 0x82edc5 // just some place with zeros
            val flags = rom.byte(p)
            val collision = (flags shr 2) and 7
            check(collision < 5)
            // Two tags share one name upstream, so the first of them is written as the second.
            fun tag(v: Int): Int {
                check(v < 64)
                return if (v == 30) 31 else v
            }
            val effect = rom.byte(p + 4)
            check(effect < 8)
            return intArrayOf(
                flags and 0xfd,
                rom.byte(p + 1),
                rom.byte(p + 2),
                rom.byte(p + 3),
                effect,
                tag(rom.byte(p + 5)),
                tag(rom.byte(p + 6)),
                rom.byte(p + 7),
                rom.byte(p + 8) and 3,
                rom.byte(p + 9),
                rom.byte(p + 10),
                rom.byte(p + 11),
                rom.byte(p + 12),
                rom.byte(p + 13),
            )
        }

        /** Appends [little] to [big], overlapping it with the longest matching tail; returns where it starts. */
        private fun appendScanBytes(big: ByteList, little: IntArray): Int {
            var n = little.size
            while (n > 0) {
                if (n <= big.size && (0 until n).all { big[big.size - n + it] == little[it] }) break
                n--
            }
            val offset = big.size - n
            for (i in n until little.size) big.add(little[i])
            return offset
        }

        private fun dungeonRooms() {
            val data = ByteList()
            val offsets = IntArray(320)
            val doorOffsets = IntArray(320)
            val headers = ByteList()
            val headerOffsets = IntArray(320)
            val chests = ByteList()
            val teleMessages = IntArray(320)

            val pits = HashSet<Int>()
            for (i in 0 until 57) pits.add(rom.word(0x80990c + i * 2))

            val chestsByRoom = HashMap<Int, MutableList<Pair<Int, Boolean>>>()
            for (i in 0 until 504 / 3) {
                val room = rom.word(0x81e96e + i * 3)
                val item = rom.byte(0x81e96e + i * 3 + 2)
                chestsByRoom.getOrPut(room and 0x7fff) { ArrayList() }.add(item to (room and 0x8000 != 0))
            }

            for (room in 0 until 320) {
                val roomAddr = rom.long(0x1f8000 + room * 3)
                offsets[room] = data.size
                data.add(rom.byte(roomAddr), rom.byte(roomAddr + 1))
                var p = roomAddr + 2
                for (layerIndex in 0 until 3) {
                    val layer = readLayer(p)
                    p = layer.end
                    // The third layer always carries a door list; the others only when it is not empty.
                    val doors = if (layerIndex == 2) {
                        layer.doors ?: ByteArray(0)
                    } else {
                        layer.doors?.takeIf { it.isNotEmpty() }
                    }
                    val doorOffset = writeLayer(data, layer.objects, doors)
                    if (layerIndex == 2) doorOffsets[room] = doorOffset
                }
                headerOffsets[room] = appendScanBytes(headers, roomHeader(room))
                teleMessages[room] = rom.word(0x87f61d + room * 2)
                for ((item, big) in chestsByRoom[room].orEmpty()) {
                    chests.add(room and 0xff, (room shr 8) or (if (big) 0x80 else 0), item)
                }
            }

            add("kDungeonRoom", data.toByteArray())
            u16("kDungeonRoomOffs", offsets)
            u16("kDungeonRoomDoorOffs", doorOffsets)
            add("kDungeonRoomHeaders", headers.toByteArray())
            u16("kDungeonRoomHeadersOffs", headerOffsets)
            add("kDungeonRoomChests", chests.toByteArray())
            u16("kDungeonRoomTeleMsg", teleMessages)
            u16("kDungeonPitsHurtPlayer", (0 until 320).filter { it in pits }.toIntArray())

            entranceInfo(ENTRANCES)
            entranceInfo(STARTING_POINTS)

            roomList("kDungeonRoomDefault", 0x84ef2f, 8)
            roomList("kDungeonRoomOverlay", 0x84ecc0, 19)

            dungeonSecrets()

            romWords("kDungAttrsForTile_Offs", 0x8e9000, 21)
            romBytes("kDungAttrsForTile", 0x8e902a, 1024)

            romWords("kMovableBlockDataInit", 0x84f1de, 198)
            romWords("kTorchDataInit", 0x84f36a, 144)
            romWords("kTorchDataJunk", 0x84f48a, 48)
        }

        private fun roomList(name: String, pointers: Int, count: Int) {
            val data = ByteList()
            val offsets = IntArray(count)
            for (i in 0 until count) {
                offsets[i] = data.size
                val layer = readLayer(rom.long(pointers + i * 3))
                check(layer.doors == null)
                writeLayer(data, layer.objects, null)
            }
            add(name, data.toByteArray())
            u16(name + "Offs", offsets)
        }

        private fun entranceInfo(t: EntranceTables) {
            val n = t.count
            // Entries are stored by index; every index belongs to a room that is part of the build.
            val rooms = IntArray(n) { rom.word(t.room + it * 2) }
            check(rooms.all { it < 320 })
            fun words(addr: Int) = IntArray(n) { rom.word(addr + it * 2) }
            fun bytes(addr: Int) = IntArray(n) { rom.byte(addr + it) }

            u16(t.prefix + "rooms", rooms)
            romBytes(t.prefix + "relativeCoords", t.relativeCoords, n * 8)
            u16(t.prefix + "scrollX", words(t.scrollX))
            u16(t.prefix + "scrollY", words(t.scrollY))
            u16(t.prefix + "playerX", words(t.playerX))
            u16(t.prefix + "playerY", words(t.playerY))
            u16(t.prefix + "cameraX", words(t.cameraX))
            u16(t.prefix + "cameraY", words(t.cameraY))
            u8(t.prefix + "blockset", bytes(t.blockset))
            i8(t.prefix + "floor", IntArray(n) { rom.int8(t.floor + it) })
            i8(t.prefix + "palace", IntArray(n) {
                val index = (rom.int8(t.palace + it) + 2) shr 1
                if (index == 0) -1 else (index - 1) * 2
            })
            u8(t.prefix + "doorwayOrientation", IntArray(n) {
                if (t.doorwayOrientation != 0) rom.int8(t.doorwayOrientation + it) else 0
            })
            u8(t.prefix + "startingBg", bytes(t.startingBg))
            u8(t.prefix + "quadrant1", IntArray(n) { rom.byte(t.quadrant1 + it) and 0x22 })
            u8(t.prefix + "quadrant2", bytes(t.quadrant2))
            u16(t.prefix + "doorSettings", IntArray(n) {
                val x = rom.word(t.exitDoor + it * 2)
                if (x == 0xffff) x else x and 0xbffe
            })
            if (t.associatedEntrance != 0) {
                u8(t.prefix + "entrance", words(t.associatedEntrance))
            }
            u8(t.prefix + "musicTrack", bytes(t.music))
        }

        private fun dungeonSprites() {
            val offsets = IntArray(320)
            val data = ByteList()
            data.add(0, 0xff)
            for (room in 0 until 320) {
                var ea = 0x890000 + rom.word(0x89d62e + room * 2)
                val sortMode = rom.byte(ea)
                ea++
                val sprites = ArrayList<IntArray>()
                val drops = ArrayList<MutableList<Int>>()
                while (rom.byte(ea) != 0xff) {
                    val y = rom.byte(ea)
                    val x = rom.byte(ea + 1)
                    val type = rom.byte(ea + 2)
                    ea += 3
                    if (type == 0xe4 && (y == 0xfe || y == 0xfd)) {
                        drops.last().add(y) // a key dropped by the previous sprite
                    } else {
                        sprites.add(
                            if (type != 0xe4 && x >= 0xe0) {
                                intArrayOf(y and 0x9f, x, type) // overlord
                            } else {
                                intArrayOf(y, x, type)
                            },
                        )
                        drops.add(ArrayList())
                    }
                }
                if (sprites.isEmpty() && sortMode == 0) continue
                offsets[room] = data.size
                data.add(sortMode)
                for (i in sprites.indices) {
                    data.add(*sprites[i])
                    if (drops[i].size == 1) data.add(drops[i][0], 0, 0xe4)
                }
                data.add(0xff)
            }
            add("kDungeonSprites", data.toByteArray())
            u16("kDungeonSpriteOffs", offsets)
        }

        /** Appends the (position, item) records at [start] up to the 0xffff terminator (not included). */
        private fun copySecrets(out: ByteList, start: Int) {
            var ea = start
            while (rom.word(ea) != 0xffff) {
                check(rom.word(ea) and 1 == 0)
                out.add(rom.byte(ea), rom.byte(ea + 1), rom.byte(ea + 2))
                ea += 3
            }
        }

        private fun dungeonSecrets() {
            val result = ByteList()
            repeat(640) { result.add(0) }
            val present = BooleanArray(320)
            for (room in 0 until 320) {
                val ea = 0x810000 or rom.word(0x81db69 + room * 2)
                if (rom.word(ea) == 0xffff) continue
                present[room] = true
                result[room * 2] = result.size and 0xff
                result[room * 2 + 1] = result.size shr 8
                copySecrets(result, ea)
                result.add(0xff, 0xff)
            }
            for (room in 0 until 320) {
                if (!present[room]) {
                    val l = result.size - 2
                    result[room * 2] = l and 0xff
                    result[room * 2 + 1] = l shr 8
                }
            }
            add("kDungeonSecrets", result.toByteArray())
        }

        private fun enemyDamageData() = add("kEnemyDamageData", decompress(rom, 0x83e800, true).data)

        // -----------------------------------------------------------------------------------------------------------
        // Graphics, tiles and misc tables

        private fun linkGraphics() = romBytes("kLinkGraphics", 0x108000, 0x7000)

        private fun map32ToMap16() {
            // 2218 groups of 6 bytes in each of four tables
            val sources = intArrayOf(0x838000, 0x83b400, 0x848000, 0x84b400)
            for (i in sources.indices) add("kMap32ToMap16_$i", rom.bytes(sources[i], 2218 * 6))
        }

        private fun images() {
            // The ROM keeps one pointer table (bank, high and low bytes) for background then sprite graphics.
            fun pointer(i: Int) =
                (rom.byte(0x80cf80 + i) shl 16) or (rom.byte(0x80d05f + i) shl 8) or rom.byte(0x80d13e + i)
            val bgCount = 115
            val spriteCount = 108

            fun compressed(addr: Int) = rom.bytes(addr, decompress(rom, addr, false).compressedLength)
            packed("kSprGfx", List(spriteCount) {
                val addr = pointer(bgCount + it)
                if (it < 12) rom.bytes(addr, 0x600) else compressed(addr)
            })
            packed("kBgGfx", List(bgCount) { compressed(pointer(it)) })
        }

        private fun misc() {
            romBytes("kOverworldMapGfx", 0x18c000, 0x4000)
            romBytes("kLightOverworldTilemap", 0xac727, 4096)
            romBytes("kDarkOverworldTilemap", 0xad727, 1024)

            romWords("kPredefinedTileData", 0x9b52, 6438)

            romWords("kMap16ToMap8", 0x8f8000, 3752 * 4)

            romBytes("kGeneratedWishPondItem", 0x888450, 256)
            romBytes("kGeneratedBombosArr", 0x8890fc, 256)

            romBytes("kGeneratedEndSequence15", 0x8ead25, 256)
            romBytes("kEnding_Credits_Text", 0x8eb178, 1989)
            romWords("kEnding_Credits_Offs", 0x8eb93d, 394)
            romWords("kEnding_MapData", 0x8eb038, 160)
            romWords("kEnding0_Offs", 0x8ec2e1, 17)
            romBytes("kEnding0_Data", 0x8ebf4c, 917)

            romWords("kPalette_DungBgMain", 0x9bd734, 1800)
            romWords("kPalette_MainSpr", 0x9bd218, 120)

            romWords("kPalette_ArmorAndGloves", 0x9bd308, 75)
            romWords("kPalette_Sword", 0x9bd630, 12)
            romWords("kPalette_Shield", 0x9bd648, 12)

            romWords("kPalette_SpriteAux3", 0x9bd39e, 84)
            romWords("kPalette_MiscSprite_Indoors", 0x9bd446, 77)
            romWords("kPalette_SpriteAux1", 0x9bd4e0, 168)

            romWords("kPalette_OverworldBgMain", 0x9be6c8, 210)
            romWords("kPalette_OverworldBgAux12", 0x9be86c, 420)
            romWords("kPalette_OverworldBgAux3", 0x9be604, 98)
            romWords("kPalette_PalaceMapBg", 0x9be544, 96)
            romWords("kPalette_PalaceMapSpr", 0x9bd70a, 21)
            romWords("kHudPalData", 0x9bd660, 64)

            romWords("kOverworldMapPaletteData", 0x8adb27, 256)
        }

        // -----------------------------------------------------------------------------------------------------------
        // Dialogue and font

        /** The messages as literal character codes (0..) and commands, with dictionary references expanded. */
        private fun decodeMessages(dictionary: List<ByteArray>): List<List<IntArray>> {
            val commandStart = 0x67
            val endMessage = 0x7f
            val switchBank = 0x80
            val dictionaryBase = 0x88
            val finish = 0xff
            // byte length of the commands 0x67..0x7f
            val commandLengths = intArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 1)

            var p = 0x9c8000
            var switched = false
            val messages = ArrayList<List<IntArray>>()
            while (true) {
                var message = ArrayList<IntArray>()
                while (true) {
                    val c = rom.byte(p)
                    val length = if (c >= commandStart && c < switchBank) commandLengths[c - commandStart] else 1
                    p += length
                    if (c == endMessage) break
                    if (c < commandStart) {
                        message.add(intArrayOf(c))
                    } else if (c < switchBank) {
                        message.add(if (length == 2) intArrayOf(c, rom.byte(p - 1)) else intArrayOf(c))
                    } else if (c == finish) {
                        return messages
                    } else if (c == switchBank) {
                        check(!switched)
                        switched = true
                        p = 0x8edf40
                        message = ArrayList()
                    } else {
                        check(c >= dictionaryBase) { "Unexpected text code" }
                        for (ch in dictionary[c - dictionaryBase]) message.add(intArrayOf(ch.toInt() and 0xff))
                    }
                }
                messages.add(message)
            }
        }

        /** Greedy re-compression: the first dictionary entry (in table order) that matches wins. */
        private fun compressMessage(message: List<IntArray>, dictionary: List<ByteArray>): ByteArray {
            val commandStart = 0x67
            val out = ByteList()
            var i = 0
            while (i < message.size) {
                val match = dictionary.indexOfFirst { entry ->
                    i + entry.size <= message.size && entry.indices.all {
                        val item = message[i + it]
                        item[0] < commandStart && item[0] == (entry[it].toInt() and 0xff)
                    }
                }
                if (match >= 0) {
                    out.add(0x88 + match)
                    i += dictionary[match].size
                } else {
                    out.add(*message[i])
                    i++
                }
            }
            return out.toByteArray()
        }

        private fun dialogue() {
            // 97 dictionary words: a table of 98 pointers into bank 0x8E
            val dictionary = List(97) {
                val start = rom.word(0x8ec703 + it * 2)
                val end = rom.word(0x8ec703 + it * 2 + 2)
                rom.bytes(0x8e0000 or start, end - start)
            }
            val messages = decodeMessages(dictionary)
            check(messages.size == 397) { "Unexpected message count" }
            val dictionaryPacked = packArrays(dictionary)
            val messagesPacked = packArrays(messages.map { compressMessage(it, dictionary) })
            packed("kDialogue", listOf(packArrays(listOf(dictionaryPacked, messagesPacked))))

            val font = rom.bytes(0x8e8000, 256 * 16)
            val widths = rom.bytes(0x8ecadf, 99)
            packed("kDialogueFont", listOf(packArrays(listOf(font, widths))))

            val language = "us".toByteArray(Charsets.US_ASCII)
            packed("kDialogueMap", listOf(packArrays(listOf(language, byteArrayOf(0, 0, 0)))))
        }

        // -----------------------------------------------------------------------------------------------------------
        // Dungeon map and tilemaps

        private fun dungeonMap() {
            val sizes = intArrayOf(75, 125, 50, 75, 175, 75, 50, 75, 50, 200, 150, 75, 100, 200)
            val floors = ArrayList<ByteArray>()
            val tiles = ArrayList<ByteArray>()
            for (i in sizes.indices) {
                val layout = rom.bytes(0xa0000 + rom.word(0x8af605 + i * 2), sizes[i])
                val used = layout.count { it.toInt() != 0xf }
                floors.add(layout)
                tiles.add(rom.bytes(0xa0000 + rom.word(0x8afbe4 + i * 2), used))
            }
            packed("kDungMap_FloorLayout", floors)
            packed("kDungMap_Tiles", tiles)
        }

        private fun tilemaps() {
            val sources = intArrayOf(0xcdd6d, 0xce7bf, 0xce2a8, 0xce63c, 0xce456, 0xeda9c)
            for ((i, source) in sources.withIndex()) {
                var p = source
                while (rom.byte(p) and 0x80 == 0) {
                    val isFill = rom.byte(p + 2) and 0x40 != 0
                    val length = ((rom.byte(p + 2) * 256 + rom.byte(p + 3)) and 0x3fff) + 1
                    p += 4
                    p += if (isFill) 2 else length
                }
                romBytes("kBgTilemap_$i", source, p - source + 1)
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        // Overworld

        private fun overworld() {
            fun maps(pointers: Int) = List(160) {
                val addr = rom.long(pointers + it * 3)
                rom.bytes(addr, decompress(rom, addr, true).compressedLength)
            }
            packed("kOverworld_Hibytes_Comp", maps(0x82f94d))
            packed("kOverworld_Lobytes_Comp", maps(0x82fb2d))
        }

        private fun overworldTables() {
            val unset = Int.MIN_VALUE
            fun table(size: Int, initial: Int = unset) = IntArray(size) { initial }

            val isSmall = table(192, 0)
            val auxTileThemes = table(128)
            val bgPalettes = table(136)
            val signText = table(128)
            val musicSets = table(256)
            val musicSets2 = table(96)

            // Areas that head a map screen; big areas cover three more screens.
            val areas = (0 until 160).filter { it >= 128 || rom.byte(0x82a5ec + (it and 63)) == (it and 63) }
            val isHead = BooleanArray(160)
            for (a in areas) isHead[a] = true

            fun spread(arr: IntArray, area: Int, key: Int, value: Int) {
                arr[key] = value
                if (area < 128 && isSmall[area] == 0) {
                    arr[key + 1] = value
                    arr[key + 8] = value
                    arr[key + 9] = value
                }
            }

            for (i in areas) {
                isSmall[i] = if (rom.byte(0x82f88d + i) != 0) 1 else 0
                if (i < 128) spread(auxTileThemes, i, i, rom.byte(0x80fc9c + i))
                if (i < 136) spread(bgPalettes, i, i, rom.byte(0x80fd1c + i))
                if (i < 128) spread(signText, i, i, rom.word(0x87f51d + i * 2))
                if (i < 64) {
                    for (stage in 0 until 4) {
                        spread(musicSets, i, i + stage * 64, rom.byte(0x82c303 + i + stage * 64))
                    }
                } else {
                    spread(musicSets2, i, i - 64, rom.byte(0x82c403 + i - 64))
                }
            }

            // Bird travel (0..8) and whirlpools (9..16, renumbered in area order)
            val birdScreen = table(17)
            val birdLoadOffset = table(17)
            val birdScrollX = table(17)
            val birdScrollY = table(17)
            val birdLinkX = table(17)
            val birdLinkY = table(17)
            val birdCameraX = table(17)
            val birdCameraY = table(17)
            val birdUnk1 = table(17)
            val birdUnk3 = table(17)
            val whirlpoolAreas = table(8)
            var whirlpool = 0
            for (area in areas) {
                for (i in 0 until 17) {
                    if (rom.word(0x82eae5 + i * 2) != area) continue
                    val j: Int
                    if (i < 9) {
                        j = i
                    } else {
                        whirlpoolAreas[whirlpool] = rom.word(0x82ecf8 + (i - 9) * 2)
                        j = whirlpool + 9
                        whirlpool++
                    }
                    birdScreen[j] = area
                    birdLoadOffset[j] = rom.word(0x82eb07 + i * 2) and 0x1ffe
                    birdScrollY[j] = rom.word(0x82eb29 + i * 2)
                    birdScrollX[j] = rom.word(0x82eb4b + i * 2)
                    birdLinkY[j] = rom.word(0x82eb6d + i * 2)
                    birdLinkX[j] = rom.word(0x82eb8f + i * 2)
                    birdCameraY[j] = rom.word(0x82ebb1 + i * 2)
                    birdCameraX[j] = rom.word(0x82ebd3 + i * 2)
                    birdUnk1[j] = rom.int8(0x82ebf5 + i * 2)
                    birdUnk3[j] = rom.int8(0x82ec17 + i * 2)
                }
            }

            val entranceArea = table(129)
            val entrancePos = table(129)
            val entranceId = table(129)
            for (area in areas) {
                for (j in 0 until 129) {
                    if (rom.word(0x9bb96f + j * 2) != area) continue
                    entranceArea[j] = area
                    entranceId[j] = rom.byte(0x9bbb73 + j)
                    entrancePos[j] = rom.word(0x9bba71 + j * 2) and 0x1ffe
                }
            }

            class Hole(val entrance: Int, val pos: Int, val area: Int)
            val holes = ArrayList<Hole>()
            for (area in areas) {
                for (i in 0 until 19) {
                    if (rom.word(0x9bb826 + i * 2) != area) continue
                    val p = rom.word(0x9bb800 + i * 2) + 0x400
                    val x = (p shr 1) and 0x3f
                    val y = (p shr 7) and 0x3f
                    holes.add(Hole(rom.byte(0x9bb84c + i), (x shl 1) or (((y - 8) and 0x3f) shl 7), area))
                }
            }
            holes.sortWith(compareBy<Hole>({ it.entrance }, { it.pos }, { it.area }))
            val holeArea = table(19)
            val holePos = table(19)
            val holeEntrance = table(19)
            for ((i, h) in holes.withIndex()) {
                holeArea[i] = h.area
                holePos[i] = h.pos
                holeEntrance[i] = h.entrance
            }

            val exitScreen = table(79)
            val exitRooms = table(79)
            val exitLoadOffset = table(79)
            val exitScrollX = table(79)
            val exitScrollY = table(79)
            val exitX = table(79)
            val exitY = table(79)
            val exitCameraX = table(79)
            val exitCameraY = table(79)
            val exitNormalDoor = table(79, 0)
            val exitFancyDoor = table(79, 0)
            val exitUnk1 = table(79)
            val exitUnk3 = table(79)

            val spTop = table(16, 0)
            val spBottom = table(16, 0)
            val spLeft = table(16, 0)
            val spRight = table(16, 0)
            val spTab4 = table(16, 0)
            val spTab5 = table(16, 0)
            val spTab6 = table(16, 0)
            val spTab7 = table(16, 0)
            val spLeftEdge = table(16, 0)
            val spDir = table(16, 0)
            val spSpriteGfx = table(16, 0)
            val spAuxGfx = table(16, 0)
            val spPalBg = table(16, 0)
            val spPalSprite = table(16, 0)

            for (area in areas) {
                for (j in 0 until 79) {
                    if (rom.byte(0x82de28 + j) != area) continue
                    val room = rom.word(0x82dd8a + j * 2)
                    exitScreen[j] = area
                    exitRooms[j] = room
                    exitLoadOffset[j] = rom.word(0x82de77 + j * 2) and 0x1ffe
                    exitScrollY[j] = rom.word(0x82df15 + j * 2)
                    exitScrollX[j] = rom.word(0x82dfb3 + j * 2)
                    exitY[j] = rom.word(0x82e051 + j * 2)
                    exitX[j] = rom.word(0x82e0ef + j * 2)
                    exitCameraY[j] = rom.word(0x82e18d + j * 2)
                    exitCameraX[j] = rom.word(0x82e22b + j * 2)
                    exitUnk1[j] = rom.int8(0x82e2c9 + j)
                    exitUnk3[j] = rom.int8(0x82e318 + j)
                    val normalDoor = rom.word(0x82e367 + j * 2)
                    val fancyDoor = rom.word(0x82e405 + j * 2)
                    check(normalDoor == 0 || fancyDoor == 0)
                    if (normalDoor != 0) exitNormalDoor[j] = normalDoor and 0xbffe
                    if (fancyDoor != 0) exitFancyDoor[j] = fancyDoor and 0xbffe
                    if (room >= 0x180 && room < 0x190) {
                        val k = room - 0x180
                        spDir[k] = (rom.byte(0x82e801 + k) shr 1) * 2
                        spSpriteGfx[k] = rom.byte(0x82e811 + k)
                        spAuxGfx[k] = rom.byte(0x82e821 + k)
                        spPalBg[k] = rom.byte(0x82e831 + k)
                        spPalSprite[k] = rom.byte(0x82e841 + k)
                        spTop[k] = rom.word(0x82e6e1 + k * 2)
                        spBottom[k] = rom.word(0x82e701 + k * 2)
                        spLeft[k] = rom.word(0x82e721 + k * 2)
                        spRight[k] = rom.word(0x82e741 + k * 2)
                        spLeftEdge[k] = rom.word(0x82e7e1 + k * 2)
                        spTab4[k] = rom.int16(0x82e761 + k * 2)
                        spTab6[k] = rom.int16(0x82e781 + k * 2)
                        spTab5[k] = rom.int16(0x82e7a1 + k * 2)
                        spTab7[k] = rom.int16(0x82e7c1 + k * 2)
                    }
                }
            }

            val secretOffsets = table(128)
            val secrets = ByteList()
            for (area in areas) {
                if (area >= 128) continue
                val ea = 0x9b0000 or rom.word(0x9bc2f9 + area * 2)
                if (rom.word(ea) == 0xffff) continue
                spread(secretOffsets, area, area, secrets.size)
                copySecrets(secrets, ea)
                secrets.add(0xff, 0xff)
            }
            for (i in 0 until 128) if (secretOffsets[i] == unset) secretOffsets[i] = secrets.size - 2

            val spriteOffsets = table(144 * 3, 0)
            val sprites = ByteList()
            val spriteGfx = table(256)
            val spritePalettes = table(256)
            sprites.add(0xff)
            fun spriteRange(range: IntRange, pointers: Int, stages: IntArray, infoStage: Int) {
                for (area in areas) {
                    if (area !in range) continue
                    if (area < 128) {
                        // Dark world areas always use the last info stage.
                        val stage = if (area >= 64) 3 else infoStage
                        val source = (area and 63) + stage * 64
                        val key = (area and 63) + infoStage * 64
                        spread(spriteGfx, area, key, rom.byte(0x80fa41 + source))
                        spread(spritePalettes, area, key, rom.byte(0x80fb41 + source))
                    }
                    var ea = 0x890000 + rom.word(pointers + area * 2)
                    if (rom.byte(ea) == 0xff) continue
                    for (stage in stages) spriteOffsets[stage * 144 + area] = sprites.size
                    while (rom.byte(ea) != 0xff) {
                        sprites.add(rom.byte(ea), rom.byte(ea + 1), rom.byte(ea + 2))
                        ea += 3
                    }
                    sprites.add(0xff)
                }
            }
            spriteRange(0..63, 0x89c881, intArrayOf(0), 0)
            spriteRange(0..63, 0x89c901, intArrayOf(1), 1)
            spriteRange(0..63, 0x89ca21, intArrayOf(2), 2)
            spriteRange(64..143, 0x89ca21, intArrayOf(1, 2), 3)

            u8("kOverworldMapIsSmall", isSmall)
            u8("kOverworldAuxTileThemeIndexes", auxTileThemes)
            u8("kOverworldBgPalettes", bgPalettes)
            u16("kOverworld_SignText", signText)
            u8("kOwMusicSets", musicSets)
            u8("kOwMusicSets2", musicSets2)
            u16("kBirdTravel_ScreenIndex", birdScreen)
            u16("kBirdTravel_Map16LoadSrcOff", birdLoadOffset)
            u16("kBirdTravel_ScrollX", birdScrollX)
            u16("kBirdTravel_ScrollY", birdScrollY)
            u16("kBirdTravel_LinkXCoord", birdLinkX)
            u16("kBirdTravel_LinkYCoord", birdLinkY)
            u16("kBirdTravel_CameraXScroll", birdCameraX)
            u16("kBirdTravel_CameraYScroll", birdCameraY)
            i8("kBirdTravel_Unk1", birdUnk1)
            i8("kBirdTravel_Unk3", birdUnk3)
            u16("kWhirlpoolAreas", whirlpoolAreas)
            u16("kOverworld_Entrance_Area", entranceArea)
            u16("kOverworld_Entrance_Pos", entrancePos)
            u8("kOverworld_Entrance_Id", entranceId)
            u16("kFallHole_Area", holeArea)
            u16("kFallHole_Pos", holePos)
            u8("kFallHole_Entrances", holeEntrance)
            u8("kExitData_ScreenIndex", exitScreen)
            u16("kExitDataRooms", exitRooms)
            u16("kExitData_Map16LoadSrcOff", exitLoadOffset)
            u16("kExitData_ScrollX", exitScrollX)
            u16("kExitData_ScrollY", exitScrollY)
            u16("kExitData_XCoord", exitX)
            u16("kExitData_YCoord", exitY)
            u16("kExitData_CameraXScroll", exitCameraX)
            u16("kExitData_CameraYScroll", exitCameraY)
            u16("kExitData_NormalDoor", exitNormalDoor)
            u16("kExitData_FancyDoor", exitFancyDoor)
            i8("kExitData_Unk1", exitUnk1)
            i8("kExitData_Unk3", exitUnk3)
            u16("kSpExit_Top", spTop)
            u16("kSpExit_Bottom", spBottom)
            u16("kSpExit_Left", spLeft)
            u16("kSpExit_Right", spRight)
            add("kSpExit_Tab4", i16Bytes(spTab4))
            add("kSpExit_Tab5", i16Bytes(spTab5))
            add("kSpExit_Tab6", i16Bytes(spTab6))
            add("kSpExit_Tab7", i16Bytes(spTab7))
            u16("kSpExit_LeftEdgeOfMap", spLeftEdge)
            u8("kSpExit_Dir", spDir)
            u8("kSpExit_SprGfx", spSpriteGfx)
            u8("kSpExit_AuxGfx", spAuxGfx)
            u8("kSpExit_PalBg", spPalBg)
            u8("kSpExit_PalSpr", spPalSprite)
            u16("kOverworldSecrets_Offs", secretOffsets)
            add("kOverworldSecrets", secrets.toByteArray())
            u16("kOverworldSpriteOffs", spriteOffsets)
            add("kOverworldSprites", sprites.toByteArray())
            u8("kOverworldSpriteGfx", spriteGfx)
            u8("kOverworldSpritePalettes", spritePalettes)

            romBytes("kMap8DataToTileAttr", 0x8e9459, 512)
            romBytes("kSomeTileAttr", 0x9bf110, 3824)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Entrance and starting point tables (ROM addresses; 0 = the set has no such table)

    private class EntranceTables(
        val prefix: String,
        val count: Int,
        val room: Int,
        val relativeCoords: Int,
        val scrollX: Int,
        val scrollY: Int,
        val playerX: Int,
        val playerY: Int,
        val cameraX: Int,
        val cameraY: Int,
        val blockset: Int,
        val floor: Int,
        val palace: Int,
        val doorwayOrientation: Int,
        val startingBg: Int,
        val quadrant1: Int,
        val quadrant2: Int,
        val exitDoor: Int,
        val associatedEntrance: Int,
        val music: Int,
    )

    private val ENTRANCES = EntranceTables(
        prefix = "kEntranceData_", count = 133,
        room = 0x82c813, relativeCoords = 0x82c91d,
        scrollX = 0x82cd45, scrollY = 0x82ce4f,
        playerX = 0x82d063, playerY = 0x82cf59,
        cameraX = 0x82d277, cameraY = 0x82d16d,
        blockset = 0x82d381, floor = 0x82d406, palace = 0x82d48b, doorwayOrientation = 0x82d510,
        startingBg = 0x82d595, quadrant1 = 0x82d61a, quadrant2 = 0x82d69f,
        exitDoor = 0x82d724, associatedEntrance = 0, music = 0x82d82e,
    )

    private val STARTING_POINTS = EntranceTables(
        prefix = "kStartingPoint_", count = 7,
        room = 0x82db6e, relativeCoords = 0x82db7c,
        scrollX = 0x82dbb4, scrollY = 0x82dbc2,
        playerX = 0x82dbde, playerY = 0x82dbd0,
        cameraX = 0x82dbfa, cameraY = 0x82dbec,
        blockset = 0x82dc08, floor = 0x82dc0f, palace = 0x82dc16, doorwayOrientation = 0,
        startingBg = 0x82dc1d, quadrant1 = 0x82dc24, quadrant2 = 0x82dc2b,
        exitDoor = 0x82dc32, associatedEntrance = 0x82dc40, music = 0x82dc4e,
    )

    // ---------------------------------------------------------------------------------------------------------------
    // Sound banks
    //
    // A bank is a set of blocks copied into the 64K sound CPU memory. The asset holds only the parts of that memory
    // that are reachable: sample and instrument tables, samples, song lists, songs, phrases, patterns and effects.

    private enum class Kind { SONG_LIST, SONG, PHRASE, PATTERN }

    private class SoundBank(rom: Rom, source: Int) {
        private val memory = IntArray(0x10000) { -1 } // -1 = not loaded
        private val used = BooleanArray(0x10000)
        private val kinds = HashMap<Int, Kind>()
        private val queue = PriorityQueue<Int>()

        init {
            var ea = source
            var blocks = 0
            while (blocks <= 256) {
                val count = rom.word(ea)
                val target = rom.word(ea + 2)
                if (count == 0) break
                ea += 4
                for (i in 0 until count) {
                    memory[target + i] = rom.byte(ea)
                    ea++
                    if (ea and 0xffff < 0x8000) ea += 0x8000
                }
                blocks++
            }
        }

        private fun byte(ea: Int): Int {
            val v = memory[ea]
            check(v >= 0) { "Sound data outside the bank" }
            return v
        }

        private fun word(ea: Int) = byte(ea) or (byte(ea + 1) shl 8)

        private fun mark(start: Int, end: Int) = used.fill(true, start, end)

        fun intro(): ByteArray {
            songList((word(0xd000) - 0xd000) / 2)
            for (ea in intArrayOf(0xd878, 0xd8a8, 0xd8b8, 0xdf11, 0xe37c)) reference(ea, Kind.PHRASE)
            decodeObjects()
            samples()
            soundEffects()
            return loadableSequence()
        }

        fun indoor(): ByteArray {
            songList((0xd046 - 0xd000) / 2)
            for (ea in intArrayOf(0xdc5e, 0xdc6e)) reference(ea, Kind.PHRASE)
            reference(0xe905, Kind.PATTERN)
            reference(0xe94a, Kind.PHRASE)
            decodeObjects()
            return loadableSequence()
        }

        fun ending(): ByteArray {
            songList((0xd046 - 0xd000) / 2)
            reference(0x2a10, Kind.PHRASE)
            decodeObjects()
            return loadableSequence()
        }

        /** Registers an object; those that live in this bank are queued for decoding. */
        private fun reference(ea: Int, kind: Kind) {
            if (ea == 0) return
            check(ea >= 256)
            val old = kinds[ea]
            if (old != null) {
                check(old == kind) { "Conflicting sound object" }
                return
            }
            kinds[ea] = kind
            if (memory[ea] >= 0) queue.add(ea)
        }

        private fun songList(count: Int) {
            reference(0xd000, Kind.SONG_LIST)
            for (i in 0 until count) reference(word(0xd000 + i * 2), Kind.SONG)
            mark(0xd000, 0xd000 + count * 2)
        }

        private fun decodeObjects() {
            while (true) {
                val ea = queue.poll() ?: break
                val next = queue.peek() ?: -1
                when (kinds.getValue(ea)) {
                    Kind.SONG_LIST -> {}
                    Kind.SONG -> song(ea)
                    Kind.PHRASE -> {
                        for (i in 0 until 8) reference(word(ea + i * 2), Kind.PATTERN)
                        mark(ea, ea + 16)
                    }
                    Kind.PATTERN -> pattern(ea, next)
                }
            }
        }

        private fun song(start: Int) {
            var ea = start
            while (true) {
                val phrase = word(ea)
                if (phrase == 0) break
                if (phrase < 0x100) { // loop: count and target
                    ea += 4
                } else {
                    reference(phrase, Kind.PHRASE)
                    ea += 2
                }
            }
            mark(start, ea + 2)
        }

        /** A pattern ends with a zero byte, or runs into the object that follows it. */
        private fun pattern(start: Int, next: Int) {
            // argument bytes of the commands 0xe0..0xfa
            val effectLengths =
                intArrayOf(1, 1, 2, 3, 0, 1, 2, 1, 2, 1, 1, 3, 0, 1, 2, 3, 1, 3, 3, 0, 1, 3, 0, 3, 3, 3, 1)
            var ea = start
            while (ea == start || ea != next) {
                var cmd = byte(ea++)
                if (cmd == 0) break
                if (cmd and 0x80 == 0) {
                    cmd = byte(ea++)
                    if (cmd and 0x80 == 0) cmd = byte(ea++)
                }
                if (cmd == 0xef) { // call
                    reference(word(ea), Kind.PATTERN)
                    ea += 3
                } else if (cmd >= 0xe0) {
                    ea += effectLengths[cmd - 0xe0]
                }
            }
            mark(start, ea)
        }

        private fun samples() {
            // 25 directory entries (+ 6 unused ones), instruments, note tables and effect instruments
            mark(0x3c00, 0x3c70)
            mark(0x3d00, 0x3dae)
            mark(0x3e00, 0x3e00 + 25 * 9)
            // BRR samples follow each other from 0x4000; two directory entries repeat the one before them.
            var end = 0x4000
            for (i in 0 until 25) {
                if (i == 10 || i == 20) continue
                var p = word(0x3c00 + i * 4)
                while (true) {
                    val header = byte(p)
                    p += 9
                    end += 9
                    if (header and 1 != 0) break
                }
            }
            mark(0x4000, end)
        }

        private fun soundEffects() {
            val effects = java.util.TreeSet<Int>()
            fun list(base: Int, count: Int, hasEcho: Boolean) {
                for (i in 0 until count) {
                    val ea = word(base + i * 2)
                    if (ea != 0) effects.add(ea)
                }
                mark(base, base + count * if (hasEcho) 4 else 3)
            }
            list(0x17c0, 32, false)
            list(0x1820, 63, true)
            list(0x191c, 63, true)
            // effects that no list points to
            for (ea in intArrayOf(
                0x1a5b, 0x1d1c, 0x1ee2, 0x1f13, 0x1f1c, 0x252d, 0x2533, 0x26a2, 0x277e,
                0x279d, 0x27c9, 0x27f6, 0x2807, 0x2818, 0x2829, 0x2831, 0x284a,
            )) effects.add(ea)
            for (ea in effects) soundEffect(ea, effects.higher(ea) ?: 0)
        }

        private fun soundEffect(start: Int, next: Int) {
            var ea = start
            while (ea != next) {
                var b = byte(ea++)
                if (b == 0) break
                if (b and 0x80 == 0) { // note length, then up to two volumes
                    b = byte(ea++)
                    if (b and 0x80 == 0) {
                        b = byte(ea++)
                        if (b and 0x80 == 0) b = byte(ea++)
                    }
                }
                if (b == 0xe0) { // instrument
                    ea += 1
                } else if (b == 0xf9) { // note with pitch slide
                    ea += 4
                } else if (b == 0xf1) { // pitch slide
                    ea += 3
                } else if (b == 0xff) { // restart
                    break
                }
            }
            mark(start, ea)
        }

        /** The used regions as (length, address, data) blocks, ended by a zero length. */
        private fun loadableSequence(): ByteArray {
            val out = ByteArrayOutputStream()
            var i = 0
            while (i < 0x10000) {
                if (!used[i]) {
                    i++
                    continue
                }
                var j = i
                while (j < 0x10000 && used[j]) j++
                out.le16(j - i)
                out.le16(i)
                for (k in i until j) out.write(maxOf(memory[k], 0))
                i = j
            }
            out.le16(0)
            return out.toByteArray()
        }
    }
}
