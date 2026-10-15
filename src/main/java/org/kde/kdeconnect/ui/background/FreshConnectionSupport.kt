/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect_tp.R

internal data class FreshConnectionStatus(
    val resetting: Boolean = false,
    val connected: Boolean? = null,
    val message: String = "",
    val updatedAt: Long = 0,
)

/** Persists the Quick Settings tile result while keeping reset state process-safe. */
internal object FreshConnectionSupport {
    private const val PREFERENCES = "ecosystem_background"
    private const val RESETTING = "fresh_connection_resetting"
    private const val CONNECTED = "fresh_connection_connected"
    private const val MESSAGE = "fresh_connection_message"
    private const val UPDATED_AT = "fresh_connection_updated_at"
    private const val STALE_RESET_MS = 30_000L
    private val lock = Any()
    private val mutableStatus = MutableStateFlow(FreshConnectionStatus())
    val status = mutableStatus.asStateFlow()
    @Volatile private var initialized = false
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
            mutableStatus.value = readStatus(appContext)
            val old = mutableStatus.value
            if (old.resetting && System.currentTimeMillis() - old.updatedAt > STALE_RESET_MS) {
                finish(appContext, false, appContext.getString(R.string.fresh_connection_interrupted), showToast = false)
            }
        }
    }

    fun request(context: Context): Boolean {
        initialize(context)
        synchronized(lock) {
            val previous = mutableStatus.value
            if (previous.resetting && System.currentTimeMillis() - previous.updatedAt <= STALE_RESET_MS) return false
            mutableStatus.value = FreshConnectionStatus(
                resetting = true,
                message = context.getString(R.string.fresh_connection_resetting),
                updatedAt = System.currentTimeMillis(),
            )
            persist(context, mutableStatus.value)
        }

        runCatching {
            val intent = Intent(context, BackgroundService::class.java).setAction(BackgroundService.ACTION_FRESH_CONNECTION)
            ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            finish(context, false, context.getString(R.string.fresh_connection_start_failed, it.javaClass.simpleName))
            return false
        }
        return true
    }

    fun isResetting(context: Context): Boolean {
        initialize(context)
        return mutableStatus.value.resetting
    }

    fun finish(context: Context, connected: Boolean, reason: String, showToast: Boolean = true) {
        initialize(context)
        val message = if (connected) context.getString(R.string.fresh_connection_connected, reason)
            else context.getString(R.string.fresh_connection_failed, reason)
        synchronized(lock) {
            mutableStatus.value = FreshConnectionStatus(
                resetting = false,
                connected = connected,
                message = message,
                updatedAt = System.currentTimeMillis(),
            )
            persist(context, mutableStatus.value)
        }
        DisconnectLog.record(
            context,
            context.getString(R.string.fresh_connection),
            connected,
            if (connected) DisconnectCause.USER_ACTION else DisconnectCause.FRESH_CONNECTION,
            reason = reason,
        )
        if (showToast) Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        requestTileRefresh(context)
    }

    fun pulse(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
            } else @Suppress("DEPRECATION") vibrator?.vibrate(24)
        }
    }

    private fun requestTileRefresh(context: Context) {
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, FreshConnectionTileService::class.java))
        }
    }

    private fun readStatus(context: Context): FreshConnectionStatus = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).let { prefs ->
        val result = prefs.getInt(CONNECTED, -1)
        FreshConnectionStatus(
            resetting = prefs.getBoolean(RESETTING, false),
            connected = when (result) { 0 -> false; 1 -> true; else -> null },
            message = prefs.getString(MESSAGE, "").orEmpty(),
            updatedAt = prefs.getLong(UPDATED_AT, 0),
        )
    }

    private fun persist(context: Context, value: FreshConnectionStatus) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putBoolean(RESETTING, value.resetting)
            putInt(CONNECTED, when (value.connected) { true -> 1; false -> 0; null -> -1 })
            putString(MESSAGE, value.message)
            putLong(UPDATED_AT, value.updatedAt)
        }
    }
}
