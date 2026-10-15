/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Opens the Health screen when a user long-presses an Ecosystem Quick Settings tile. */
class EcosystemTilePreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BackgroundFeaturesActivity::class.java)
            .putExtra(BackgroundFeaturesActivity.EXTRA_SCREEN, BackgroundFeaturesActivity.SCREEN_HEALTH))
        finish()
    }
}
