/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import org.kde.kdeconnect.extensions.safeDrawingBottomPadding
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeBodyLargeText
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeBodySmallText
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

private val WideLayoutBreakpoint = 840.dp

@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onLocalNetworkPermissionClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onFavoriteClick: (String) -> Unit = {},
    showOverflow: Boolean = false,
    onMoreClick: () -> Unit = {},
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val hapticFeedback = LocalHapticFeedback.current
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val hasFavorites = remember(uiState.connected, uiState.available, uiState.remembered) {
        (uiState.connected + uiState.available + uiState.remembered).any { it.isFavorite }
    }
    val favoriteFilterActive = favoritesOnly && hasFavorites
    LaunchedEffect(hasFavorites) {
        if (!hasFavorites) favoritesOnly = false
    }
    var previousConnectedIds by remember { mutableStateOf(uiState.connected.map { it.id }.toSet()) }
    var previousAvailableIds by remember { mutableStateOf(uiState.available.map { it.id }.toSet()) }
    LaunchedEffect(uiState.connected.map { it.id }.toSet(), uiState.available.map { it.id }.toSet()) {
        val connectedIds = uiState.connected.map { it.id }.toSet()
        val newlyConnected = connectedIds - previousConnectedIds
        if (newlyConnected.any { it in previousAvailableIds }) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        previousConnectedIds = connectedIds
        previousAvailableIds = uiState.available.map { it.id }.toSet()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= WideLayoutBreakpoint) {
                    WidePairingContent(
                        lazyListState = lazyListState,
                        uiState = uiState,
                        favoritesOnly = favoriteFilterActive,
                        hasFavorites = hasFavorites,
                        onFavoritesOnlyChange = { favoritesOnly = it },
                        onClick = onClick,
                        onFavoriteClick = onFavoriteClick,
                        onWifiSettingsClick = onWifiSettingsClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onLocalNetworkPermissionClick = onLocalNetworkPermissionClick,
                        showOverflow = showOverflow,
                        onMoreClick = onMoreClick,
                    )
                } else {
                    CompactPairingContent(
                        lazyListState = lazyListState,
                        uiState = uiState,
                        favoritesOnly = favoriteFilterActive,
                        hasFavorites = hasFavorites,
                        onFavoritesOnlyChange = { favoritesOnly = it },
                        onClick = onClick,
                        onFavoriteClick = onFavoriteClick,
                        onWifiSettingsClick = onWifiSettingsClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onLocalNetworkPermissionClick = onLocalNetworkPermissionClick,
                        showOverflow = showOverflow,
                        onMoreClick = onMoreClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun WidePairingContent(
    lazyListState: LazyListState,
    uiState: PairingUiState,
    favoritesOnly: Boolean,
    hasFavorites: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit,
    showOverflow: Boolean,
    onMoreClick: () -> Unit,
) {
    val statusBarPadding = if (LocalContext.current.resources.configuration.screenWidthDp < 600) {
        with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    } else 0.dp
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1440.dp)
                .padding(start = 24.dp, top = statusBarPadding + 20.dp, end = 24.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DiscoveryHeader(showOverflow, onMoreClick)
                RadarCard(modifier = Modifier.fillMaxWidth().weight(1f))
                PairingExplanations(
                    uiState = uiState,
                    onWifiSettingsClick = onWifiSettingsClick,
                    onNotificationSettingsClick = onNotificationSettingsClick,
                    onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1.1f).fillMaxSize(),
                contentPadding = safeDrawingBottomPadding(),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceSections(
                    uiState = uiState,
                    favoritesOnly = favoritesOnly,
                    hasFavorites = hasFavorites,
                    onFavoritesOnlyChange = onFavoritesOnlyChange,
                    onClick = onClick,
                    onFavoriteClick = onFavoriteClick,
                )
            }
        }
    }
}

