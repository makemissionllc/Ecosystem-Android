/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R

/** Consent is always obtained on the device whose screen is being shared. */
class MirrorConsentActivity : AppCompatActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val peerId = intent.getStringExtra("deviceId")
        if (result.resultCode == Activity.RESULT_OK && result.data != null && peerId != null &&
            KdeConnect.getInstance().getDevice(peerId)?.isPaired == true) {
            ContextCompat.startForegroundService(this, Intent(this, MirrorService::class.java)
                .putExtra("deviceId", peerId).putExtra("consent", result.data).putExtra("result", result.resultCode))
        }
        finish()
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestProjection()
    }
    private fun requestProjection() {
        val manager = getSystemService<MediaProjectionManager>()
        if (manager == null) finish() else consent.launch(manager.createScreenCaptureIntent())
    }
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            else requestProjection()
        }
    }
}

class MirrorService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val thread = HandlerThread("EcosystemMirror")
    private lateinit var handler: Handler
    private var lastFrameAt = 0L
    private var captureWidth = 0
    private var captureHeight = 0
    @Volatile private var audioEnabled = false
    @Volatile private var audioGeneration = 0
    @Volatile private var audioRecord: android.media.AudioRecord? = null
    private var audioThread: Thread? = null
    private val audioSending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Synchronized internal fun setAudioEnabled(enabled: Boolean) {
        if (enabled && audioEnabled && audioThread?.isAlive == true) return
        val generation = ++audioGeneration
        audioEnabled = enabled
        if (!enabled) { runCatching { audioRecord?.stop() }; return }
        if (Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val capture = projection ?: return
        val peer = MirrorSession.sharingDeviceId?.let { KdeConnect.getInstance().getDevice(it) } ?: return
        audioThread = Thread({
            var record: android.media.AudioRecord? = null
            try {
                record = android.media.AudioRecord.Builder().setAudioFormat(android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(android.media.AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(16384).setAudioPlaybackCaptureConfig(android.media.AudioPlaybackCaptureConfiguration.Builder(capture)
                        .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA).addMatchingUsage(android.media.AudioAttributes.USAGE_GAME).build()).build()
                audioRecord = record; record.startRecording()
                val data = ByteArray(2048)
                while (audioEnabled && audioGeneration == generation && peer.isReachable) {
                    val count = record.read(data, 0, data.size)
                    if (count <= 0) break
                    if (!audioSending.compareAndSet(false, true)) continue
                    val packet = org.kde.kdeconnect.NetworkPacket(MirrorSession.AUDIO).apply {
                        this["audio"] = android.util.Base64.encodeToString(data, 0, count, android.util.Base64.NO_WRAP)
                    }
                    peer.sendPacket(packet, object : org.kde.kdeconnect.Device.SendPacketStatusCallback() {
                        override fun onSuccess() { audioSending.set(false) }
                        override fun onFailure(e: Throwable) { audioSending.set(false) }
                    })
                }
            } catch (e: Exception) { audioEnabled = false }
            finally { runCatching { record?.stop() }; record?.release(); if (audioRecord === record) audioRecord = null }
        }, "EcosystemAudioCapture").apply { start() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); thread.start(); handler = Handler(thread.looper) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val peer = intent?.getStringExtra("deviceId")?.let { KdeConnect.getInstance().getDevice(it) }
        @Suppress("DEPRECATION") val consent = intent?.getParcelableExtra<Intent>("consent")
        if (peer == null || !peer.isPaired || !peer.isReachable || consent == null) { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(this, 918, Intent(this, MirrorService::class.java).setAction("stop"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, NotificationHelper.Channels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(getString(R.string.feature_sharing_with, peer.name))
            .setOngoing(true).addAction(R.drawable.ic_stop, getString(R.string.feature_stop_mirror), stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(918, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(918, notification)
        try {
            val capture = requireNotNull(getSystemService<MediaProjectionManager>()!!.getMediaProjection(intent!!.getIntExtra("result", Activity.RESULT_CANCELED), consent))
            projection = capture
            capture.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
                override fun onCapturedContentResize(width: Int, height: Int) {
                    configureReader(peer, width, height)
                }
            }, handler)
            val metrics = resources.displayMetrics
            configureReader(peer, metrics.widthPixels, metrics.heightPixels)
            MirrorSession.sharingDeviceId = peer.deviceId
            MirrorSession.service = this
            display = capture.createVirtualDisplay("Ecosystem", captureWidth, captureHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)

        } catch (e: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
    private fun configureReader(peer: org.kde.kdeconnect.Device, sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val scale = (720f / sourceWidth).coerceAtMost(1f)
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
        if (captureWidth == width && captureHeight == height && reader != null) return
        val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val old = reader
        reader = images; captureWidth = width; captureHeight = height
        display?.resize(width, height, resources.displayMetrics.densityDpi)
        display?.surface = images.surface
        old?.setOnImageAvailableListener(null, null); old?.close()
        images.setOnImageAvailableListener({ imageReader ->
            val image = runCatching { imageReader.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (!peer.isReachable) { stopSelf(); return@setOnImageAvailableListener }
                if (now - lastFrameAt < 200) return@setOnImageAvailableListener
                lastFrameAt = now
                val plane = image.planes[0]
                val paddedWidth = width + (plane.rowStride - plane.pixelStride * width) / plane.pixelStride
                val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                MirrorSession.send(peer, cropped)
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            } catch (e: Exception) {
                android.util.Log.w("EcosystemMirror", "Capture frame unavailable", e)
            } finally { image.close() }
        }, handler)
    }
    override fun onDestroy() {
        setAudioEnabled(false)
        if (MirrorPower.screenOff) MirrorPower.set(this, true) {}
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        MirrorSession.sharingDeviceId = null; MirrorSession.service = null
        thread.quitSafely()
        super.onDestroy()
    }
}
