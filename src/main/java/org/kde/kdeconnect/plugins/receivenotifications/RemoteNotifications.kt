/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.receivenotifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kde.kdeconnect.NetworkPacket

internal data class RemoteNotification(val deviceId: String, val id: String, val app: String,
    val title: String, val text: String, val time: Long, val replyId: String?, val clearable: Boolean)

internal object RemoteNotifications {
    private val mutableItems = MutableStateFlow<List<RemoteNotification>>(emptyList())
    val items = mutableItems.asStateFlow()
    @Synchronized fun receive(deviceId: String, packet: NetworkPacket) {
        val id = packet.getString("id")
        if (id.isBlank()) return
        val remaining = mutableItems.value.filterNot { it.deviceId == deviceId && it.id == id }
        mutableItems.value = if (packet.getBoolean("isCancel", false)) remaining else {
            listOf(RemoteNotification(deviceId, id, packet.getString("appName"), packet.getString("title"),
                packet.getString("text", packet.getString("ticker")),
                packet.getString("time").toLongOrNull() ?: System.currentTimeMillis(),
                packet.getString("requestReplyId").takeIf { it.isNotBlank() }, packet.getBoolean("isClearable", true))) + remaining.take(199)
        }
    }
    @Synchronized fun remove(deviceId: String, id: String) {
        mutableItems.value = mutableItems.value.filterNot { it.deviceId == deviceId && it.id == id }
    }
}
