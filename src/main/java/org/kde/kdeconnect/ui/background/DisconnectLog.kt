/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.lan.LanLink
import org.kde.kdeconnect_tp.R

internal enum class DisconnectCause(val text: Int) {
    UNKNOWN(R.string.disconnect_cause_unknown),
    WIFI_OFF(R.string.disconnect_cause_wifi_off),
    NETWORK_LOST(R.string.disconnect_cause_network_lost),
    NETWORK_CHANGED(R.string.disconnect_cause_network_changed),
    IDLE(R.string.disconnect_cause_idle),
    SERVICE_STOPPED(R.string.disconnect_cause_service_stopped),
    SERVICE_BLOCKED(R.string.disconnect_cause_service_blocked),
    PROCESS_RESTARTED(R.string.disconnect_cause_process_restarted),
    LOW_MEMORY(R.string.disconnect_cause_low_memory),
    CRASH(R.string.disconnect_cause_crash),
    USER_ACTION(R.string.disconnect_cause_user_action),
    REBOOT(R.string.disconnect_cause_reboot),
    APP_UPDATE(R.string.disconnect_cause_update),
    SHIZUKU_STOPPED(R.string.disconnect_cause_shizuku),
}

internal data class DisconnectLogEntry(
    val timestamp: Long,
    val deviceName: String,
    val connected: Boolean,
    val cause: DisconnectCause = DisconnectCause.UNKNOWN,
    val component: Boolean = false,
    val linkFrom: String? = null,
    val linkTo: String? = null,
    val reason: String? = null,
)

internal object DisconnectLog {
    private const val PREFERENCES = "ecosystem_background"
    private const val KEY_ENTRIES = "disconnect_log"
    private const val MAX_ENTRIES = 5_000
    @Volatile private var networkCause = DisconnectCause.UNKNOWN
    @Volatile private var networkChangedAt = 0L
    @Volatile private var lastNetworkWasWifi = false
    @Volatile private var serviceStoppedAt = 0L

    fun networkAvailable(wifi: Boolean, changed: Boolean) {
        lastNetworkWasWifi = wifi
        if (changed) {
            networkCause = DisconnectCause.NETWORK_CHANGED
            networkChangedAt = SystemClock.elapsedRealtime()
        }
    }

    fun networkLost(context: Context, wifi: Boolean, anotherNetworkAvailable: Boolean) {
        lastNetworkWasWifi = wifi
        networkCause = when {
            anotherNetworkAvailable -> DisconnectCause.NETWORK_CHANGED
            wifi && context.getSystemService(WifiManager::class.java)?.isWifiEnabled == false -> DisconnectCause.WIFI_OFF
            else -> DisconnectCause.NETWORK_LOST
        }
        networkChangedAt = SystemClock.elapsedRealtime()
    }

    fun connectionLostCause(context: Context, link: BaseLink): DisconnectCause {
        val now = SystemClock.elapsedRealtime()
        if (serviceStoppedAt > 0 && now - serviceStoppedAt < 15_000) return DisconnectCause.SERVICE_STOPPED
        if (link is LanLink) {
            if (lastNetworkWasWifi && context.getSystemService(WifiManager::class.java)?.isWifiEnabled == false) {
                return DisconnectCause.WIFI_OFF
            }
            if (networkChangedAt > 0 && now - networkChangedAt < 15_000) return networkCause
        }
        if (context.getSystemService(PowerManager::class.java)?.isDeviceIdleMode == true) return DisconnectCause.IDLE
        return DisconnectCause.UNKNOWN
    }

