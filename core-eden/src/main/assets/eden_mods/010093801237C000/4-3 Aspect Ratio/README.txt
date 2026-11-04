Metroid Dread - 4:3 Aspect Ratio (Nebulos / Eden handheld), EXPERIMENTAL.
Title 010093801237C000, game version 2.1.0 (main NSO build id 646761F643AFEBB379EDD5E6A5151AF2CEF93DC1), file exefs/2.1.0.pchtxt.
One change, derived from theboy181's "21:9" patch (github.com/theboy181/switch-ptchtxt-mods, Metroid Dread/2.1.0): the two ints at 0x1A1BE18
(render width, height; stock 1600x900) are set to 1280x960 instead of 1680x720. The 3D scene then renders as a true 4:3 view (same width as 16:9,
more height). Menus/backgrounds keep a 16:9 picture. Needs the Nebulos host stretch (EdenMods ASPECT_GAMES entry) and an Eden build whose NSO patch
path does not copy the 1.9 GB .bss (see eden-src src/core/loader/nso.cpp: patchable_end); on stock Eden any Dread patch is OOM-killed on a 12 GB device.
Installed by Nebulos (core-eden). Delete this folder to turn it off; Nebulos will not put it back.
