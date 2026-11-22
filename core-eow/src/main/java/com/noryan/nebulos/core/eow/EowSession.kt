package com.noryan.nebulos.core.eow

import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.ToggleOptionSupport
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.nebulos.recomp.RecompSession
import java.io.File

/**
 * The shared recomp session plus one menu row: "4:3 FILL" (default off). Echoes of Wisdom's picture is 16:9; the Nova's screen is 4:3. Off: the whole picture with bars. On: the picture's middle
 * is cropped to fill the screen (a quarter of the width goes, the HUD's outer edges with it - hearts, rupee counter, button prompts are cut; the menus' side columns too); the runtime polls the
 * flag file `<data root>/aspect43` once a second, so the row works live and survives restarts. (60 fps is always on: the runtime holds the present interval at 1.)
 */
class EowSession(private val inner: RecompSession, private val dir: () -> File?) :
    CoreSession by inner, StateSlotProvider by inner, StateSupport by inner, ToggleOptionSupport {
    private fun flag(): File? = dir()?.let { File(it, "aspect43") }
    override val toggleLabel = "4:3 FILL"
    override val toggleAvailable: Boolean get() = dir() != null
    override fun toggleValue(): Boolean = flag()?.exists() == true
    override fun toggle(): Boolean {
        val f = flag() ?: return false
        return runCatching { if (f.exists()) f.delete() else f.createNewFile() }.getOrDefault(false)
    }
}
