/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.clipboard

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ClipboardHistoryItem(val id: String, val text: String, val source: String, val time: Long, val pinned: Boolean)

/** Bounded, private history. Password/sensitive clips are rejected by callers before reaching this store. */
internal object ClipboardHistory {
    private val mutableItems = MutableStateFlow<List<ClipboardHistoryItem>>(emptyList())
    val items = mutableItems.asStateFlow()
    private var loaded = false

    @Synchronized fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = runCatching { JSONArray(prefs(context).getString("items", "[]")) }.getOrDefault(JSONArray())
        mutableItems.value = (0 until json.length()).mapNotNull { index ->
            json.optJSONObject(index)?.let { ClipboardHistoryItem(it.optString("id"), it.optString("text"),
                it.optString("source"), it.optLong("time"), it.optBoolean("pinned")) }
        }
    }

    @Synchronized fun record(context: Context, text: String, source: String) {
        if (text.isBlank() || text.length > 100_000) return
        load(context)
        val existing = mutableItems.value.firstOrNull { it.text == text }
        val next = listOf(ClipboardHistoryItem(existing?.id ?: UUID.randomUUID().toString(), text, source,
            System.currentTimeMillis(), existing?.pinned ?: false)) + mutableItems.value.filter { it.text != text }
        save(context, next.filter { it.pinned } + next.filterNot { it.pinned }.take(100))
    }

    @Synchronized fun pin(context: Context, id: String) {
        load(context)
        save(context, mutableItems.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    @Synchronized fun delete(context: Context, id: String) {
        load(context)
        save(context, mutableItems.value.filterNot { it.id == id })
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ecosystem_clipboard_history", Context.MODE_PRIVATE)
    private fun save(context: Context, items: List<ClipboardHistoryItem>) {
        prefs(context).edit { putString("items", JSONArray().apply { items.forEach {
            put(JSONObject().put("id", it.id).put("text", it.text).put("source", it.source)
                .put("time", it.time).put("pinned", it.pinned))
        } }.toString()) }
        mutableItems.value = items
    }
}
