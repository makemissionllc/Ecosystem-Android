/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.kde.kdeconnect_tp.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DisconnectLogScreen(entries: List<DisconnectLogEntry>, onBack: () -> Unit, onRefresh: () -> Unit) {
    FoundationPage(R.string.disconnect_log_title, onBack) {
        Text(stringResource(R.string.disconnect_log_explanation), style = MaterialTheme.typography.bodyLarge)
        if (entries.isEmpty()) {
            FoundationCard {
                Text(stringResource(R.string.disconnect_log_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            entries.forEach { entry ->
                FoundationCard {
                    val name = if (entry.component) stringResource(
                        if (entry.deviceName == "clipboard") R.string.clipboard_sync_title else R.string.keep_alive_connection_title
                    ) else entry.deviceName
                    val message = when {
                        entry.linkFrom != null && entry.linkTo != null -> stringResource(R.string.connection_link_switch)
                        entry.component -> stringResource(R.string.disconnect_log_component_stopped, name)
                        entry.connected -> stringResource(R.string.disconnect_log_reconnected, name)
                        else -> stringResource(R.string.disconnect_log_disconnected, name)
                    }
                    StatusLine(entry.connected, message)
                    if (entry.linkFrom != null && entry.linkTo != null) {
                        Text("${entry.linkFrom} → ${entry.linkTo}", style = MaterialTheme.typography.bodyMedium)
                    }
                    entry.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(dateFormat.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!entry.connected) {
                        Text(stringResource(entry.cause.text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Button(onClick = onRefresh) { Text(stringResource(R.string.background_check_again)) }
    }
}
