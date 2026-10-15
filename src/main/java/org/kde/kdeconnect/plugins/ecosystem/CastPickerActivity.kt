/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R

/** The tile routes tablets straight to their paired phone and shows a two-way choice on phones. */
class CastPickerActivity : AppCompatActivity() {
    private var pads: List<Device> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pads = KdeConnect.getInstance().devices.values.filter { it.isPaired && it.deviceType == DeviceType.TABLET }
            .sortedBy { it.name.lowercase() }
        if (DeviceHelper.deviceType == DeviceType.TABLET) {
            val phone = KdeConnect.getInstance().devices.values.firstOrNull { it.isPaired && it.deviceType == DeviceType.PHONE }
                ?: KdeConnect.getInstance().devices.values.firstOrNull { it.isPaired }
            if (phone == null) {
                Toast.makeText(this, getString(R.string.cast_no_device), Toast.LENGTH_LONG).show()
                finish()
                return
            }
            openViewer(phone.deviceId)
            return
        }

        if (pads.isEmpty()) {
            Toast.makeText(this, getString(R.string.cast_no_device), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            KdeTheme(this) {
                CastPickerScreen(
                    pads = pads,
                    onShowOnPad = { showPhoneOnPad(it) },
                    onOpenPadHere = { openViewer(it) },
                )
            }
        }
    }

    private fun showPhoneOnPad(deviceId: String) {
        val device = KdeConnect.getInstance().getDevice(deviceId)
        val plugin = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(deviceId)
        if (device?.isPaired != true || !device.isReachable || plugin == null) {
            Toast.makeText(this, getString(R.string.cast_screen_unavailable), Toast.LENGTH_LONG).show()
            return
        }
        plugin.request("castWindow", MirrorWindowActivity.DIRECTION_RECEIVE)
        ContextCompat.startActivity(this, Intent(this, MirrorConsentActivity::class.java).putExtra("deviceId", deviceId), null)
        finish()
    }

    private fun openViewer(deviceId: String) {
        val device = KdeConnect.getInstance().getDevice(deviceId)
        if (device?.isPaired != true || KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(deviceId) == null) {
            openLegacyMirror(deviceId)
            return
        }
        MirrorSession.expect(deviceId)
        CastWindowSupport.tryFreeform(this, deviceId, MirrorWindowActivity.DIRECTION_VIEW) { result ->
            if (isFinishing || isDestroyed) return@tryFreeform
            if (result.launched) {
                CastWindowSupport.rememberFreeform(this)
                finish()
            } else {
                CastWindowSupport.rememberFallback(this, result.reason)
                runCatching {
                    CastWindowSupport.launchActivity(this, deviceId, MirrorWindowActivity.DIRECTION_VIEW)
                    finish()
                }.onFailure { openLegacyMirror(deviceId) }
            }
        }
    }

    private fun openLegacyMirror(deviceId: String) {
        CastWindowSupport.launchLegacyMirror(this, deviceId)
        finish()
    }
}

@Composable
private fun CastPickerScreen(pads: List<Device>, onShowOnPad: (String) -> Unit, onOpenPadHere: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.cast_picker_title), style = MaterialTheme.typography.headlineSmall)
            pads.forEach { pad ->
                Button(onClick = { onShowOnPad(pad.deviceId) }, enabled = pad.isReachable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cast_show_on, pad.name))
                }
                Button(onClick = { onOpenPadHere(pad.deviceId) }, enabled = pad.isReachable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cast_open_here, pad.name))
                }
                if (!pad.isReachable) Text(stringResource(R.string.cast_screen_unavailable), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
