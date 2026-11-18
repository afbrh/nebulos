package com.noryan.nebulos.core.sotc

import android.content.Context
import com.noryan.nebulos.api.save.SaveRoot
import java.io.File

/** Portable save data (nebulos-api/SAVEDATA.md): the memory card images live in `<root>/sotc-core/memcards`. */
internal object SotcSaveData {
    const val CORE_ID = "sotc"
    fun memcardDir(ctx: Context): File = File(SaveRoot.coreDir(ctx, CORE_ID), "memcards").apply { mkdirs() }
}
