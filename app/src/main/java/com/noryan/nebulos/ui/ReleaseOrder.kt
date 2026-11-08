package com.noryan.nebulos.ui

/**
 * Release order of the games inside a series heading. Each series lists title fragments oldest first by the release of the ORIGINAL
 * game, so a remake / HD / 3D version sits right below the game it is based on (Ocarina of Time 3D under Ocarina of Time). A title takes the position of the longest fragment it contains; a fragment
 * starting with "=" must equal the whole title. Titles that match nothing sort last, by name.
 */
internal object ReleaseOrder {
    private val ORDER: Map<String, List<String>> = mapOf(
        "Metroid" to listOf("=metroid", "zero mission", "metroid - ii", "samus returns", "super metroid", "fusion", "prime trilogy", "hunters", "other m", "federation force", "dread"),
        "The Legend of Zelda" to listOf(
            "=the legend of zelda", "zelda ii", "a link to the past", "awakening dx", "awakening", "ocarina of time", "ocarina of time 3d", "mask",
            "mask 3d", "oracle of seasons", "oracle of ages", "four swords", "wind waker", "wind waker hd", "four swords adventures", "minish cap",
            "twilight princess", "twilight princess hd", "phantom hourglass", "spirit tracks", "skyward sword", "a link between worlds",
            "triforce heroes", "breath of the wild", "tears of the kingdom", "echoes of wisdom"),
        "Super Mario" to listOf(
            "=super mario bros", "super mario bros 2", "super mario bros 3", "super mario land", "super mario world", "super mario land 2",
            "super mario all-stars", "super mario world 2", "super mario 64", "super mario sunshine", "super mario bros new", "super mario galaxy",
            "super mario bros wii", "super mario galaxy 2", "super mario 3d land", "super mario bros new 2", "super mario bros u deluxe",
            "super mario 3d world", "super mario odyssey", "super mario bros wonder"),
        "Sly Cooper" to listOf("thievius", "sly 2", "sly 3"),
        "Jak and Daxter" to listOf("jak and daxter", "jak ii", "jak 3", "jak x"),
        "Ratchet & Clank" to listOf("=ratchet & clank", "going commando", "up your arsenal", "deadlocked"),
        "Pokemon" to listOf("- red version", "- firered", "- silver version", "- ruby version", "- emerald", "- brilliant diamond"),
        "Dark Cloud" to listOf("=dark cloud", "dark cloud 2"),
        "God of War" to listOf("=god of war", "god of war ii"),
    )

    private fun norm(title: String) = title.lowercase().replace('é', 'e').trim()

    /** Position of [title] in its [series]; Int.MAX_VALUE when unknown. */
    fun rank(series: String, title: String): Int {
        val keys = ORDER[series] ?: return Int.MAX_VALUE
        val t = norm(title)
        var best = -1; var bestLen = -1
        keys.forEachIndexed { i, k ->
            val hit = if (k.startsWith("=")) t == k.substring(1) else t.contains(k)
            val len = if (k.startsWith("=")) Int.MAX_VALUE else k.length
            if (hit && len > bestLen) { best = i; bestLen = len }
        }
        return if (best < 0) Int.MAX_VALUE else best
    }
}
