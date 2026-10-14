/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui.features

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.ConnectionTransport
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.clipboard.*
import org.kde.kdeconnect.plugins.ecosystem.*
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadActivity
import org.kde.kdeconnect.plugins.receivenotifications.*
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun FeatureContent(device: Device, screen: FeatureDestination, onBack: () -> Unit, onNavigate: (FeatureDestination) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle, device.deviceId) { lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    } }
    when (screen) {
        FeatureDestination.CLIPBOARD -> ClipboardPage(device, now, onBack)
        FeatureDestination.NOTIFICATIONS -> NotificationsPage(device, now, onBack)
        FeatureDestination.CONNECTION -> ConnectionPage(device, now, onBack)
        FeatureDestination.CONTROL -> ControlPage(device, now, onBack)
        FeatureDestination.SCENES -> ScenesPage(device, now, onBack)
        FeatureDestination.MIRROR -> MirrorPage(device, now, onBack)
        FeatureDestination.DESK -> DeskPage(device, now, onBack, onNavigate)
    }
}

@Composable
private fun FeaturePage(title: String, onBack: () -> Unit, deviceName: String? = null, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val tablet = (LocalContext.current as? MainActivity)?.isTwoPaneLayout == true
        Column(Modifier.fillMaxSize().then(if (tablet) Modifier else Modifier.statusBarsPadding()).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = if (title.isEmpty()) 20.dp else 36.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (deviceName != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(FeatureIcons.Back, stringResource(R.string.feature_back)) }
                Text(deviceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            } else if (!compact) Spacer(Modifier.height(48.dp))
            if (title.isNotEmpty()) Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), content = content)
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String, icon: ImageVector, onClick: (() -> Unit)? = null, showIcon: Boolean = true, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
        .padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showIcon) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(icon, null, Modifier.padding(14.dp).size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun Segment(first: String, second: String, selected: Boolean, onSelect: (Boolean) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outline) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            listOf(first, second).forEachIndexed { index, text ->
                Surface(Modifier.weight(1f).clickable { onSelect(index == 0) }, shape = CircleShape,
                    color = if (selected == (index == 0)) MaterialTheme.colorScheme.surface else Color.Transparent) {
                    Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) { Text(text, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) { Text(text.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }

@Composable
private fun Age(time: Long, now: Long): String {
    val seconds = ((now - time).coerceAtLeast(0) / 1000)
    return when { seconds < 60 -> stringResource(R.string.feature_now)
        seconds < 3600 -> stringResource(R.string.feature_minutes, seconds / 60)
        seconds < 86400 -> stringResource(R.string.feature_hours, seconds / 3600)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time)) }
}

@Composable
private fun ClipboardPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ClipboardHistory.load(context) }
    val items by ClipboardHistory.items.collectAsState()
    var settings by remember { mutableStateOf(ClipboardSyncManager.settings(context)) }
    LaunchedEffect(now) { settings = ClipboardSyncManager.settings(context) }
    var selected by remember { mutableStateOf<ClipboardHistoryItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var snippet by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val paused = settings.pausedUntil > now
    FeaturePage(stringResource(R.string.feature_clipboard), onBack, device.name) {
        Group {
            FeatureRow(if (paused) stringResource(R.string.feature_sync_paused) else if (settings.enabled) stringResource(R.string.feature_sync_on) else stringResource(R.string.feature_sync_off),
                if (paused) stringResource(R.string.feature_resume_sync) else stringResource(R.string.feature_pause_hour), FeatureIcons.ContentPaste,
                onClick = {
                    ClipboardSyncManager.updateSettings(context) { it.copy(pausedUntil = if (paused) 0 else System.currentTimeMillis() + 3_600_000) }; settings = ClipboardSyncManager.settings(context)
                }, showIcon = false) {
                FeatureSwitch(settings.enabled, onCheckedChange = { enabled -> ClipboardSyncManager.updateSettings(context) { it.copy(enabled = enabled, pausedUntil = 0) }; settings = ClipboardSyncManager.settings(context) })
            }
        }
        if (settings.enabled && !ClipboardSyncManager.watcherActive) {
            Text(stringResource(R.string.feature_clipboard_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { context.startActivity(Intent(context, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity::class.java).putExtra(org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.EXTRA_SCREEN, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.SCREEN_CLIPBOARD_SYNC)) }) { Text(stringResource(R.string.open_settings)) }
        }
        val pinned = items.filter { it.pinned }
        val recent = items.filterNot { it.pinned }
        if (pinned.isNotEmpty()) {
            Section(stringResource(R.string.feature_pinned))
            Group { pinned.forEachIndexed { index, item ->
                FeatureRow(item.text.lineSequence().first(), stringResource(R.string.feature_kept), FeatureIcons.PushPin, { selected = item })
                if (index < pinned.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            } }
        }
        Section(stringResource(R.string.feature_recent))
        if (recent.isEmpty()) Group { FeatureRow(stringResource(R.string.feature_clipboard_empty), stringResource(R.string.feature_clipboard_empty_detail), FeatureIcons.ContentPaste) }
        else Group { recent.forEachIndexed { index, item ->
            FeatureRow(item.text.lineSequence().first(), stringResource(R.string.feature_from, item.source) + " · " + Age(item.time, now), FeatureIcons.ContentPaste, { selected = item })
            if (index < recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        } }
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.feature_add_snippet)) }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(stringResource(R.string.feature_clipboard)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(item.text)
            TextButton(onClick = { context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Ecosystem", item.text)); selected = null }) { Text(stringResource(R.string.feature_copy)) }
            TextButton(onClick = {
                val plugin = KdeConnect.getInstance().getDevicePlugin<ClipboardPlugin>(device.deviceId)
                if (plugin != null && device.isReachable) { plugin.propagateClipboard(item.text); selected = null }
                else message = context.getString(R.string.feature_disconnected)
            }) { Text(stringResource(R.string.send_clipboard)) }
            TextButton(onClick = { ClipboardHistory.pin(context, item.id); selected = null }) { Text(stringResource(if (item.pinned) R.string.feature_unpin else R.string.feature_pin)) }
            TextButton(onClick = { ClipboardHistory.delete(context, item.id); selected = null }) { Text(stringResource(R.string.feature_delete)) }
        } }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } }) }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text(stringResource(R.string.feature_add_snippet)) },
        text = { OutlinedTextField(snippet, { snippet = it }, label = { Text(stringResource(R.string.feature_text)) }) },
        confirmButton = { TextButton(enabled = snippet.isNotBlank(), onClick = {
            ClipboardHistory.record(context, snippet, context.getString(R.string.feature_this_device))
            ClipboardHistory.items.value.firstOrNull { it.text == snippet && !it.pinned }?.let { ClipboardHistory.pin(context, it.id) }
            snippet = ""; adding = false
        }) { Text(stringResource(R.string.feature_save)) } }, dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun NotificationsPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by RemoteNotifications.items.collectAsState()
    val notifications = all.filter { it.deviceId == device.deviceId }.sortedByDescending { it.time }
    var showAll by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<RemoteNotification?>(null) }
    val plugin = KdeConnect.getInstance().getDevicePlugin<ReceiveNotificationsPlugin>(device.deviceId)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) device.setPluginEnabled(Plugin.getPluginKey(ReceiveNotificationsPlugin::class.java), true)
    }
    FeaturePage(stringResource(R.string.feature_notifications), onBack) {
        Segment(stringResource(R.string.all), stringResource(R.string.feature_digest), showAll) { showAll = it }
        if (plugin == null) Group {
            Text(stringResource(R.string.feature_notifications_setup), Modifier.padding(16.dp))
            Pill(stringResource(R.string.feature_enable_notifications), enabled = device.isReachable && Plugin.getPluginKey(ReceiveNotificationsPlugin::class.java) in device.supportedPlugins) {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else device.setPluginEnabled(Plugin.getPluginKey(ReceiveNotificationsPlugin::class.java), true)
            }
        }
        if (notifications.isEmpty()) Group { FeatureRow(stringResource(R.string.feature_notifications_empty), stringResource(R.string.feature_notifications_empty_detail), FeatureIcons.NotificationsNone) }
        else if (!showAll) Group { notifications.groupBy { it.app }.entries.forEachIndexed { index, (app, items) ->
            FeatureRow(app, stringResource(R.string.feature_notification_count, items.size), FeatureIcons.NotificationsNone, { showAll = true })
            if (index < notifications.groupBy { it.app }.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        } } else {
            notifications.filter { it.replyId != null }.forEach { item ->
                NotificationCard(item, now, device.isReachable, onReply = { plugin?.reply(item.replyId.orEmpty(), it) }, onOpen = { selected = item })
            }
            val plain = notifications.filter { it.replyId == null }
            if (plain.isNotEmpty()) Group { plain.forEachIndexed { index, item ->
                FeatureRow(item.app, listOf(item.title, item.text).filter { it.isNotBlank() }.distinct().joinToString(" · "), FeatureIcons.NotificationsNone,
                    onClick = { selected = item }) { Text(Age(item.time, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (index < plain.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            } }
        }
    }
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.app) },
        text = { Text(listOf(item.title, item.text).filter { it.isNotBlank() }.distinct().joinToString("\n")) },
        confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
        dismissButton = { if (item.clearable) TextButton(enabled = device.isReachable, onClick = { plugin?.dismiss(item.id); selected = null }) { Text(stringResource(R.string.feature_dismiss)) } }) }
}

