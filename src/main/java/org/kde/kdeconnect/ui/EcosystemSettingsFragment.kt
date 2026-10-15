/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncManager
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.ui.about.AboutFragment
import org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity
import org.kde.kdeconnect.ui.background.KeepAliveSupport
import org.kde.kdeconnect.ui.background.ShizukuSupport
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R

class EcosystemSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(requireContext()) {
                    EcosystemSettingsScreen(
                        onOpenAdvanced = {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.container, SettingsFragment())
                                .commit()
                        },
                        onOpenAbout = {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.container, AboutFragment.newInstance())
                                .commit()
                        },
                        onTryTour = ::tryTourAction,
                    )
                }
            }
        }

    private fun tryTourAction(action: TourAction) {
        val mainActivity = activity as? MainActivity ?: return
        val context = context ?: return
        val selectedDevice = MainActivity.getSelectedDeviceId(context)
            ?.let(KdeConnect.getInstance()::getDevice)
            ?.takeIf { it.isPaired }
        when (action) {
            TourAction.DEVICES -> mainActivity.onDeviceSelected(null)
            TourAction.DASHBOARD -> mainActivity.onDeviceSelected(selectedDevice?.deviceId)
            TourAction.SHARE -> mainActivity.openShareScreen(selectedDevice?.deviceId)
            TourAction.CLIPBOARD -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.CLIPBOARD)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.NOTIFICATIONS -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.NOTIFICATIONS)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.CONNECTION -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.CONNECTION)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.CONTROL -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.CONTROL)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.MIRROR -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.MIRROR)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.SCENES -> selectedDevice?.let {
                mainActivity.openFeatureScreen(it.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.SCENES)
            } ?: mainActivity.onDeviceSelected(null)
            TourAction.SETTINGS -> mainActivity.openSettings()
            TourAction.HEALTH -> startActivity(
                Intent(requireContext(), BackgroundFeaturesActivity::class.java)
                    .putExtra(BackgroundFeaturesActivity.EXTRA_SCREEN, BackgroundFeaturesActivity.SCREEN_HEALTH),
            )
        }
    }
}

