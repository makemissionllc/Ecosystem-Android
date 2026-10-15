/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.clipboard

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.kde.kdeconnect.backends.BaseLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Content-free clipboard receive diagnostics, retained locally for inspection after a failure. */
internal object ClipboardDiagnostics {
    private const val TAG = "EcosystemClipboard"
    private const val PREFERENCES = "ecosystem_clipboard_diagnostics"
    private const val KEY_LINES = "ring_buffer"
    private const val KEY_VERSION = "ring_version"
    private const val RING_VERSION = 1
    private const val MAX_LINES = 200
    private const val REPEAT_INTERVAL_MS = 5 * 60 * 1000L
    private val lock = Any()
    private val lastRepeatableEvents = LinkedHashMap<String, Long>()
    private val writeContext = ThreadLocal<Pair<String, String?>?>()

    fun record(context: Context, id: String?, link: String?, stage: String, result: String) {
        val rawId = id?.takeIf(String::isNotBlank) ?: "none"
        val safeId = if (rawId == "health" || rawId == "service" || rawId == "legacy" ||
            rawId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            safe(rawId)
        } else {
            "opaque"
        }
        val safeLink = safe(link?.takeIf(String::isNotBlank) ?: "unknown")
        val safeStage = safe(stage)
        val safeResult = safe(result)
        val time = synchronized(this) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        }
        val line = "$time id=$safeId link=$safeLink stage=$safeStage result=$safeResult"
        synchronized(lock) {
            val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val lines = if (preferences.getInt(KEY_VERSION, 0) == RING_VERSION) {
                runCatching { JSONArray(preferences.getString(KEY_LINES, "[]")) }.getOrDefault(JSONArray())
            } else JSONArray()
            val next = JSONArray()
            for (index in (lines.length() - MAX_LINES + 1).coerceAtLeast(0) until lines.length()) {
                next.put(lines.optString(index))
            }
            next.put(line)
            preferences.edit(commit = true) {
                putInt(KEY_VERSION, RING_VERSION)
                putString(KEY_LINES, next.toString())
            }
        }
        Log.i(TAG, line)
    }

    fun recordWait(context: Context, id: String?, link: String?, stage: String, result: String) {
        val key = "${id.orEmpty()}|${link.orEmpty()}|$stage|$result"
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val previous = lastRepeatableEvents[key]
            if (previous != null && now - previous < REPEAT_INTERVAL_MS) return
            lastRepeatableEvents[key] = now
            while (lastRepeatableEvents.size > MAX_LINES) {
                lastRepeatableEvents.remove(lastRepeatableEvents.keys.first())
            }
        }
        record(context, id, link, stage, result)
    }

    fun lines(context: Context): List<String> = synchronized(lock) {
        val json = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .takeIf { it.getInt(KEY_VERSION, 0) == RING_VERSION }
            ?.getString(KEY_LINES, "[]")
        val lines = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        (0 until lines.length()).mapNotNull { index -> lines.optString(index).takeIf(String::isNotBlank) }
    }

    fun incomingLink(): String? = BaseLink.currentReceivingLink()?.transport?.name

    fun <T> withWriteContext(id: String, link: String?, action: () -> T): T {
        val previous = writeContext.get()
        writeContext.set(id to link)
        return try {
            action()
        } finally {
            if (previous == null) writeContext.remove() else writeContext.set(previous)
        }
    }

    fun currentWriteContext(): Pair<String, String?>? = writeContext.get()

    private fun safe(value: String): String = value
        .replace(Regex("[^A-Za-z0-9_.:=/-]"), "_")
        .take(160)
}