@Composable
private fun NotificationCard(item: RemoteNotification, now: Long, reachable: Boolean, onReply: (String) -> Unit, onOpen: () -> Unit) {
    var reply by rememberSaveable(item.id) { mutableStateOf("") }
    var sent by remember(item.id) { mutableStateOf(false) }
    Group {
        FeatureRow(item.app, listOf(item.title, item.text).filter { it.isNotBlank() }.distinct().joinToString(" · "), FeatureIcons.NotificationsNone, onClick = onOpen) {
            Text(Age(item.time, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.replyId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.feature_on_way, R.string.feature_call_later).forEach { label ->
                    val text = stringResource(label)
                    FilledTonalButton(onClick = { onReply(text); sent = true }, enabled = reachable, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(text) }
                }
            }
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reply, { reply = it; sent = false }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.feature_reply_here)) }, shape = CircleShape)
                FilledIconButton(onClick = { onReply(reply); reply = ""; sent = true }, enabled = reachable && reply.isNotBlank()) {
                    Icon(FeatureIcons.ArrowUpward, stringResource(R.string.feature_send))
                }
            }
            if (sent) Text(stringResource(R.string.feature_reply_sent), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun ConnectionPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val plugin = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(device.deviceId)
    val state = plugin?.state?.collectAsState()?.value ?: RemoteControlState()
    var auto by remember { mutableStateOf(device.automaticLinkSelection) }
    FeaturePage(stringResource(R.string.feature_connection), onBack, device.name) {
        Group {
            listOf(ConnectionTransport.WIFI, ConnectionTransport.WIFI_DIRECT, ConnectionTransport.HOTSPOT, ConnectionTransport.BLUETOOTH).forEachIndexed { index, link ->
                val active = device.activeTransport == link
                val subtitle = when {
                    active && state.latency != null -> stringResource(R.string.feature_delay, state.latency)
                    active -> stringResource(R.string.feature_connected)
                    link in device.availableTransports -> stringResource(R.string.feature_backup_ready)
                    else -> stringResource(R.string.feature_no_link)
                }
                FeatureRow(stringResource(link.labelRes), subtitle, if (link == ConnectionTransport.BLUETOOTH) FeatureIcons.Bluetooth else FeatureIcons.Wifi,
                    onClick = { if (!auto && link in device.availableTransports) device.selectTransport(link) }) {
                    if (active) Text(stringResource(R.string.feature_active), color = Color(0xFF30D158), fontWeight = FontWeight.SemiBold)
                }
                if (index < 3) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            if (ConnectionTransport.TAILSCALE in device.availableTransports) FeatureRow("Tailscale", stringResource(R.string.feature_connected), FeatureIcons.Public)
        }
        Group { FeatureRow(stringResource(R.string.feature_auto_switch), stringResource(R.string.feature_best_link), FeatureIcons.Sync, showIcon = false) {
            FeatureSwitch(auto, { auto = it; device.automaticLinkSelection = it })
        } }
        Pill(stringResource(R.string.feature_connection_test), device.isReachable && plugin != null) { plugin?.request("ping") }
        if (state.latency != null) Text(stringResource(R.string.feature_test_result, state.latency), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plugin == null) Text(stringResource(R.string.feature_peer_update), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.message.isNotBlank()) Text(state.message)
        TextButton(onClick = { BackgroundService.ForceRefreshConnections(context) }) { Text(stringResource(R.string.refresh)) }
        @Suppress("UNUSED_VARIABLE") val refreshAt = now
    }
}

@Composable
private fun ActionGrid(actions: List<Triple<String, String, ImageVector>>, onClick: (Int) -> Unit) {
    actions.chunked(2).forEachIndexed { row, entries ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { entries.forEachIndexed { column, entry ->
            Surface(Modifier.weight(1f).clickable { onClick(row * 2 + column) }, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp).heightIn(min = 104.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Icon(entry.third, null, Modifier.padding(12.dp).size(24.dp)) }
                    Spacer(Modifier.height(4.dp))
                    Text(entry.first, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.second, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }; if (entries.size == 1) Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ControlPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val plugin = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(device.deviceId)
    val state = plugin?.state?.collectAsState()?.value ?: RemoteControlState()
    val mirrors by MirrorSession.views.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    val battery = KdeConnect.getInstance().getDevicePlugin<BatteryPlugin>(device.deviceId)?.remoteBatteryInfo?.currentCharge
    LaunchedEffect(plugin) { plugin?.request("status") }
    FeaturePage(device.name, onBack) {
        Group { FeatureRow(if (battery == null) stringResource(R.string.feature_battery_unknown) else stringResource(R.string.feature_battery_percent, battery),
            device.connectivityType ?: stringResource(R.string.feature_disconnected), FeatureIcons.BatteryFull) {
            Text(stringResource(if (device.isReachable) R.string.feature_connected else R.string.feature_disconnected),
                color = if (device.isReachable) Color(0xFF30D158) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        } }
        val actions = listOf(
            Triple(stringResource(R.string.feature_ring_phone), stringResource(R.string.feature_even_silent), FeatureIcons.NotificationsNone),
            Triple(stringResource(R.string.feature_lock_screen), stringResource(R.string.feature_wake_or_lock), Icons.Filled.Lock),
            Triple(stringResource(R.string.feature_screenshot), stringResource(R.string.feature_saved_here), FeatureIcons.CameraAlt),
            Triple(stringResource(R.string.feature_volume), stringResource(R.string.feature_change_remote), FeatureIcons.VolumeUp),
            Triple(stringResource(R.string.feature_phone_apps), stringResource(R.string.feature_open_phone), FeatureIcons.Apps),
            Triple(stringResource(R.string.feature_last_seen), if (device.isReachable) stringResource(R.string.feature_now) else if (device.lastSeenAt > 0) Age(device.lastSeenAt, now) else stringResource(R.string.feature_no_previous_connection), FeatureIcons.PushPin))
        ActionGrid(actions) { action ->
            if (!device.isReachable && action != 5) { dialog = "offline"; return@ActionGrid }
            when (action) {
                0 -> if (device.canSendPacketType(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)) device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)) else dialog = "unsupported"
                1 -> if (plugin != null) dialog = "lock" else dialog = "unsupported"
                2 -> if (plugin != null) { MirrorSession.expect(device.deviceId); plugin.request("screenshot"); dialog = "screenshot" } else dialog = "unsupported"
                3 -> if (plugin != null) { plugin.request("status"); dialog = "volume" } else dialog = "unsupported"
                4 -> if (plugin != null) { plugin.request("apps"); dialog = "apps" } else dialog = "unsupported"
                5 -> dialog = "seen"
            }
        }
        if (state.message.isNotBlank()) Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    dialog?.let { kind -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(when(kind) {
        "lock" -> R.string.feature_lock_screen; "volume" -> R.string.feature_volume; "apps" -> R.string.feature_phone_apps; "screenshot" -> R.string.feature_screenshot; "seen" -> R.string.feature_last_seen; else -> R.string.feature_controls })) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { when(kind) {
            "lock" -> {
                Pill(stringResource(R.string.feature_wake)) { plugin?.request("wake"); dialog = null }
                Pill(stringResource(R.string.feature_lock_screen)) { plugin?.request("lock"); dialog = null }
            }
            "volume" -> Slider(state.volume.toFloat(), { plugin?.request("volume", it.toInt().toString()) }, valueRange = 0f..state.maximumVolume.coerceAtLeast(1).toFloat())
            "apps" -> if (state.apps.isEmpty()) Text(stringResource(R.string.feature_loading)) else state.apps.forEach { app -> TextButton(onClick = { plugin?.request("launch", app.packageName); dialog = null }) { Text(app.label) } }
            "screenshot" -> mirrors[device.deviceId]?.screenshot?.let { Image(it.asImageBitmap(), stringResource(R.string.feature_screenshot), Modifier.fillMaxWidth()) }
                ?: Text(stringResource(R.string.feature_loading))
            "seen" -> {
                Text(if (device.isReachable) stringResource(R.string.feature_connected) else if (device.lastSeenAt > 0)
                    DateFormat.getDateTimeInstance().format(Date(device.lastSeenAt)) else stringResource(R.string.feature_no_previous_connection))
                TextButton(onClick = { context.startActivity(Intent(context, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity::class.java)
                    .putExtra(org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.EXTRA_SCREEN, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.SCREEN_DISCONNECT_LOG)) }) {
                    Text(stringResource(R.string.feature_connection_history))
                }
            }
            "offline" -> Text(stringResource(R.string.feature_disconnected))
            else -> Text(stringResource(R.string.feature_peer_update))
        }; if (state.message.isNotEmpty()) Text(state.message) } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) } }) }
    @Suppress("UNUSED_VARIABLE") val refreshAt = now
}

