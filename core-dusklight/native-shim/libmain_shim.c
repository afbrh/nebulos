/* Empty on purpose. Dusklight mods (built against upstream Dusklight on Android) carry DT_NEEDED "libmain.so", the upstream
 * engine library. RomCore's engine library is libdusklight_main.so (unique name). This stub is named libmain.so and only
 * has DT_NEEDED libdusklight_main.so, so the mod's engine symbols resolve through it into the real engine. */
