package com.noryan.nebulos.core.lichii

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.noryan.lichii.R
import com.noryan.lichii.YuzuApplication
import com.noryan.lichii.activities.EmulationActivity
import com.noryan.lichii.features.settings.model.BooleanSetting
import com.noryan.lichii.features.settings.model.StringSetting
import com.noryan.lichii.utils.NativeConfig
import com.noryan.lichii.utils.NebulosBridge

/**
 * Declared with android:process=":lichii" so it only runs in the Eden game process, before Application.onCreate.
 * Eden's own YuzuApplication is never instantiated (Nebulos has its own Application), so we call the embedding
 * entry point that eden-src provides (YuzuApplication.initializeForEmbedding), then hide Eden's touch overlay
 * (the game surface is the only UI; the shared Nebulos menu replaces Eden's drawer) and hook EmulationActivity.
 */
class LichiiInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        createNotificationChannels(app)
        // Stage the bundled Turnip driver BEFORE the engine loads its Vulkan library (see LichiiDriver).
        val driver = LichiiDriver.prepare(app)
        try {
            // Portable save data: Eden reads this between config load and System init (see DirectoryInitialization in eden-src);
            // the profile (user UUID) is created right there, so it must already point at <save root>/eden/save.
            NebulosBridge.saveDir = LichiiCore.saveDir(app).absolutePath
            // Texture-cache budget for phones (the fork's env knobs; read by the native library at start). An empty or user-set value wins.
            for ((k, v) in listOf("EDEN_NX2_TC_EXPECTED_MIB" to "1024", "EDEN_NX2_TC_CRITICAL_MIB" to "1536")) {
                if (System.getenv(k) == null) runCatching { android.system.Os.setenv(k, v, false) }
            }
            YuzuApplication.initializeForEmbedding(app)
            BooleanSetting.SHOW_INPUT_OVERLAY.setBoolean(false)
            // Every launch: keep driver_path in step with the installed driver so Eden's onLaunchGame never swaps it.
            StringSetting.DRIVER_PATH.setString(driver.zipPath ?: "")
            enforceNebulosSettings()
            NativeConfig.saveGlobalConfig()
        } catch (t: Throwable) {
            Log.e(TAG, "Lichii embedding init failed", t)
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is EmulationActivity) LichiiHost.attach(activity)
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is EmulationActivity) LichiiHost.detach(activity)
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) { if (a is EmulationActivity) LichiiHost.onResume() }
            override fun onActivityPaused(a: Activity) { if (a is EmulationActivity) LichiiHost.onPause() }
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) {}
        })
        return true
    }

    /**
     * Enforced on EVERY launch right after the config is loaded and before any game/engine start (the in-memory values
     * are what the emulation core reads, and saveGlobalConfig persists them to config.ini):
     *  - vibration/rumble permanently off (the Java/native vibration paths are also stubbed out in eden-src);
     *  - asynchronous shader/pipeline compilation on, driver pipeline cache on.
     *  use_asynchronous_gpu_emulation is deliberately left at the user/default value.
     */
    private fun enforceNebulosSettings() {
        fun b(key: String, v: Boolean) = try { NativeConfig.setBoolean(key, v) } catch (t: Throwable) { Log.w(TAG, "setting $key failed", t) }
        // Ocarina of Time (the first NX2 title): without cooperative-matrix support its anti-aliasing network runs as shader math at ~2 fps on
        // Adreno; "No sharpening" (1) bypasses it. The NX2 guest memory layout must be the 12 GiB one (smaller crashes at boot).
        try { NativeConfig.setInt("oot_anti_aliasing", 1); NativeConfig.setInt("memory_layout_mode", 4) } catch (t: Throwable) { Log.w(TAG, "NX2 settings failed", t) }
        b("vibration_enabled", false)
        b("enable_accurate_vibrations", false)
        // Escape hatch / A-B testing: create <externalFilesDir>/nebulos_sync_shaders to force synchronous shader compilation.
        val syncFlag = context?.getExternalFilesDir(null)?.let { java.io.File(it, "nebulos_sync_shaders").exists() } == true
        b("use_asynchronous_shaders", !syncFlag)
        b("use_vulkan_driver_pipeline_cache", true)
        try {
            val players = NativeConfig.getInputSettings(true)
            for (p in players) { p.vibrationEnabled = false; p.vibrationStrength = 0; p.useSystemVibrator = false }
            NativeConfig.setInputSettings(players, true)
        } catch (t: Throwable) { Log.w(TAG, "player vibration settings failed", t) }
    }

    /** YuzuApplication.createNotificationChannels() is private and only runs from its own onCreate; Eden's
     *  ForegroundService crashes the process ("Bad notification for startForeground") without the channel. */
    private fun createNotificationChannels(app: Application) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            app.getString(R.string.app_notification_channel_id), app.getString(R.string.app_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); vibrationPattern = null })
        nm.createNotificationChannel(NotificationChannel(
            app.getString(R.string.notice_notification_channel_id), app.getString(R.string.notice_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    private companion object { const val TAG = "NebulosLichii" }
}