@Composable
private fun ScenesPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val prefs = remember(revision, now) { SceneSupport.prefs(context) }
    val peer = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(device.deviceId)
    val remoteState = peer?.state?.collectAsState()?.value
    val active = prefs.getString("active", "")
    FeaturePage(stringResource(R.string.feature_scenes), onBack, compact = true) {
        val scenes = listOf("work", "movie", "sleep", "travel")
        val actions = listOf(Triple(stringResource(R.string.feature_work), stringResource(R.string.feature_do_not_disturb), FeatureIcons.WorkOutline),
            Triple(stringResource(R.string.feature_movie), stringResource(R.string.feature_dark_low), FeatureIcons.Movie),
            Triple(stringResource(R.string.feature_sleep), stringResource(R.string.feature_quiet_dim), FeatureIcons.Bedtime),
            Triple(stringResource(R.string.feature_travel), stringResource(R.string.feature_hotspot_link), FeatureIcons.NearMe))
        actions.chunked(2).forEachIndexed { row, entries -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { entries.forEachIndexed { col, action ->
            val scene = scenes[row * 2 + col]
            val chosen = active == scene
            Surface(Modifier.weight(1f).clickable { message = SceneSupport.apply(context, scene, device.deviceId); revision++ }, shape = RoundedCornerShape(26.dp),
                color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                contentColor = if (chosen) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.padding(18.dp).heightIn(min = 104.dp)) {
                    Icon(action.third, null, Modifier.size(40.dp).padding(8.dp)); Spacer(Modifier.height(16.dp))
                    Text(action.first, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(action.second, fontSize = 14.sp)
                }
            }
        } } }
        Section(stringResource(R.string.feature_automations))
        Group {
            FeatureRow(stringResource(R.string.feature_sleep_11), stringResource(R.string.feature_every_day), FeatureIcons.Bedtime) { FeatureSwitch(prefs.getBoolean("sleep", false), { message = SceneSupport.automate(context, "sleep", it); revision++ }) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            FeatureRow(stringResource(R.string.feature_work_desk), stringResource(R.string.feature_near_tablet), FeatureIcons.WorkOutline) { FeatureSwitch(prefs.getBoolean("desk", false), { message = SceneSupport.automate(context, "desk", it); revision++ }) }
        }
        if (remoteState?.message?.isNotBlank() == true) Text(remoteState.message)
        if (message.isNotBlank()) {
            Text(message)
            TextButton(onClick = {
                val manager = context.getSystemService<NotificationManager>()
                val intent = when {
                    manager?.isNotificationPolicyAccessGranted != true -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    !Settings.System.canWrite(context) -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, ("package:" + context.packageName).toUri())
                    Build.VERSION.SDK_INT >= 31 -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, ("package:" + context.packageName).toUri())
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                context.startActivity(intent)
            }) { Text(stringResource(R.string.open_settings)) }
        }
        @Suppress("UNUSED_VARIABLE") val preferenceRevision = revision
    }
}

