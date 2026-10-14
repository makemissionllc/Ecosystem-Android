package org.kde.kdeconnect.plugins.receivenotifications

import android.app.Application
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.NetworkPacket
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemoteNotificationsTest {
    @After fun clear() { RemoteNotifications.items.value.toList().forEach { RemoteNotifications.remove(it.deviceId, it.id) } }
    private fun notification(id: String, text: String) = NetworkPacket("kdeconnect.notification").apply {
        this["id"] = id; this["appName"] = "Messages"; this["ticker"] = text; this["time"] = "12345"
    }
    @Test fun updatesAnExistingNotificationAndRetainsReplyToken() {
        RemoteNotifications.receive("phone", notification("1", "First"))
        RemoteNotifications.receive("phone", notification("1", "Updated").apply { this["requestReplyId"] = "reply-token" })
        val rows = RemoteNotifications.items.value
        assertEquals(1, rows.size)
        assertEquals("Updated", rows.single().text)
        assertEquals("reply-token", rows.single().replyId)
        assertEquals(12345L, rows.single().time)
    }
    @Test fun cancellationWithoutTickerRemovesOnlyTheSendingDevicesNotification() {
        RemoteNotifications.receive("phone", notification("1", "Phone"))
        RemoteNotifications.receive("tablet", notification("1", "Tablet"))
        RemoteNotifications.receive("phone", NetworkPacket("kdeconnect.notification").apply { this["id"] = "1"; this["isCancel"] = true })
        assertEquals("tablet", RemoteNotifications.items.value.single().deviceId)
    }
    @Test fun preexistingNotificationsPopulateDigestWithoutDuplicates() {
        repeat(3) { RemoteNotifications.receive("phone", notification("same", "Preexisting").apply { this["silent"] = true }) }
        assertEquals(1, RemoteNotifications.items.value.size)
    }
    @Test fun historyRemainsBounded() {
        repeat(250) { RemoteNotifications.receive("phone", notification(it.toString(), "Text")) }
        assertEquals(200, RemoteNotifications.items.value.size)
        assertEquals("249", RemoteNotifications.items.value.first().id)
    }
}
