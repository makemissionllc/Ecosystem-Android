/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnectAccessibilityService
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.features.SceneSupport
import org.kde.kdeconnect_tp.R
import java.util.UUID

internal data class PhoneApp(val label: String, val packageName: String)
internal data class RemoteControlState(val volume: Int = 0, val maximumVolume: Int = 1,
    val accessibility: Boolean = false, val audioAvailable: Boolean = false, val screenPowerAvailable: Boolean = false, val screenOff: Boolean = false, val apps: List<PhoneApp> = emptyList(), val message: String = "", val latency: Long? = null)

@LoadablePlugin
class EcosystemPlugin : Plugin() {
    override val displayName get() = context.getString(R.string.feature_controls)
    override val description get() = context.getString(R.string.feature_controls_description)
    override val supportedPacketTypes = arrayOf(REQUEST, RESPONSE, MirrorSession.FRAME, MirrorSession.AUDIO)
    override val outgoingPacketTypes = arrayOf(REQUEST, RESPONSE, MirrorSession.FRAME, MirrorSession.AUDIO)
    private val mutableState = MutableStateFlow(RemoteControlState())
    internal val state = mutableState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, Long>()
    override fun onCreate() { request("status") }
    override fun onDestroy() { pending.clear(); MirrorSession.disconnected(device.deviceId) }

