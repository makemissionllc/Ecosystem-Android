/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal data class MirrorViewState(val frame: Bitmap? = null, val screenshot: Bitmap? = null,
    val lastFrameAt: Long = 0, val frames: Long = 0, val startedAt: Long = 0)

internal object MirrorSession {
    const val FRAME = "ecosystem.mirror.frame"
    const val AUDIO = "ecosystem.mirror.audio"
    private val audioPlayers = mutableMapOf<String, MirrorAudio>()
    @Synchronized fun playAudio(deviceId: String, enabled: Boolean) {
        audioPlayers.remove(deviceId)?.stop()
        if (enabled) audioPlayers[deviceId] = MirrorAudio()
    }
    @Synchronized fun receiveAudio(deviceId: String, packet: NetworkPacket) {
        val player = audioPlayers[deviceId] ?: return
        val encoded = packet.getString("audio")
        if (encoded.length > 8192) return
        runCatching { player.offer(Base64.decode(encoded, Base64.NO_WRAP)) }
    }
    private val mutableViews = MutableStateFlow<Map<String, MirrorViewState>>(emptyMap())
    val views = mutableViews.asStateFlow()
    @Volatile var sharingDeviceId: String? = null
    @Volatile var service: MirrorService? = null
    private val castWindowHeartbeats = mutableMapOf<String, Long>()
    private val sending = AtomicBoolean(false)
    private val requested = mutableSetOf<String>()
    @Synchronized fun expect(deviceId: String) { requested.add(deviceId) }
    @Synchronized fun disconnected(deviceId: String) {
        requested.remove(deviceId)
        castWindowHeartbeats.remove(deviceId)
        audioPlayers.remove(deviceId)?.stop()
        mutableViews.value = mutableViews.value - deviceId
        if (sharingDeviceId == deviceId) service?.stopSelf()
    }
    @Synchronized fun recordCastWindowHeartbeat(deviceId: String) {
        castWindowHeartbeats[deviceId] = android.os.SystemClock.elapsedRealtime()
    }
    @Synchronized fun castWindowHeartbeatExpired(deviceId: String, timeoutMs: Long): Boolean {
        val lastHeartbeat = castWindowHeartbeats[deviceId] ?: return false
        return android.os.SystemClock.elapsedRealtime() - lastHeartbeat > timeoutMs
    }
    @Synchronized fun clearCastWindowHeartbeat(deviceId: String?) {
        if (deviceId != null) castWindowHeartbeats.remove(deviceId)
    }
    fun offer(context: Context, deviceId: String): Boolean {
        val peer = KdeConnect.getInstance().getDevice(deviceId) ?: return false
        val launch = PendingIntent.getActivity(context, deviceId.hashCode(), Intent(context, MirrorConsentActivity::class.java)
            .putExtra("deviceId", deviceId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.feature_mirror_request, peer.name))
            .setContentText(context.getString(R.string.feature_mirror_consent)).setContentIntent(launch).setAutoCancel(true)
            .addAction(R.drawable.ic_accept_pairing_24dp, context.getString(R.string.feature_share_screen), launch).build()
        if (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(deviceId.hashCode(), notification)
            return true
        }
        return false
    }
    fun stop(context: Context, deviceId: String) {
        if (sharingDeviceId == deviceId) context.stopService(Intent(context, MirrorService::class.java))
        disconnected(deviceId)
    }
    fun send(device: Device, bitmap: Bitmap, screenshot: Boolean = false) {
        if (!screenshot && !sending.compareAndSet(false, true)) return
        try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, if (screenshot) 85 else 65, output)
            val np = NetworkPacket(FRAME).apply {
                this["image"] = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                this["screenshot"] = screenshot
            }
            device.sendPacket(np, object : Device.SendPacketStatusCallback() {
                override fun onSuccess() { if (!screenshot) sending.set(false) }
                override fun onFailure(e: Throwable) { if (!screenshot) sending.set(false) }
            })
        } catch (e: Exception) { if (!screenshot) sending.set(false) }
    }
    @Synchronized fun receive(deviceId: String, packet: NetworkPacket) {
        if (deviceId !in requested) return
        val encoded = packet.getString("image")
        if (encoded.length > 2_000_000) return
        val image = runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) null
            else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return
        val old = mutableViews.value[deviceId] ?: MirrorViewState()
        val now = System.currentTimeMillis()
        mutableViews.value = mutableViews.value + (deviceId to if (packet.getBoolean("screenshot", false)) old.copy(screenshot = image)
            else old.copy(frame = image, lastFrameAt = now, frames = old.frames + 1, startedAt = old.startedAt.takeIf { it > 0 } ?: now))
    }
}