    fun noteRestart(context: Context, reason: DisconnectCause) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString("pending_restart", reason.name) }
    }

    fun serviceStarted(context: Context) {
        serviceStoppedAt = 0
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (prefs.getBoolean("connection_service_running", false)) {
            val reason = when {
                boot != prefs.getInt("connection_service_boot", boot) -> DisconnectCause.REBOOT
                prefs.getString("pending_restart", null) == DisconnectCause.APP_UPDATE.name -> DisconnectCause.APP_UPDATE
                else -> previousExitCause(context, prefs.getLong("connection_service_started", 0))
            }
            val connected = runCatching {
                JSONObject(prefs.getString("connected_before_restart", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            connected.keys().asSequence().toList().forEach { name -> record(context, name, false, reason) }
        }
        prefs.edit {
            putBoolean("connection_service_running", true)
            putInt("connection_service_boot", boot)
            putLong("connection_service_started", System.currentTimeMillis())
            remove("pending_restart")
        }
    }

    fun serviceStopped(context: Context) {
        serviceStoppedAt = SystemClock.elapsedRealtime()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean("connection_service_running", false) }
    }

    private fun previousExitCause(context: Context, startedAt: Long): DisconnectCause {
        if (Build.VERSION.SDK_INT < 30) return DisconnectCause.PROCESS_RESTARTED
        val exit = runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull { it.processName == context.packageName && it.timestamp >= startedAt }
        }.getOrNull() ?: return DisconnectCause.PROCESS_RESTARTED
        return when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> DisconnectCause.LOW_MEMORY
            ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR -> DisconnectCause.CRASH
            ApplicationExitInfo.REASON_USER_REQUESTED -> DisconnectCause.USER_ACTION
            else -> DisconnectCause.PROCESS_RESTARTED
        }
    }

    @Synchronized
    fun record(
        context: Context,
        deviceName: String,
        connected: Boolean,
        cause: DisconnectCause = DisconnectCause.UNKNOWN,
        component: Boolean = false,
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val entries = entries(context).toMutableList()
        val now = System.currentTimeMillis()
        if (entries.firstOrNull()?.let {
                it.deviceName == deviceName && it.connected == connected && it.cause == cause &&
                    it.component == component && now - it.timestamp < 2_000
            } == true) return
        entries.add(0, DisconnectLogEntry(now, deviceName, connected, cause, component))
        val json = JSONArray()
        entries.take(MAX_ENTRIES).forEach { json.put(it.toJson()) }
        val connectedDevices = runCatching {
            JSONObject(prefs.getString("connected_before_restart", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        if (!component) {
            if (connected) connectedDevices.put(deviceName, true) else connectedDevices.remove(deviceName)
        }
        prefs.edit {
            putString(KEY_ENTRIES, json.toString())
            putString("connected_before_restart", connectedDevices.toString())
        }
    }

    @Synchronized
    fun recordLinkSwitch(context: Context, deviceName: String, from: String, to: String, reason: String) {
        val entries = entries(context).toMutableList()
        entries.add(0, DisconnectLogEntry(
            timestamp = System.currentTimeMillis(),
            deviceName = deviceName,
            connected = true,
            linkFrom = from,
            linkTo = to,
            reason = reason,
        ))
        val json = JSONArray()
        entries.take(MAX_ENTRIES).forEach { json.put(it.toJson()) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putString(KEY_ENTRIES, json.toString()) }
    }

    @Synchronized
    fun entries(context: Context): List<DisconnectLogEntry> = runCatching {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null)
            ?: return emptyList()
        val json = JSONArray(stored)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val timestamp = item.optLong("timestamp", 0)
            val name = item.optString("device", "").takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (timestamp <= 0) return@mapNotNull null
            val cause = runCatching { DisconnectCause.valueOf(item.optString("cause")) }
                .getOrDefault(DisconnectCause.UNKNOWN)
            DisconnectLogEntry(
                timestamp = timestamp,
                deviceName = name,
                connected = item.optBoolean("connected"),
                cause = cause,
                component = item.optBoolean("component"),
                linkFrom = item.optString("linkFrom").takeIf(String::isNotBlank),
                linkTo = item.optString("linkTo").takeIf(String::isNotBlank),
                reason = item.optString("reason").takeIf(String::isNotBlank),
            )
        }
    }.getOrDefault(emptyList())

    private fun DisconnectLogEntry.toJson() = JSONObject()
        .put("timestamp", timestamp)
        .put("device", deviceName)
        .put("connected", connected)
        .put("cause", cause.name)
        .put("component", component)
        .put("linkFrom", linkFrom)
        .put("linkTo", linkTo)
        .put("reason", reason)
}
