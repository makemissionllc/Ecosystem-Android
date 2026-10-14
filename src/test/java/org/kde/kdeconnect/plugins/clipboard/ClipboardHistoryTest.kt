package org.kde.kdeconnect.plugins.clipboard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ClipboardHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() {
        ClipboardHistory.load(context)
        ClipboardHistory.items.value.toList().forEach { ClipboardHistory.delete(context, it.id) }
        context.getSharedPreferences("ecosystem_clipboard_sync", 0).edit().clear().commit()
    }
    @Test fun repeatedCopiesKeepPinnedEntryAndUpdateSource() {
        ClipboardHistory.record(context, "Address", "Phone")
        val id = ClipboardHistory.items.value.single().id
        ClipboardHistory.pin(context, id)
        ClipboardHistory.record(context, "Address", "Tablet")
        val item = ClipboardHistory.items.value.single()
        assertEquals(id, item.id); assertTrue(item.pinned); assertEquals("Tablet", item.source)
    }
    @Test fun recentHistoryIsBoundedWithoutEvictingPinnedClips() {
        ClipboardHistory.record(context, "Saved", "Phone")
        ClipboardHistory.pin(context, ClipboardHistory.items.value.single().id)
        repeat(150) { ClipboardHistory.record(context, "Clip $it", "Phone") }
        assertEquals(101, ClipboardHistory.items.value.size)
        assertTrue(ClipboardHistory.items.value.any { it.text == "Saved" && it.pinned })
    }
    @Test fun blankAndOversizedClipsAreNotStored() {
        ClipboardHistory.record(context, "   ", "Phone")
        ClipboardHistory.record(context, "x".repeat(100001), "Phone")
        assertTrue(ClipboardHistory.items.value.isEmpty())
    }
    @Test fun sensitiveIncomingClipsNeverReachHistory() {
        assertFalse(ClipboardSyncManager.applyIncoming(context, "Secret", "id", "Phone", sensitive = true))
        assertTrue(ClipboardHistory.items.value.isEmpty())
    }
    @Test fun pausedSyncRejectsIncomingAutomaticDelivery() {
        context.getSharedPreferences("ecosystem_clipboard_sync", 0).edit().putBoolean("enabled", true)
            .putLong("paused_until", System.currentTimeMillis() + 3600000).commit()
        assertFalse(ClipboardSyncManager.applyIncoming(context, "Text", "id", "Phone"))
        assertTrue(ClipboardHistory.items.value.isEmpty())
    }
}
