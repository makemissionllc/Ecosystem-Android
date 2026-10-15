/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import org.kde.kdeconnect_tp.R

@RequiresApi(Build.VERSION_CODES.N)
class CastTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.cast_tile)
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(R.string.cast_tile_ready)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = android.content.Intent(this, CastPickerActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        TileServiceCompat.startActivityAndCollapse(this, PendingIntentActivityWrapper(
            this, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT, true,
        ))
    }
}