@Composable
private fun EcosystemSettingsScreen(
    onOpenAdvanced: () -> Unit,
    onOpenAbout: () -> Unit,
    onTryTour: (TourAction) -> Unit,
) {
    val context = LocalContext.current
    val statusBarInset = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val backgroundPreferences = remember(context) { context.getSharedPreferences("ecosystem_background", android.content.Context.MODE_PRIVATE) }
    var autoConnectEnabled by remember(context) {
        mutableStateOf(preferences.getBoolean(SettingsFragment.KEY_AUTO_CONNECT, true))
    }
    var keepRunningEnabled by remember(context) {
        mutableStateOf(backgroundPreferences.getBoolean("keep_running_enabled", false))
    }
    val clipboardSettings = remember(context) { mutableStateOf(ClipboardSyncManager.settings(context)) }
    val selectedDevice = remember(context) {
        MainActivity.getSelectedDeviceId(context)?.let(KdeConnect.getInstance()::getDevice)
    }
    val notificationsPluginKey = remember { Plugin.getPluginKey(NotificationsPlugin::class.java) }
    var notificationsEnabled by remember(selectedDevice) {
        mutableStateOf(selectedDevice?.isPluginEnabled(notificationsPluginKey) == true)
    }
    var deviceName by remember(context) { mutableStateOf(DeviceHelper.getDeviceName(context)) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showAppTour by remember { mutableStateOf(false) }
    var whatsNewSeen by remember(context) {
        mutableStateOf(preferences.getString(KEY_WHATS_NEW_SEEN_VERSION, null) == org.kde.kdeconnect_tp.BuildConfig.VERSION_NAME)
    }
    val appearanceScope = rememberCoroutineScope()

    val deviceGroup: @Composable () -> Unit = {
        SettingsGroup {
            SettingsRow(
                icon = R.drawable.ic_device_phone_32dp,
                title = stringResource(R.string.settings_device_name),
                subtitle = deviceName,
                onClick = { showDeviceNameDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = R.drawable.ic_baseline_content_paste_24,
                title = stringResource(R.string.settings_clipboard_sync),
                subtitle = stringResource(R.string.settings_clipboard_sync_summary),
                checked = clipboardSettings.value.enabled,
                onCheckedChange = { enabled ->
                    ClipboardSyncManager.updateSettings(context) { it.copy(enabled = enabled) }
                    clipboardSettings.value = clipboardSettings.value.copy(enabled = enabled)
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = R.drawable.ic_notifications_outline_24,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_summary),
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    selectedDevice?.setPluginEnabled(notificationsPluginKey, enabled)
                    notificationsEnabled = enabled
                },
            )
        }
    }
    val connectionGroup: @Composable () -> Unit = {
        SettingsGroup {
            SettingsRow(
                icon = R.drawable.ic_device_desktop_32dp,
                title = stringResource(R.string.settings_auto_connect),
                subtitle = stringResource(R.string.settings_auto_connect_summary),
                checked = autoConnectEnabled,
                onCheckedChange = { enabled ->
                    preferences.edit { putBoolean(SettingsFragment.KEY_AUTO_CONNECT, enabled) }
                    autoConnectEnabled = enabled
                    BackgroundService.instance?.refreshAutoConnectSetting()
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = R.drawable.ic_device_phone_32dp,
                title = stringResource(R.string.settings_keep_running),
                subtitle = stringResource(R.string.settings_keep_running_summary),
                checked = keepRunningEnabled,
                onCheckedChange = { enabled ->
                    backgroundPreferences.edit { putBoolean("keep_running_enabled", enabled) }
                    keepRunningEnabled = enabled
                    if (enabled) {
                        KeepAliveSupport.refresh(apply = true)
                        if (!ShizukuSupport.state(context).authorized) {
                            context.startActivity(Intent(context, BackgroundFeaturesActivity::class.java)
                                .putExtra(BackgroundFeaturesActivity.EXTRA_SCREEN, BackgroundFeaturesActivity.SCREEN_HEALTH))
                        }
                    }
                },
            )
        }
    }
    val appGroup: @Composable () -> Unit = {
        SettingsGroup {
            SettingsRow(
                icon = R.drawable.ic_appearance_24,
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_summary),
                value = appearanceLabel(preferences.getString(SettingsFragment.KEY_APP_THEME, ThemeUtil.DEFAULT_MODE)),
                onClick = {
                    appearanceScope.launch {
                        delay(120)
                        showAppearanceDialog = true
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = R.drawable.ic_document_24,
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.settings_version, org.kde.kdeconnect_tp.BuildConfig.VERSION_NAME),
                onClick = onOpenAbout,
            )
        }
    }
    val learningGroup: @Composable () -> Unit = {
        SettingsGroup {
            SettingsRow(
                icon = R.drawable.ic_document_24,
                title = stringResource(R.string.settings_whats_new),
                subtitle = stringResource(R.string.settings_whats_new_summary),
                value = if (whatsNewSeen) null else stringResource(R.string.settings_whats_new_badge),
                onClick = {
                    preferences.edit { putString(KEY_WHATS_NEW_SEEN_VERSION, org.kde.kdeconnect_tp.BuildConfig.VERSION_NAME) }
                    whatsNewSeen = true
                    showWhatsNew = true
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = R.drawable.feature_sync,
                title = stringResource(R.string.settings_app_tour),
                subtitle = stringResource(R.string.settings_app_tour_summary),
                onClick = { showAppTour = true },
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wideLayout = maxWidth >= 600.dp
            Column(
                modifier = Modifier.fillMaxSize()
                    .widthIn(max = 1100.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = statusBarInset + 20.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                if (wideLayout) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) { deviceGroup() }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            connectionGroup()
                            appGroup()
                            learningGroup()
                        }
                    }
                } else {
                    deviceGroup()
                    connectionGroup()
                    appGroup()
                    learningGroup()
                }
                Spacer(Modifier.size(120.dp))
                TextButton(onClick = onOpenAdvanced, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.settings_advanced))
                }
            }
        }
    }

    if (showDeviceNameDialog) {
        androidx.compose.runtime.key(deviceName) {
            DeviceNameDialog(
                currentName = deviceName,
                onDismiss = { showDeviceNameDialog = false },
                onSave = { updatedName ->
                    preferences.edit { putString(DeviceHelper.KEY_DEVICE_NAME_PREFERENCE, updatedName) }
                    deviceName = updatedName
                    showDeviceNameDialog = false
                },
            )
        }
    }
    if (showAppearanceDialog) {
        val values = stringArrayResource(R.array.theme_list_values)
        val current = preferences.getString(SettingsFragment.KEY_APP_THEME, ThemeUtil.DEFAULT_MODE) ?: ThemeUtil.DEFAULT_MODE
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text(stringResource(R.string.theme_dialog_title)) },
            text = {
                Column {
                    values.indices.forEach { index ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val value = values.getOrNull(index) ?: ThemeUtil.DEFAULT_MODE
                                preferences.edit { putString(SettingsFragment.KEY_APP_THEME, value) }
                                ThemeUtil.applyTheme(value)
                                showAppearanceDialog = false
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = values.getOrNull(index) == current, onClick = {
                                val value = values.getOrNull(index) ?: ThemeUtil.DEFAULT_MODE
                                preferences.edit { putString(SettingsFragment.KEY_APP_THEME, value) }
                                ThemeUtil.applyTheme(value)
                                showAppearanceDialog = false
                            })
                            Text(appearanceLabel(values[index]), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearanceDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showWhatsNew) {
        WhatsNewDialog(
            onDismiss = { showWhatsNew = false },
            onStartTour = { showWhatsNew = false; showAppTour = true },
        )
    }
    if (showAppTour) {
        AppTourDialog(
            hasPairedDevice = selectedDevice?.isPaired == true,
            onDismiss = { showAppTour = false },
            onTry = { action ->
                showAppTour = false
                onTryTour(action)
            },
        )
    }
}

private const val KEY_WHATS_NEW_SEEN_VERSION = "ecosystem_whats_new_seen_version"

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.62f))
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconVector: ImageVector? = null,
    checked: Boolean? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    checked != null && onCheckedChange != null -> Modifier.clickable { onCheckedChange(!checked) }
                    else -> Modifier
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            when {
                iconVector != null -> Icon(iconVector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp))
                icon != null -> Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            checked != null && onCheckedChange != null -> Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.onBackground,
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedBorderColor = MaterialTheme.colorScheme.onBackground,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            value != null -> Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun DeviceNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_rename_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { typed -> name = DeviceHelper.filterInvalidCharactersFromDeviceName(typed).take(DeviceHelper.MAX_DEVICE_NAME_LENGTH) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.device_rename_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun appearanceLabel(value: String?): String = when (value) {
    ThemeUtil.LIGHT_MODE -> stringResource(R.string.settings_theme_light)
    ThemeUtil.DARK_MODE -> stringResource(R.string.settings_theme_dark)
    else -> stringResource(R.string.settings_theme_auto)
}
