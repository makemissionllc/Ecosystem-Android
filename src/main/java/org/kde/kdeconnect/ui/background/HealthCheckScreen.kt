/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncSettings
import org.kde.kdeconnect_tp.R

internal data class HealthCheckState(
    val shizuku: ShizukuState,
    val batteryUnrestricted: Boolean,
    val connectedDevice: Boolean,
    val notificationAccess: Boolean,
    val oemName: String,
    val oemInstructions: String,
    val oemAutostartConfirmed: Boolean,
    val clipboardSync: ClipboardSyncSettings = ClipboardSyncSettings(false, false, true, true),
    val clipboardWatcherActive: Boolean = false,
    val clipboardAutomaticAvailable: Boolean = false,
    val keepAlive: KeepAliveReport = KeepAliveReport(),
    val keepAliveBusy: Boolean = false,
    val foregroundRunning: Boolean = false,
    val oemBatteryConfirmed: Boolean = false,
    val oemRecentsConfirmed: Boolean = false,
    val companionReady: Boolean = false,
    val clipboardDiagnostics: List<String> = emptyList(),
    val castStatus: String = "",
)

@Composable
internal fun HealthCheckScreen(
    state: HealthCheckState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenOemSettings: () -> Unit,
    onOpenOemBattery: () -> Unit,
    onOpenClipboardSettings: () -> Unit,
    onOpenShizukuSetup: () -> Unit,
    onApplyKeepAlive: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenCompanion: () -> Unit,
    onConfirmOem: (String, Boolean) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onFreshConnection: () -> Unit,
    onAddFreshConnectionTile: () -> Unit,
    onAddCastTile: () -> Unit,
) {
    val freshConnection by FreshConnectionSupport.status.collectAsState()
    FoundationPage(R.string.health_title, onBack) {
        Text(stringResource(R.string.health_summary), style = MaterialTheme.typography.bodyLarge)
        FoundationCard {
            StatusLine(state.shizuku.running && state.shizuku.authorized,
                stringResource(if (state.shizuku.running && state.shizuku.authorized) R.string.health_shizuku_ok else R.string.health_shizuku_missing))
            if (!state.shizuku.running || !state.shizuku.authorized) {
                Text(stringResource(R.string.health_shizuku_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.shizuku_setup_title)) }
            }
        }
        FoundationCard {
            Text(stringResource(R.string.keep_alive_title), style = MaterialTheme.typography.titleLarge)
            StatusLine(state.foregroundRunning, stringResource(R.string.keep_alive_connection_title))
            StatusLine(state.batteryUnrestricted, stringResource(R.string.keep_alive_battery))
            StatusLine(state.keepAlive.backgroundAllowed && !state.keepAlive.error,
                stringResource(R.string.keep_alive_background))
            StatusLine(state.keepAlive.standbyProtected && !state.keepAlive.error,
                stringResource(R.string.keep_alive_standby))
            if (state.keepAlive.checkedAt > 0) {
                Text(stringResource(R.string.keep_alive_last_checked, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(state.keepAlive.checkedAt))), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.keep_alive_explanation), style = MaterialTheme.typography.bodyMedium)
            if (state.keepAlive.error) Text(stringResource(R.string.keep_alive_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = onApplyKeepAlive, enabled = state.shizuku.authorized && !state.keepAliveBusy) {
                Text(stringResource(if (state.keepAliveBusy) R.string.keep_alive_applying else R.string.keep_alive_apply))
            }
            TextButton(onClick = onOpenNotificationSettings) { Text(stringResource(R.string.keep_alive_notification)) }
            Text(stringResource(R.string.keep_alive_notification_hint), style = MaterialTheme.typography.bodySmall)
        }
        FoundationCard {
            StatusLine(state.batteryUnrestricted,
                stringResource(if (state.batteryUnrestricted) R.string.health_battery_ok else R.string.health_battery_missing))
            if (!state.batteryUnrestricted) {
                Text(stringResource(R.string.health_battery_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenBattery) { Text(stringResource(R.string.health_fix_battery)) }
            }
        }
        FoundationCard {
            StatusLine(state.companionReady, stringResource(R.string.companion_title))
            Text(stringResource(R.string.companion_explanation), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenCompanion) { Text(stringResource(R.string.companion_setup)) }
        }
        FoundationCard {
            StatusLine(state.connectedDevice,
                stringResource(if (state.connectedDevice) R.string.health_connection_ok else R.string.health_connection_missing))
            if (!state.connectedDevice) {
                Text(stringResource(R.string.health_connection_next), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenConnection) { Text(stringResource(R.string.health_open_devices)) }
            }
        }
        FoundationCard {
            Text(stringResource(R.string.fresh_connection), style = MaterialTheme.typography.titleLarge)
            Text(freshConnection.message.ifBlank { stringResource(R.string.fresh_connection_health_hint) },
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFreshConnection, enabled = !freshConnection.resetting) {
                Text(stringResource(if (freshConnection.resetting) R.string.fresh_connection_resetting else R.string.fresh_connection))
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Button(onClick = onAddFreshConnectionTile) { Text(stringResource(R.string.fresh_connection_add_tile)) }
            } else {
                Text(stringResource(R.string.qs_tile_manual_setup), style = MaterialTheme.typography.bodySmall)
            }
        }
        FoundationCard {
            Text(stringResource(R.string.cast_tile), style = MaterialTheme.typography.titleLarge)
            Text(state.castStatus.ifBlank { stringResource(R.string.cast_setup_hint) }, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.cast_setup_hint), style = MaterialTheme.typography.bodySmall)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Button(onClick = onAddCastTile) { Text(stringResource(R.string.cast_tile_setup)) }
            } else {
                Text(stringResource(R.string.cast_tile_manual_setup), style = MaterialTheme.typography.bodySmall)
            }
        }
        FoundationCard {
            StatusLine(state.notificationAccess,
                stringResource(if (state.notificationAccess) R.string.health_notifications_ok else R.string.health_notifications_missing))
            if (!state.notificationAccess) {
                Text(stringResource(R.string.health_notifications_next), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenNotifications) { Text(stringResource(R.string.health_fix_notifications)) }
            }
        }
        FoundationCard {
            if (!state.clipboardSync.enabled) {
                Text(stringResource(R.string.health_clipboard_off), style = MaterialTheme.typography.bodyLarge)
            } else {
                StatusLine(state.clipboardAutomaticAvailable,
                    stringResource(when {
                        state.clipboardAutomaticAvailable -> R.string.health_clipboard_running
                        state.clipboardSync.trustedWifiOnly && state.clipboardWatcherActive -> R.string.clipboard_sync_wifi_fallback
                        else -> R.string.health_clipboard_manual
                    }))
                if (!state.clipboardWatcherActive) {
                    Text(stringResource(R.string.clipboard_sync_shizuku_hint), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenShizukuSetup) { Text(stringResource(R.string.shizuku_setup_title)) }
                } else if (state.clipboardSync.trustedWifiOnly && !state.clipboardAutomaticAvailable) {
                    Text(stringResource(R.string.clipboard_sync_trusted_wifi_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onOpenClipboardSettings) { Text(stringResource(R.string.clipboard_sync_title)) }
        }
        FoundationCard {
            Text(stringResource(R.string.health_clipboard_diagnostics_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.health_clipboard_diagnostics_summary), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefreshDiagnostics) { Text(stringResource(R.string.health_clipboard_diagnostics_refresh)) }
            Text(
                text = state.clipboardDiagnostics.takeLast(200).joinToString("\n").ifBlank {
                    stringResource(R.string.health_clipboard_diagnostics_empty)
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        FoundationCard {
            Text(stringResource(R.string.keep_alive_oem_title, state.oemName), style = MaterialTheme.typography.titleLarge)
            Text(state.oemInstructions, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.keep_alive_oem_manual), style = MaterialTheme.typography.bodySmall)
            OemConfirmation(state.oemAutostartConfirmed, R.string.keep_alive_oem_autostart) { onConfirmOem("oem_autostart_confirmed", it) }
            Button(onClick = onOpenOemSettings) { Text(stringResource(R.string.health_open_autostart)) }
            OemConfirmation(state.oemBatteryConfirmed, R.string.keep_alive_oem_battery) { onConfirmOem("oem_battery_confirmed", it) }
            TextButton(onClick = onOpenOemBattery) { Text(stringResource(R.string.health_open_oem_battery)) }
            OemConfirmation(state.oemRecentsConfirmed, R.string.keep_alive_oem_recents) { onConfirmOem("oem_recents_confirmed", it) }
            Text(stringResource(R.string.keep_alive_recents_steps), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onCheck) { Text(stringResource(R.string.background_check_again)) }
    }
}

@Composable
private fun OemConfirmation(confirmed: Boolean, label: Int, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (confirmed) R.string.keep_alive_confirmed else R.string.keep_alive_not_confirmed),
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = confirmed, onCheckedChange = onChanged)
    }
}
