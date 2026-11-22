package com.noryan.nebulos.core.eow

import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.ToggleOptionSupport
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.nebulos.recomp.RecompSession
import java.io.File

/**
 * The shared recomp session plus one menu row: "4:3 (NEXT LAUNCH)" (default off). Echoes of Wisdom renders 16:9 (1920x1080, bars above and below on the Nova's 4:3 screen). On: the next launch renders
 * the game at 1920x1440, a real 4:3 frame - the same width, more world above and below, nothing cropped, the HUD and menus in their normal proportions (the 16:9 layout in the middle of the frame).
 * The render size is read once when the game starts, so the row takes effect after closing and reopening the game; the flag file is `<data root>/aspect43`.
 * (60 fps is always on: the runtime holds the present interval at 1.)
 */
class EowSession(private val inner: RecompSession, private val dir: () -> File?) :
    CoreSession by inner, StateSlotProvider by inner, StateSupport by inner, ToggleOptionSupport {
    private fun flag(): File? = dir()?.let { File(it, "aspect43") }
    override val toggleLabel = "4:3 (NEXT LAUNCH)"
    override val toggleAvailable: Boolean get() = dir() != null
    override fun toggleValue(): Boolean = flag()?.exists() == true
    override fun toggle(): Boolean {
        val f = flag() ?: return false
        return runCatching { if (f.exists()) f.delete() else f.createNewFile() }.getOrDefault(false)
    }
}
