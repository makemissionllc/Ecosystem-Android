/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** A small bounded jitter queue prevents delayed sound from accumulating on slow links. */
internal class MirrorAudio {
    private val queue = ArrayBlockingQueue<ByteArray>(6)
    @Volatile private var running = true
    private var track: AudioTrack? = null
    private val worker = Thread({
        try {
            val output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(16384).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = output; output.play()
            while (running) {
                val data = queue.poll(250, TimeUnit.MILLISECONDS) ?: continue
                output.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Exception) { running = false }
        finally { runCatching { track?.stop() }; track?.release(); track = null }
    }, "EcosystemAudioPlayback").apply { start() }
    fun offer(data: ByteArray) { if (running && !queue.offer(data)) { queue.poll(); queue.offer(data) } }
    fun stop() { running = false; queue.clear(); worker.interrupt() }
}