@Composable
private fun MirrorPage(device: Device, now: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val plugin = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(device.deviceId)
    val state = plugin?.state?.collectAsState()?.value ?: RemoteControlState()
    val views by MirrorSession.views.collectAsState()
    val view = views[device.deviceId]
    var requested by rememberSaveable { mutableStateOf(false) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    var audio by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val live = view?.frame != null && now - view.lastFrameAt < 5000
    LaunchedEffect(live) { if (live) plugin?.request("status") }
    LaunchedEffect(audio, live) {
        MirrorSession.playAudio(device.deviceId, audio && live)
        if (live) plugin?.request("audio", audio.toString())
    }
    val tablet = (context as? MainActivity)?.isTwoPaneLayout == true
    FeaturePage(if (tablet) "" else stringResource(R.string.feature_mirror), onBack, if (tablet) null else device.name, compact = tablet) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            @Composable fun preview(modifier: Modifier) {
                Surface(modifier, shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (live) stringResource(R.string.feature_mirroring, device.name) else if (requested) stringResource(R.string.feature_approve_phone) else stringResource(R.string.feature_mirror_ready),
                            Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val bitmap = view?.frame
                        if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.feature_phone_screen),
                            Modifier.heightIn(max = 560.dp).aspectRatio(bitmap.width.toFloat() / bitmap.height, matchHeightConstraintsFirst = true)
                                .pointerInput(device.deviceId, live) { detectTapGestures { position ->
                                    if (live) plugin?.request("touch", "${position.x / size.width},${position.y / size.height},${position.x / size.width},${position.y / size.height}")
                                } }.pointerInput(device.deviceId, live) {
                                    var start = androidx.compose.ui.geometry.Offset.Zero
                                    var end = start
                                    detectDragGestures(onDragStart = { start = it; end = it }, onDragEnd = {
                                        if (live) plugin?.request("touch", "${start.x / size.width},${start.y / size.height},${end.x / size.width},${end.y / size.height}")
                                    }, onDrag = { change, delta -> change.consume(); end += delta })
                                }, contentScale = ContentScale.Fit)
                        else PhoneSilhouette()
                    }
                }
            }
            @Composable fun controls(modifier: Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (wide) Text(stringResource(R.string.feature_mirror), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("back" to FeatureIcons.Back, "home" to FeatureIcons.RadioButtonUnchecked,
                            "recent" to FeatureIcons.CheckBoxOutlineBlank, "rotate" to FeatureIcons.Sync).forEach { (action, icon) ->
                            FilledTonalIconButton(onClick = { plugin?.request(action) }, enabled = live && (action == "rotate" || state.accessibility), modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(icon, stringResource(when(action) { "back" -> R.string.feature_back; "home" -> R.string.feature_home; "recent" -> R.string.feature_recent_apps; else -> R.string.feature_rotate }))
                            }
                        }
                    }
                    Group {
                        FeatureRow(stringResource(R.string.feature_phone_audio), stringResource(if (state.audioAvailable) R.string.feature_audio_here else R.string.feature_audio_permission), FeatureIcons.VolumeUp) {
                            FeatureSwitch(audio, { audio = it }, enabled = live && state.audioAvailable)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        FeatureRow(stringResource(R.string.feature_screen_off), stringResource(if (state.screenPowerAvailable) R.string.feature_screen_off_detail else R.string.feature_screen_power_permission), Icons.Filled.Lock) {
                            FeatureSwitch(state.screenOff, { plugin?.request("screenOff", it.toString()) }, enabled = live && state.screenPowerAvailable)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        FeatureRow(stringResource(R.string.feature_tablet_keyboard), stringResource(R.string.feature_type_phone), FeatureIcons.Keyboard) { FeatureSwitch(keyboard, { keyboard = it }, enabled = live && state.accessibility) }
                    }
                    if (keyboard) {
                        OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.feature_type_phone)) }, modifier = Modifier.fillMaxWidth())
                        Pill(stringResource(R.string.feature_send), text.isNotEmpty() && live) { plugin?.request("text", text); text = "" }
                    }
                    Pill(stringResource(if (requested || live) R.string.feature_stop_mirror else R.string.feature_start_mirror), plugin != null && device.isReachable) {
                        if (requested || live) { plugin?.request("stopMirror"); MirrorSession.stop(context, device.deviceId); requested = false }
                        else { MirrorSession.expect(device.deviceId); plugin?.request("mirror"); requested = true }
                    }
                    if (!state.accessibility) Text(stringResource(R.string.feature_mirror_touch_setup), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (plugin == null) Text(stringResource(R.string.feature_peer_update))
                    if (state.message.isNotBlank()) Text(state.message)
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { preview(Modifier.weight(1.5f)); controls(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { preview(Modifier.fillMaxWidth()); controls(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun DeskPage(device: Device, now: Long, onBack: () -> Unit, onNavigate: (FeatureDestination) -> Unit) {
    val context = LocalContext.current
    val all by RemoteNotifications.items.collectAsState()
    val notifications = all.filter { it.deviceId == device.deviceId }.take(3)
    val media = KdeConnect.getInstance().getDevicePlugin<MprisPlugin>(device.deviceId)?.playingPlayer
    val battery = KdeConnect.getInstance().getDevicePlugin<BatteryPlugin>(device.deviceId)?.remoteBatteryInfo?.currentCharge
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val tablet = (context as? MainActivity)?.isTwoPaneLayout == true
            val cardHeight = if (tablet) (LocalConfiguration.current.screenHeightDp - 240).coerceAtLeast(300).dp else 300.dp
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), fontSize = if (tablet) 112.sp else 76.sp, fontWeight = FontWeight.Bold)
                    Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Text(device.name + " · " + stringResource(if (device.isReachable) R.string.feature_connected else R.string.feature_disconnected),
                        Modifier.padding(12.dp), color = if (device.isReachable) Color(0xFF30D158) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onBack) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 700.dp
                @Composable fun mediaCard(modifier: Modifier) { Surface(modifier.clickable { KdeConnect.getInstance().getDevicePlugin<MprisPlugin>(device.deviceId)?.getUiButtons()?.firstOrNull()?.onClick?.invoke(context as android.app.Activity) }, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(20.dp).height(cardHeight)) {
                        val art = media?.getAlbumArt()
                        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                        else Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Icon(FeatureIcons.MusicNote, null, Modifier.size(40.dp)) } }
                        Spacer(Modifier.weight(1f))
                        Text(media?.title ?: stringResource(R.string.feature_nothing_playing), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(media?.artist.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (media != null) IconButton(onClick = { media.sendPlayPause() }) { Icon(FeatureIcons.Pause, stringResource(R.string.feature_play_pause)) }
                    }
                } }
                @Composable fun notificationCard(modifier: Modifier) { Surface(modifier.clickable { onNavigate(FeatureDestination.NOTIFICATIONS) }, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp).height(cardHeight)) {
                        if (notifications.isEmpty()) FeatureRow(stringResource(R.string.feature_notifications_empty), "", FeatureIcons.NotificationsNone)
                        notifications.forEachIndexed { index, item -> FeatureRow(item.app, item.text, FeatureIcons.NotificationsNone)
                            if (index < notifications.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
                    }
                } }
                @Composable fun batteryCard(modifier: Modifier) { Surface(modifier, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(20.dp).height(cardHeight)) {
                        Text(battery?.let { "$it%" } ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.feature_device_battery, device.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Pill(stringResource(R.string.feature_ring_phone), device.isReachable && device.canSendPacketType(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)) { device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)) }
                    }
                } }
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { mediaCard(Modifier.weight(1f)); notificationCard(Modifier.weight(1.2f)); batteryCard(Modifier.weight(1f)) }
                else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { mediaCard(Modifier.fillMaxWidth()); notificationCard(Modifier.fillMaxWidth()); batteryCard(Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun PhoneSilhouette() {
    val frame = MaterialTheme.colorScheme.onSurface
    val screen = MaterialTheme.colorScheme.surfaceVariant
    val panel = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(480.dp)) {
        val height = size.height * .92f
        val width = height * .48f
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val radius = androidx.compose.ui.geometry.CornerRadius(width * .25f)
        drawRoundRect(frame, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height), radius)
        val border = width * .045f
        drawRoundRect(panel, androidx.compose.ui.geometry.Offset(left + border, top + border), androidx.compose.ui.geometry.Size(width - border * 2, height - border * 2), radius)
        drawRoundRect(screen, androidx.compose.ui.geometry.Offset(left + width * .14f, top + height * .07f), androidx.compose.ui.geometry.Size(width * .72f, height * .86f), androidx.compose.ui.geometry.CornerRadius(width * .16f))
        drawRoundRect(frame, androidx.compose.ui.geometry.Offset(left + width * .36f, top + height * .11f), androidx.compose.ui.geometry.Size(width * .28f, height * .037f), androidx.compose.ui.geometry.CornerRadius(height * .02f))
        repeat(2) { index -> drawRoundRect(panel, androidx.compose.ui.geometry.Offset(left + width * .23f, top + height * (.24f + index * .23f)), androidx.compose.ui.geometry.Size(width * .54f, height * .17f), androidx.compose.ui.geometry.CornerRadius(width * .12f)) }
    }
}

@Composable
private fun FeatureSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.onSurface,
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedBorderColor = Color.Transparent))
}