@Composable
private fun CompactPairingContent(
    lazyListState: LazyListState,
    uiState: PairingUiState,
    favoritesOnly: Boolean,
    hasFavorites: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit,
    showOverflow: Boolean,
    onMoreClick: () -> Unit,
) {
    val statusBarPadding = if (LocalContext.current.resources.configuration.screenWidthDp < 600) {
        with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    } else 0.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = statusBarPadding, end = 16.dp, bottom = 80.dp),
        contentPadding = safeDrawingBottomPadding(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DiscoveryHeader(showOverflow, onMoreClick) }
        item { RadarCard(modifier = Modifier.fillMaxWidth().height(220.dp)) }
        item {
            PairingExplanations(
                uiState = uiState,
                onWifiSettingsClick = onWifiSettingsClick,
                onNotificationSettingsClick = onNotificationSettingsClick,
                onLocalNetworkPermissionClick = onLocalNetworkPermissionClick
            )
        }
        DeviceSections(
            uiState = uiState,
            favoritesOnly = favoritesOnly,
            hasFavorites = hasFavorites,
            onFavoritesOnlyChange = onFavoritesOnlyChange,
            onClick = onClick,
            onFavoriteClick = onFavoriteClick,
        )
    }
}

@Composable
private fun DiscoveryHeader(showOverflow: Boolean, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.add_a_device), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.discovery_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showOverflow) {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.extra_options))
            }
        }
    }
}

@Composable
private fun RadarCard(modifier: Modifier = Modifier) {
    // Keep the discovery illustration still at rest: an always-running Canvas sweep
    // was redrawing the whole card every frame even when no one was interacting.
    val sweep = 0f
    val pulse = 0.82f
    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2) - 14.dp.toPx()
                for (ring in 1..3) {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.12f + (ring * 0.025f)),
                        radius = radius * ring / 3f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                drawLine(
                    color = lineColor.copy(alpha = 0.42f),
                    start = center,
                    end = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(sweep * 6.283185f) * radius,
                        center.y + kotlin.math.sin(sweep * 6.283185f) * radius
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = lineColor.copy(alpha = pulse),
                    radius = 5.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(center.x + radius * 0.58f, center.y - radius * 0.24f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.searching), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.discovery_devices_appear_here),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun LazyListScope.DeviceSections(
    uiState: PairingUiState,
    favoritesOnly: Boolean,
    hasFavorites: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
) {
    val connected = if (favoritesOnly) uiState.connected.filter { it.isFavorite } else uiState.connected
    val available = if (favoritesOnly) uiState.available.filter { it.isFavorite } else uiState.available
    val remembered = if (favoritesOnly) uiState.remembered.filter { it.isFavorite } else uiState.remembered

    item {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.devices),
                modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 2.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (hasFavorites) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                    label = { Text(stringResource(R.string.device_favorites_filter)) },
                )
            }
        }
    }

    if (!favoritesOnly || connected.isNotEmpty()) {
        item { DeviceSectionHeader(stringResource(R.string.category_connected_devices)) }
        if (connected.isEmpty()) {
            item { EmptyPlaceholder() }
        } else {
            items(connected, key = { it.id }) { device ->
                DeviceCard(device, onClick, onFavoriteClick, modifier = deviceItemAnimation())
            }
        }
    }
    if (available.isNotEmpty()) {
        item { DeviceSectionHeader(stringResource(R.string.category_not_paired_devices)) }
        items(available, key = { it.id }) { device ->
            DeviceCard(device, onClick, onFavoriteClick, modifier = deviceItemAnimation())
        }
    }
    if (remembered.isNotEmpty()) {
        item { DeviceSectionHeader(stringResource(R.string.category_remembered_devices)) }
        items(remembered, key = { it.id }) { device ->
            DeviceCard(device, onClick, onFavoriteClick, modifier = deviceItemAnimation())
        }
    }
}

private fun LazyItemScope.deviceItemAnimation(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 150),
    placementSpec = tween(durationMillis = 180),
    fadeOutSpec = tween(durationMillis = 120),
)

