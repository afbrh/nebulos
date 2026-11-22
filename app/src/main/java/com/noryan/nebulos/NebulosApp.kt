package com.noryan.nebulos

import android.app.Application
import android.os.Process
import androidx.room.Room
import com.noryan.nebulos.core.watermelonds.NebulosHiltApplication
import com.noryan.nebulos.data.AppDatabase
import com.noryan.nebulos.data.MIGRATION_2_3
import com.noryan.nebulos.data.MIGRATION_3_4
import com.noryan.nebulos.data.Prefs
import dagger.hilt.android.HiltAndroidApp

/**
 * One Application class serves every process of the app (the main shell and the per-core game processes
 * :dolphin, :eden, ...). Only the main process builds the registry/database; core processes stay light.
 */
@HiltAndroidApp
class NebulosApp : NebulosHiltApplication() {
    lateinit var db: AppDatabase; private set
    lateinit var prefs: Prefs; private set

    override fun onCreate() {
        super.onCreate() // WatermelonDS' Hilt/native init for :melonds must run first
        com.noryan.nebulos.api.crash.NebulosCrashGuard.install(this) // core processes: no silent relaunch after a crash
        if (!isMainProcess()) return
        CoreBootstrap.registerAll()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "nebulos.db").addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            // Only the pre-migration v1 schema may still be rebuilt from scratch (the library is re-scanned); 2+ always migrate.
            .fallbackToDestructiveMigrationFrom(true, 1).build()
        prefs = Prefs(this)
        removeGalaxy2Leftovers()
    }

    /** Super Mario Galaxy 2 was dropped: delete the files its core left behind (extracted disc, data folder). Cheap and idempotent. */
    private fun removeGalaxy2Leftovers() {
        Thread {
            runCatching {
                val ext = getExternalFilesDir(null)
                listOf(java.io.File(ext, "native-dumps/SB4E01"), java.io.File(ext, "smg2"), java.io.File(ext, "runtime-env-smg2.txt"), java.io.File(filesDir, "smg2"))
                    .filter { it.exists() }.forEach { it.deleteRecursively() }
            }
        }.start()
    }

    private fun isMainProcess(): Boolean =
        Application.getProcessName() == packageName
}
