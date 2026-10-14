/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui.features

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import org.kde.kdeconnect_tp.R

internal object FeatureIcons {
    val Back: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_back)
    val ContentPaste: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_clipboard)
    val PushPin: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_pin)
    val NotificationsNone: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_bell)
    val ArrowUpward: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_up)
    val Bluetooth: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_bluetooth)
    val Wifi: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_wifi)
    val Public: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_world)
    val Sync: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_sync)
    val BatteryFull: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_battery)
    val CameraAlt: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_camera)
    val VolumeUp: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_volume)
    val Apps: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_apps)
    val WorkOutline: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_work)
    val Movie: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_movie)
    val Bedtime: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_sleep)
    val NearMe: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_travel)
    val RadioButtonUnchecked: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_home)
    val CheckBoxOutlineBlank: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_recent)
    val Keyboard: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_keyboard)
    val MusicNote: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_music)
    val Pause: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.feature_pause)
}
