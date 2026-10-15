/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.kde.kdeconnect_tp.R

class FreshConnectionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        FreshConnectionSupport.pulse(this)
        FreshConnectionSupport.request(this)
        updateTileState()
    }

    private fun updateTileState() {
        FreshConnectionSupport.initialize(this)
        val state = FreshConnectionSupport.status.value
        qsTile?.apply {
            label = getString(R.string.fresh_connection)
            this.state = if (state.resetting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when {
                    state.resetting -> getString(R.string.fresh_connection_resetting)
                    state.message.isNotBlank() -> state.message
                    else -> getString(R.string.fresh_connection_ready)
                }
            }
            updateTile()
        }
    }
}
