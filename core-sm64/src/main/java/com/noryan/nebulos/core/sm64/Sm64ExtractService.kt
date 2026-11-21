package com.noryan.nebulos.core.sm64

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.io.File

/**
 * The one-time ROM -> `sm64.o2r` conversion (Torch), in the `:sm64x` process. Torch's Companion is deliberately never freed (its destructor double-frees), so the process ends when the
 * archive is written and the OS takes everything back before the game starts. Progress and the result go through `files/sm64/.extract-status` (one line: `run <count>`, `ok`, `error <msg>`),
 * which [Sm64Activity] polls.
 */
class Sm64ExtractService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rom = intent?.getStringExtra(EXTRA_ROM).orEmpty()
        val dir = Sm64Files.dataDir(this)
        val status = File(dir, STATUS)
        Thread({
            try {
                status.writeText("run 0")
                System.loadLibrary("SDL2_sm64")
                System.loadLibrary("sm64_main")
                val ticker = Thread({
                    try { while (true) { status.writeText("run " + Sm64Assets.nativeProgress()); Thread.sleep(250) } } catch (_: Throwable) {}
                }, "sm64-progress").apply { isDaemon = true; start() }
                val err = Sm64Assets.nativeGenerateGameArchive(rom, dir.absolutePath, dir.absolutePath)
                ticker.interrupt()
                status.writeText(if (err == null) "ok" else "error " + err.replace('\n', ' '))
            } catch (t: Throwable) {
                Log.e(TAG, "extraction failed", t)
                runCatching { status.writeText("error " + (t.message ?: t.toString()).replace('\n', ' ')) }
            }
            Process.killProcess(Process.myPid())
        }, "sm64-extract").start()
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_ROM = "rom"
        const val STATUS = ".extract-status"
        private const val TAG = "Sm64ExtractService"
    }
}
