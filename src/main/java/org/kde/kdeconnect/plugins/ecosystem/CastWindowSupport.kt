/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.ui.background.ShizukuSupport
import org.kde.kdeconnect_tp.R
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

internal data class FreeformLaunchResult(val launched: Boolean, val reason: String)

/** Keeps the optional windowing path outside the screen capture protocol. */
internal object CastWindowSupport {
    private const val PREFS = "ecosystem_cast_window"
    private const val KEY_FALLBACK = "freeform_fallback"
    private const val KEY_REASON = "freeform_reason"
    private const val KEY_BOUNDS = "window_bounds"
    private const val CHANNEL = "ecosystem_cast_incoming"
    private const val NOTIFICATION_ID = 0x43415354
    private val main = Handler(Looper.getMainLooper())

    fun tryFreeform(context: Context, deviceId: String, direction: String, callback: (FreeformLaunchResult) -> Unit) {
        if (!DeviceInfo.isValidDeviceId(deviceId) || direction !in setOf(MirrorWindowActivity.DIRECTION_VIEW, MirrorWindowActivity.DIRECTION_RECEIVE)) {
            callback(FreeformLaunchResult(false, "invalid target"))
            return
        }
        if (!ShizukuSupport.state(context).authorized) {
            callback(FreeformLaunchResult(false, context.getString(R.string.shizuku_setup_title)))
            return
        }

        val app = context.applicationContext
        val args = Shizuku.UserServiceArgs(ComponentName(app, MirrorWindowUserService::class.java))
            .tag("ecosystem_mirror_window").version(1).daemon(false).processNameSuffix("mirror_window")
        val completed = AtomicBoolean(false)
        var bound = false
        lateinit var connection: ServiceConnection
        fun complete(result: FreeformLaunchResult) {
            if (!completed.compareAndSet(false, true)) return
            if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
            callback(result)
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val launcher = IMirrorWindowLauncher.Stub.asInterface(binder)
                Thread {
                    val launched = runCatching { launcher?.launchWindow(deviceId, direction) == true }.getOrDefault(false)
                    main.post {
                        complete(FreeformLaunchResult(launched,
                            if (launched) "" else "Shizuku window launch failed"))
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                main.post { complete(FreeformLaunchResult(false, "Shizuku service disconnected")) }
            }

            override fun onNullBinding(name: ComponentName?) {
                main.post { complete(FreeformLaunchResult(false, "Shizuku service returned no binding")) }
            }
        }
        main.post {
            try {
                bound = true
                Shizuku.bindUserService(args, connection)
                main.postDelayed({ complete(FreeformLaunchResult(false, "Shizuku launch timed out")) }, 10_000)
            } catch (e: Exception) {
                bound = false
                complete(FreeformLaunchResult(false, e.javaClass.simpleName))
            }
        }
    }

    fun rememberFallback(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FALLBACK, true)
            putString(KEY_REASON, reason)
        }
    }

    fun rememberFreeform(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FALLBACK, false)
            remove(KEY_REASON)
        }
    }

    fun healthStatus(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_FALLBACK, false)) {
            context.getString(R.string.cast_freeform_fallback, prefs.getString(KEY_REASON, "unknown") ?: "unknown")
        } else if (ShizukuSupport.state(context).authorized) {
            context.getString(R.string.cast_freeform_ready)
        } else {
            context.getString(R.string.cast_freeform_fallback, context.getString(R.string.shizuku_setup_title))
        }
    }

    fun launchActivity(context: Context, deviceId: String, direction: String, fromNotification: Boolean = false) {
        val intent = Intent(context, MirrorWindowActivity::class.java)
            .putExtra(MirrorWindowActivity.EXTRA_DEVICE_ID, deviceId)
            .putExtra(MirrorWindowActivity.EXTRA_DIRECTION, direction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (fromNotification) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ActivityOptions.makeBasic().apply {
            savedBounds(context)?.let(::setLaunchBounds)
        }.toBundle() else null
        if (options == null) context.startActivity(intent) else context.startActivity(intent, options)
    }

    fun launchLegacyMirror(context: Context, deviceId: String) {
        context.startActivity(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            .putExtra(MainActivity.EXTRA_OPEN_MIRROR, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    fun saveWindowBounds(activity: Activity) {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.windowManager.currentWindowMetrics.bounds else {
            val metrics = activity.resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        if (bounds.width() < activity.resources.displayMetrics.widthPixels * 0.96f ||
            bounds.height() < activity.resources.displayMetrics.heightPixels * 0.96f) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY_BOUNDS, bounds.flattenToString())
            }
        }
    }

    private fun savedBounds(context: Context): Rect? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_BOUNDS, null)?.let(Rect::unflattenFromString)

    fun showTapToOpen(context: Context, deviceId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            rememberFallback(context, "notifications are disabled")
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.cast_tile), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.cast_background_text)
            })
        }
        val intent = Intent(context, MirrorWindowActivity::class.java)
            .putExtra(MirrorWindowActivity.EXTRA_DEVICE_ID, deviceId)
            .putExtra(MirrorWindowActivity.EXTRA_DIRECTION, MirrorWindowActivity.DIRECTION_RECEIVE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val open = PendingIntent.getActivity(context, deviceId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.cast_background_title))
            .setContentText(context.getString(R.string.cast_background_text))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}

/** Runs the window launch as the Shizuku shell user, without changing pairing or capture. */
class MirrorWindowUserService() : IMirrorWindowLauncher.Stub() {
    private var applicationId: String = "com.ecosystem.personal"

    constructor(context: Context) : this() {
        applicationId = context.packageName
    }

    override fun launchWindow(deviceId: String, direction: String): Boolean {
        if (!DeviceInfo.isValidDeviceId(deviceId) || direction !in setOf("view", "receive")) return false
        val component = "$applicationId/org.kde.kdeconnect.plugins.ecosystem.MirrorWindowActivity"
        val data = "ecosystem://cast/$deviceId/$direction/${System.currentTimeMillis()}"
        return runCatching {
            val process = ProcessBuilder("am", "start", "-W", "--windowingMode", "5", "--activity-multiple-task",
                "-n", component, "-d", data, "--es", MirrorWindowActivity.EXTRA_DEVICE_ID, deviceId,
                "--es", MirrorWindowActivity.EXTRA_DIRECTION, direction).redirectErrorStream(true).start()
            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.exitValue() == 0 && output.contains("Status: ok") && !output.contains("Error:")
        }.getOrDefault(false)
    }
}
