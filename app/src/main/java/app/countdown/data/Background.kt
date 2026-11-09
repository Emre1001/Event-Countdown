package app.countdown.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Damit Widgets pünktlich weiterzählen, darf Android die App im Hintergrund nicht drosseln.
 * Akku-Optimierung aus = keine Doze-/Standby-Beschränkungen für die Minuten-Alarme.
 */
object Background {
    fun isOptimized(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestUnrestricted(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        start(context, direct) || start(context, list) || start(context, appDetails(context))
    }

    /** Xiaomi, Redmi und POCO blockieren Hintergrundstarts zusätzlich über "Autostart". */
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun openAutostart(context: Context) {
        val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        start(context, miui) || start(context, appDetails(context))
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
