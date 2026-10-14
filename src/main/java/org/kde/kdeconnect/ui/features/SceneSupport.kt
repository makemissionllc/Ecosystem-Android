/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui.features

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.ecosystem.EcosystemPlugin
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.R
import java.util.Calendar

internal object SceneSupport {
    fun prefs(context: Context) = context.getSharedPreferences("ecosystem_scenes", Context.MODE_PRIVATE)
    fun initialize(context: Context) {
        schedule(context)
        var wasNear = false
        KdeConnect.getInstance().addDeviceListChangedCallback("DeskScene") {
            val near = KdeConnect.getInstance().devices.values.any { it.isPaired && it.isReachable && it.deviceType == DeviceType.TABLET && it.activeTransport != org.kde.kdeconnect.backends.ConnectionTransport.TAILSCALE }
            if (near && !wasNear && prefs(context).getBoolean("desk", false)) {
                apply(context, "work", KdeConnect.getInstance().devices.values.firstOrNull { it.isPaired && it.isReachable && it.deviceType == DeviceType.TABLET && it.activeTransport != org.kde.kdeconnect.backends.ConnectionTransport.TAILSCALE }?.deviceId)
            }
            wasNear = near
        }
    }
    fun apply(context: Context, scene: String, deviceId: String?): String {
        val message = applyLocal(context, scene)
        if (message.isNotBlank()) return message
        deviceId?.let { KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(it)?.request("scene", scene) }
        return ""
    }
    fun applyLocal(context: Context, scene: String): String {
        if (scene !in listOf("work", "movie", "sleep", "travel")) return context.getString(R.string.feature_action_failed)
        val manager = context.getSystemService<NotificationManager>()
        val permission = permissionMessage(context, scene)
        if (permission.isNotEmpty()) return permission
        try {
            if (scene in listOf("work", "sleep")) manager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            val audio = context.getSystemService<AudioManager>()
            if (scene == "movie" || scene == "sleep") {
                audio?.setStreamVolume(AudioManager.STREAM_MUSIC, if (scene == "sleep") 0 else (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 4), 0)
                PreferenceManager.getDefaultSharedPreferences(context).edit { putString("theme_pref", ThemeUtil.DARK_MODE) }
                android.os.Handler(android.os.Looper.getMainLooper()).post { ThemeUtil.applyTheme(ThemeUtil.DARK_MODE) }
            }
            if (scene == "sleep") Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 20)
            PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean("travel_mode", scene == "travel") }
            if (scene == "travel") org.kde.kdeconnect.BackgroundService.ForceRefreshConnections(context)
            prefs(context).edit { putString("active", scene) }
        } catch (e: SecurityException) { return context.getString(R.string.feature_action_failed) }
        return ""
    }
    fun permissionMessage(context: Context, scene: String): String {
        if (scene in listOf("work", "sleep") && context.getSystemService<NotificationManager>()?.isNotificationPolicyAccessGranted != true)
            return context.getString(R.string.feature_focus_permission)
        if (scene == "sleep" && !Settings.System.canWrite(context)) return context.getString(R.string.feature_brightness_permission)
        return ""
    }
    fun automate(context: Context, name: String, enabled: Boolean): String {
        if (enabled) {
            val permission = permissionMessage(context, if (name == "sleep") "sleep" else "work")
            if (permission.isNotEmpty()) return permission
            if (name == "sleep" && Build.VERSION.SDK_INT >= 31 && context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() != true)
                return context.getString(R.string.feature_alarm_permission)
        }
        prefs(context).edit { putBoolean(name, enabled) }
        schedule(context)
        return ""
    }
    fun schedule(context: Context) {
        val intent = PendingIntent.getBroadcast(context, 23, Intent(context, SceneAlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService<AlarmManager>() ?: return
        alarms.cancel(intent)
        if (!prefs(context).getBoolean("sleep", false)) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, intent)
    }
}

class SceneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { SceneSupport.schedule(context); return }
        if (SceneSupport.prefs(context).getBoolean("sleep", false)) {
            SceneSupport.applyLocal(context, "sleep")
            KdeConnect.getInstance().devices.values.filter { it.isPaired && it.isReachable }.forEach {
                KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(it.deviceId)?.request("scene", "sleep")
            }
        }
        SceneSupport.schedule(context)
    }
}
