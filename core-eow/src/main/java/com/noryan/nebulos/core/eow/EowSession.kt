package com.noryan.nebulos.core.eow

import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.ToggleOptionSupport
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.nebulos.recomp.RecompSession
import java.io.File

/**
 * The shared recomp session plus one menu row: "4:3 (NEXT LAUNCH)" (default ON). Echoes of Wisdom renders a real 4:3 frame (1920x1440: the width of 16:9, more world above and below, nothing cropped, the HUD and
 * menus in their normal proportions in a 16:9 band in the middle). Off: the plain 16:9 picture with bars above and below on the Nova's 4:3 screen. The render size is read once when the game starts, so the row
 * takes effect after closing and reopening the game. The choice is the flag file `<data root>/aspect169`: present = 16:9 (off), absent = 4:3 (on, also on a fresh install).
 * (60 fps is always on: the runtime holds the present interval at 1.)
 */
class EowSession(private val inner: RecompSession, private val dir: () -> File?) :
    CoreSession by inner, StateSlotProvider by inner, StateSupport by inner, ToggleOptionSupport {
    private fun flag(): File? = dir()?.let { File(it, "aspect169") }
    override val toggleLabel = "4:3 (NEXT LAUNCH)"
    override val toggleAvailable: Boolean get() = dir() != null
    override fun toggleValue(): Boolean = flag()?.exists() != true  // (on = no 16:9 marker)
    override fun toggle(): Boolean {
        val f = flag() ?: return false
        return runCatching { if (f.exists()) f.delete() else f.createNewFile() }.getOrDefault(false)
    }
}