@Composable
private fun DeviceSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun DeviceCard(
    device: DeviceUiModel,
    onClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 80.dp),
        onClick = { onClick(device.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (device.icon > 0) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Image(
                        imageVector = ImageVector.vectorResource(device.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (device.summaryRes > 0) {
                    Text(
                        text = stringResource(device.summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (device.isPaired) {
                Text(
                    text = stringResource(if (device.isReachable) R.string.connected else R.string.offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Surface(
                    modifier = Modifier.defaultMinSize(minWidth = 80.dp, minHeight = 48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = stringResource(R.string.pair), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            val starScale by animateFloatAsState(
                targetValue = if (device.isFavorite) 1.12f else 1f,
                animationSpec = spring(),
                label = "favorite device scale",
            )
            IconButton(onClick = { onFavoriteClick(device.id) }) {
                val description = stringResource(
                    if (device.isFavorite) R.string.device_favorite_remove else R.string.device_favorite_add,
                )
                val tint = if (device.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                val modifier = Modifier.size(22.dp).graphicsLayer {
                    scaleX = starScale
                    scaleY = starScale
                }
                if (device.isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = description, tint = tint, modifier = modifier)
                } else {
                    Icon(painterResource(R.drawable.ic_star_outline_24), contentDescription = description,
                        tint = tint, modifier = modifier)
                }
            }
        }
    }
}

@Composable
private fun PairingExplanations(
    uiState: PairingUiState,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onLocalNetworkPermissionClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!uiState.hasLocalNetworkPermission) {
            PairingExplanationRow(stringResource(R.string.pairing_missing_local_network_permission), R.drawable.ic_warning, onLocalNetworkPermissionClick)
        }
        if (!uiState.hasNotificationsPermission) {
            PairingExplanationRow(stringResource(R.string.no_notifications), R.drawable.ic_warning, onNotificationSettingsClick)
        }
        if (!uiState.hasLocalNetworkPermission || !uiState.hasNotificationsPermission) return

        if (uiState.hasDuplicateNames) DuplicateNamesWarning()
        val someDevicesReachable = uiState.available.isNotEmpty() || uiState.connected.isNotEmpty()
        if (someDevicesReachable || uiState.isWifiAvailable) {
            if (uiState.isTrustedNetwork) {
                PairingExplanationRow(stringResource(R.string.pairing_description))
            } else {
                PairingExplanationRow(stringResource(R.string.on_non_trusted_message), R.drawable.ic_warning)
            }
        } else {
            PairingExplanationRow(stringResource(R.string.no_wifi), R.drawable.ic_wifi, onWifiSettingsClick)
        }
    }
}

@Composable
fun PairingExplanationRow(text: String, icon: Int? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.width(10.dp))
        }
        KdeBodyMediumText(text = text, onClick = { onClick?.invoke() })
    }
}

@Composable
fun DuplicateNamesWarning() {
    PairingExplanationRow(stringResource(R.string.pairing_duplicate_names), R.drawable.ic_warning)
}

@Composable
private fun EmptyPlaceholder() {
    KdeBodySmallText(
        text = stringResource(R.string.device_list_empty),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        textAlign = TextAlign.Center
    )
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    KdeTheme(context = LocalContext.current) {
        PairingScreen(
            uiState = PairingUiState(
                isWifiAvailable = true,
                hasNotificationsPermission = true,
                hasLocalNetworkPermission = true,
                isTrustedNetwork = true,
                hasDuplicateNames = false,
                connected = emptyList(),
                available = listOf(
                    DeviceUiModel("pad", R.drawable.ic_device_tablet_32dp, "Pad 8 Pro", 0, true, false),
                    DeviceUiModel("phone", R.drawable.ic_device_phone_32dp, "OnePlus 15", 0, true, false)
                ),
                remembered = emptyList(),
                isRefreshing = false
            ),
            onClick = {}
        )
    }
}