    internal fun request(action: String, value: String = "") {
        val id = UUID.randomUUID().toString()
        synchronized(pending) { pending[id] = android.os.SystemClock.elapsedRealtime() }
        device.sendPacket(NetworkPacket(REQUEST).apply { this["id"] = id; this["action"] = action; this["value"] = value })
        handler.postDelayed({
            if (synchronized(pending) { pending.remove(id) } != null) {
                mutableState.value = mutableState.value.copy(message = context.getString(R.string.feature_request_timeout))
            }
        }, 10_000)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type == MirrorSession.AUDIO) { MirrorSession.receiveAudio(device.deviceId, np); return true }
        if (np.type == MirrorSession.FRAME) { MirrorSession.receive(device.deviceId, np); return true }
        if (np.type == RESPONSE) {
            val sent = synchronized(pending) { pending.remove(np.getString("id")) }
            val apps = np.getJSONArray("apps")
            val old = mutableState.value
            mutableState.value = old.copy(volume = np.getInt("volume", old.volume), maximumVolume = np.getInt("maximumVolume", old.maximumVolume),
                accessibility = np.getBoolean("accessibility", old.accessibility),
                audioAvailable = np.getBoolean("audioAvailable", old.audioAvailable),
                screenPowerAvailable = np.getBoolean("screenPowerAvailable", old.screenPowerAvailable),
                screenOff = np.getBoolean("screenOff", old.screenOff),
                message = np.getString("message"), latency = if (np.getString("action") == "ping" && sent != null)
                    android.os.SystemClock.elapsedRealtime() - sent else old.latency,
                apps = if (apps == null) old.apps else (0 until apps.length()).map {
                    apps.getJSONObject(it).let { row -> PhoneApp(row.getString("label"), row.getString("package")) }
                })
            return true
        }
        handler.post { handleRequest(np) }
        return true
    }

    private fun handleRequest(np: NetworkPacket) {
        if (!device.isPaired) return
        val action = np.getString("action")
        val value = np.getString("value")
        val reply = NetworkPacket(RESPONSE).apply { this["id"] = np.getString("id"); this["action"] = action }
        val audio = context.getSystemService<AudioManager>()
        val accessibility = KdeConnectAccessibilityService.instance
        try {
            when (action) {
                "status", "ping" -> Unit
                "volume" -> audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value.toIntOrNull()?.coerceIn(0,
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) ?: 0, 0)
                "back", "home", "recent", "lock" -> {
                    val code = when (action) {
                        "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                        "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                        "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                        else -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else -1
                    }
                    if (code == -1 || accessibility?.performGlobalAction(code) != true) reply["message"] = context.getString(R.string.feature_accessibility_required)
                }
                "apps" -> {
                    val apps = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    reply["apps"] = JSONArray().apply { apps.sortedBy { it.loadLabel(context.packageManager).toString() }.take(300).forEach {
                        put(JSONObject().put("label", it.loadLabel(context.packageManager).toString()).put("package", it.activityInfo.packageName))
                    } }
                }
                "launch", "wake" -> if (MirrorPower.available(context)) {
                    MirrorPower.execute(context, action, value) { success ->
                        if (!success) reply["message"] = context.getString(R.string.feature_action_failed)
                        device.sendPacket(reply)
                    }
                    return
                } else if (action == "launch" && androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    context.packageManager.getLaunchIntentForPackage(value)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        ?: run { reply["message"] = context.getString(R.string.feature_app_unavailable) }
                } else reply["message"] = context.getString(R.string.feature_remote_shizuku_required)
                "text" -> {
                    val node = accessibility?.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                        }) != true) reply["message"] = context.getString(R.string.feature_accessibility_required)
                }
                "scene" -> reply["message"] = SceneSupport.applyLocal(context, value)
                "mirror" -> if (!MirrorSession.offer(context, device.deviceId)) reply["message"] = context.getString(R.string.feature_mirror_notification_permission)
                "castWindow" -> {
                    if (value == "heartbeat") {
                        if (MirrorSession.sharingDeviceId == device.deviceId) {
                            MirrorSession.recordCastWindowHeartbeat(device.deviceId)
                        }
                    } else if (value != MirrorWindowActivity.DIRECTION_RECEIVE) {
                        reply["message"] = context.getString(R.string.cast_screen_unavailable)
                    } else {
                        MirrorSession.expect(device.deviceId)
                        CastWindowSupport.tryFreeform(context, device.deviceId, MirrorWindowActivity.DIRECTION_RECEIVE) { result ->
                            if (result.launched) {
                                CastWindowSupport.rememberFreeform(context)
                                reply["message"] = context.getString(R.string.cast_freeform_ready)
                            } else {
                                CastWindowSupport.rememberFallback(context, result.reason)
                                val appVisible = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                                    .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                                val opened = if (appVisible) {
                                    runCatching { CastWindowSupport.launchActivity(context, device.deviceId,
                                        MirrorWindowActivity.DIRECTION_RECEIVE); true }.getOrElse {
                                        runCatching { CastWindowSupport.launchLegacyMirror(context, device.deviceId); true }.getOrDefault(false)
                                    }
                                } else CastWindowSupport.showTapToOpen(context, device.deviceId)
                                reply["message"] = if (opened) context.getString(R.string.cast_opening)
                                    else context.getString(R.string.cast_screen_unavailable)
                            }
                            device.sendPacket(reply)
                        }
                        return
                    }
                }
                "stopMirror" -> MirrorSession.stop(context, device.deviceId)
                "audio" -> if (MirrorSession.sharingDeviceId == device.deviceId) {
                    MirrorSession.service?.setAudioEnabled(value == "true")
                }
                "screenOff" -> if (MirrorSession.sharingDeviceId == device.deviceId) {
                    MirrorPower.set(context, value != "true") { success ->
                        reply["screenOff"] = MirrorPower.screenOff
                        if (!success) reply["message"] = context.getString(R.string.feature_screen_power_failed)
                        device.sendPacket(reply)
                    }
                    return
                }
                "rotate" -> if (MirrorPower.available(context)) {
                    MirrorPower.execute(context, "rotate") { success ->
                        if (!success) reply["message"] = context.getString(R.string.feature_action_failed)
                        device.sendPacket(reply)
                    }
                    return
                } else if (Settings.System.canWrite(context)) {
                    val rotation = Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0)
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, (rotation + 1) % 4)
                } else reply["message"] = context.getString(R.string.feature_brightness_permission)
                "touch" -> if (MirrorSession.sharingDeviceId == device.deviceId && Build.VERSION.SDK_INT >= 24) {
                    val coordinates = value.split(',').mapNotNull { it.toFloatOrNull() }
                    if (coordinates.size == 4 && accessibility != null) {
                        val metrics = context.resources.displayMetrics
                        val path = android.graphics.Path().apply {
                            moveTo(coordinates[0].coerceIn(0f, 1f) * metrics.widthPixels, coordinates[1].coerceIn(0f, 1f) * metrics.heightPixels)
                            lineTo(coordinates[2].coerceIn(0f, 1f) * metrics.widthPixels, coordinates[3].coerceIn(0f, 1f) * metrics.heightPixels)
                        }
                        accessibility.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(
                            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 120)).build(), null, null)
                    }
                }
                "screenshot" -> if (Build.VERSION.SDK_INT >= 30 && accessibility != null) {
                    accessibility.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, context.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                                val buffer = result.hardwareBuffer
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                if (bitmap != null) {
                                    val copy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    bitmap.recycle(); buffer.close()
                                    MirrorSession.send(device, copy, screenshot = true); copy.recycle()
                                } else buffer.close()
                            }
                            override fun onFailure(errorCode: Int) {
                                reply["message"] = context.getString(R.string.feature_screenshot_failed)
                                device.sendPacket(reply)
                            }
                        })
                } else reply["message"] = context.getString(R.string.feature_accessibility_required)
            }
        } catch (e: Exception) { reply["message"] = context.getString(R.string.feature_action_failed) }
        reply["volume"] = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        reply["maximumVolume"] = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
        reply["accessibility"] = accessibility != null
        reply["audioAvailable"] = Build.VERSION.SDK_INT >= 29 && androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        reply["screenPowerAvailable"] = MirrorPower.available(context)
        reply["screenOff"] = MirrorPower.screenOff
        device.sendPacket(reply)
    }
    companion object {
        const val REQUEST = "ecosystem.control.request"
        const val RESPONSE = "ecosystem.control.response"
    }
}
