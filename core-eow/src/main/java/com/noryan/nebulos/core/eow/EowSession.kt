package com.noryan.nebulos.core.eow

import com.noryan.nebulos.api.CoreSession
import com.noryan.nebulos.api.menu.StateSlotProvider
import com.noryan.nebulos.api.menu.StateSupport
import com.noryan.nebulos.api.ToggleOptionSupport
import com.noryan.nebulos.recomp.RecompSession
import java.io.File

/**
 * The shared recomp session plus one menu row: "60 FPS". Echoes of Wisdom asks for a 30 fps present interval (its Switch 1 design target); with the row on, the runtime holds the
 * interval at 1 (60 fps where the GPU keeps up: about 14 ms a frame in the Suthorn Ruins room; the world's step is time-based, so the game does not run faster). The state is the
 * flag file `<data root>/fps60`, which the runtime polls once a second, so the row works live and survives restarts. Default: off (30 fps).
 */
class EowSession(private val inner: RecompSession, private val flag: () -> File?) :
    CoreSession by inner, StateSlotProvider by inner, StateSupport by inner, ToggleOptionSupport {
    override val toggleLabel = "60 FPS"
    override val toggleAvailable: Boolean get() = flag() != null
    override fun toggleValue(): Boolean = flag()?.exists() == true
    override fun toggle(): Boolean {
        val f = flag() ?: return false
        return runCatching { if (f.exists()) f.delete() else f.createNewFile() }.getOrDefault(false)
    }
}
