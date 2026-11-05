package com.noryan.nebulos.api

/**
 * Games that hitch hard in the first seconds after their picture first appears (measured on the Nova, repeatable even with no input):
 * how many milliseconds the loading screen should stay up AFTER the picture shows, so the hitch happens behind it. See [PictureGate].
 * Everything else gets 0. Matched on [TitleMatch.clean] of the ROM path.
 */
object StartupSettle {
    private val RULES = listOf(
        Regex("smash\\s*bros\\.?\\s*melee", RegexOption.IGNORE_CASE) to 7_000L,   // freezes of 0.9 / 2.9 / 1.5 s at 1.1 / 4.0 / 5.4 s after the picture
        Regex("mario\\s*bros\\.?\\s*wii", RegexOption.IGNORE_CASE) to 4_500L,     // 1.6 to 2.9 s freezes early on
        Regex("metroid.*prime", RegexOption.IGNORE_CASE) to 4_000L,               // one ~2 s freeze about 2 s after the picture
    )

    @JvmStatic
    fun forPath(romPath: String): Long = TitleMatch.clean(romPath).let { t -> RULES.firstOrNull { it.first.containsMatchIn(t) }?.second ?: 0L }
}
