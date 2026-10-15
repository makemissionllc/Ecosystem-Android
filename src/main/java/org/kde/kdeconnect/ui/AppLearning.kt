/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

internal enum class TourAction { DEVICES, DASHBOARD, SHARE, CLIPBOARD, NOTIFICATIONS, CONNECTION, CONTROL, MIRROR, SCENES, SETTINGS, HEALTH }

private data class TourStep(
    val title: Int,
    val description: Int,
    val icon: Int,
    val action: TourAction,
)

private val tourSteps = listOf(
    TourStep(R.string.tour_devices_title, R.string.tour_devices_body, R.drawable.feature_home, TourAction.DEVICES),
    TourStep(R.string.tour_dashboard_title, R.string.tour_dashboard_body, R.drawable.feature_sync, TourAction.DASHBOARD),
    TourStep(R.string.tour_share_title, R.string.tour_share_body, R.drawable.share_plugin_action_24dp, TourAction.SHARE),
    TourStep(R.string.tour_clipboard_title, R.string.tour_clipboard_body, R.drawable.feature_clipboard, TourAction.CLIPBOARD),
    TourStep(R.string.tour_notifications_title, R.string.tour_notifications_body, R.drawable.feature_bell, TourAction.NOTIFICATIONS),
    TourStep(R.string.tour_remote_title, R.string.tour_remote_body, R.drawable.feature_music, TourAction.DASHBOARD),
    TourStep(R.string.tour_connections_title, R.string.tour_connections_body, R.drawable.feature_wifi, TourAction.CONNECTION),
    TourStep(R.string.tour_control_title, R.string.tour_control_body, R.drawable.feature_cast, TourAction.MIRROR),
    TourStep(R.string.tour_scenes_title, R.string.tour_scenes_body, R.drawable.feature_work, TourAction.SCENES),
    TourStep(R.string.tour_settings_title, R.string.tour_settings_body, R.drawable.ic_appearance_24, TourAction.SETTINGS),
    TourStep(R.string.tour_health_title, R.string.tour_health_body, R.drawable.feature_battery, TourAction.HEALTH),
)

@Composable
internal fun WhatsNewDialog(onDismiss: () -> Unit, onStartTour: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.whats_new_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LearningHighlight(R.drawable.feature_sync, R.string.whats_new_resume_title, R.string.whats_new_resume_body)
                LearningHighlight(R.drawable.feature_cast, R.string.whats_new_cast_title, R.string.whats_new_cast_body)
                LearningHighlight(R.drawable.feature_wifi, R.string.whats_new_connection_title, R.string.whats_new_connection_body)
                LearningHighlight(R.drawable.feature_home, R.string.whats_new_favorites_title, R.string.whats_new_favorites_body)
                LearningHighlight(R.drawable.ic_appearance_24, R.string.whats_new_settings_title, R.string.whats_new_settings_body)
                LearningHighlight(R.drawable.feature_clipboard, R.string.whats_new_everyday_title, R.string.whats_new_everyday_body)
                LearningHighlight(R.drawable.feature_battery, R.string.whats_new_efficiency_title, R.string.whats_new_efficiency_body)
                LearningHighlight(R.drawable.feature_sync, R.string.whats_new_navigation_title, R.string.whats_new_navigation_body)
                Text(
                    stringResource(R.string.whats_new_tour_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onStartTour) { Text(stringResource(R.string.settings_app_tour)) } },
    )
}

@Composable
private fun LearningHighlight(icon: Int, title: Int, description: Int) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun AppTourDialog(
    hasPairedDevice: Boolean,
    onDismiss: () -> Unit,
    onTry: (TourAction) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val step = tourSteps[page]
    val progress by animateFloatAsState(
        targetValue = (page + 1).toFloat() / tourSteps.size,
        animationSpec = tween(240),
        label = "tour progress",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tour_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.tour_counter, page + 1, tourSteps.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).height(5.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tutorial step",
                ) { currentPage ->
                    val current = tourSteps[currentPage]
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Image(
                                painter = painterResource(current.icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                        Text(stringResource(current.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(current.description), style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
                    }
                }
                Button(
                    onClick = { onTry(if (step.action in deviceActions && !hasPairedDevice) TourAction.DEVICES else step.action) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        stringResource(if (step.action in deviceActions && !hasPairedDevice) R.string.tour_choose_device else R.string.tour_try_this),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            if (page == tourSteps.lastIndex) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.tour_finish)) }
            } else {
                TextButton(onClick = { page++ }) { Text(stringResource(R.string.tour_next)) }
            }
        },
        dismissButton = {
            if (page > 0) OutlinedButton(onClick = { page-- }) { Text(stringResource(R.string.tour_previous)) }
        },
    )
}

private val deviceActions = setOf(
    TourAction.DASHBOARD,
    TourAction.CLIPBOARD,
    TourAction.NOTIFICATIONS,
    TourAction.CONNECTION,
    TourAction.CONTROL,
    TourAction.MIRROR,
    TourAction.SCENES,
)
