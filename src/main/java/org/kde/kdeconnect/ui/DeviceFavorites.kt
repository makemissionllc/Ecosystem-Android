/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Context
import androidx.core.content.edit

/** Small shared store used by the device list and the navigation drawer. */
internal object DeviceFavorites {
    private const val PREFERENCES = "device_favorites"
    private const val FAVORITE_DEVICE_IDS = "favorite_device_ids"

    fun getIds(context: Context): Set<String> = preferences(context)
        .getStringSet(FAVORITE_DEVICE_IDS, emptySet())
        .orEmpty()
        .toSet()

    fun toggle(context: Context, deviceId: String): Set<String> {
        val updated = getIds(context).toMutableSet().apply {
            if (!add(deviceId)) remove(deviceId)
        }
        preferences(context).edit { putStringSet(FAVORITE_DEVICE_IDS, updated) }
        return updated
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
