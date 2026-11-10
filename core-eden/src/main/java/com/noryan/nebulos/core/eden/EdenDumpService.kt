package com.noryan.nebulos.core.eden

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import org.yuzu.yuzu_emu.R
import java.io.File

/**
 * Internal/debug entry point for [EdenGameDump], runs in `:eden`. Declared exported but guarded by android.permission.DUMP,
 * which only the adb shell / system hold (no third-party app can start it). Not used by the Nebulos UI yet.
 *
 *   adb shell am start-foreground-service -n com.noryan.nebulos/com.noryan.nebulos.core.eden.EdenDumpService \
 *       --es game '/storage/XXXX/_ROMS/Game.xci' [--es out /some/dir] ; cancel: --ez cancel true
 * Progress/result: `<externalFilesDir>/eden_dump_status.json` (and logcat tag NebulosDump).
 */
class EdenDumpService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("cancel", false) == true) { EdenGameDump.cancel(); return START_NOT_STICKY }
        val game = intent?.getStringExtra("game")
        if (game.isNullOrBlank()) { stopSelf(startId); return START_NOT_STICKY }
        val out = intent.getStringExtra("out")?.takeIf { it.isNotBlank() }?.let { File(it) }
        val notif: Notification = Notification.Builder(this, getString(R.string.app_notification_channel_id))
            .setSmallIcon(R.drawable.ic_stat_notification_logo).setContentTitle("Dumping game files").setOngoing(true).build()
        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val status = File(getExternalFilesDir(null) ?: filesDir, "eden_dump_status.json")
        Thread({
            status.writeText("""{"state":"running","game":"${game.replace("\"", "'")}"}""")
            val r = try {
                EdenGameDump.dump(this, game, out) { d, t ->
                    status.writeText("""{"state":"running","done":$d,"total":$t}""")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "dump crashed", t)
                EdenGameDump.parse("""{"ok":false,"error":"${t.toString().replace("\"", "'")}"}""", null)
            }
            Log.i(TAG, "dump finished: ${r.raw} -> ${r.outDir}")
            status.writeText("""{"state":"finished","outDir":"${r.outDir ?: ""}","result":${r.raw}}""")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }, "eden-dump").start()
        return START_NOT_STICKY
    }

    private companion object { const val TAG = "NebulosDump"; const val NOTIF_ID = 0x1d01 }
}
