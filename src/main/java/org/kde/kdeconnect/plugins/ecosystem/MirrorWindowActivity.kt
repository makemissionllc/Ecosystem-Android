/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.features.FeatureIcons
import org.kde.kdeconnect_tp.R

class MirrorWindowActivity : AppCompatActivity() {
    private var peerId: String? = null
    private var stopped = false
    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val castHeartbeat = object : Runnable {
        override fun run() {
            peerId?.let { KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(it)?.request("castWindow", "heartbeat") }
            heartbeatHandler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        peerId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val direction = intent.getStringExtra(EXTRA_DIRECTION) ?: DIRECTION_VIEW
        val id = peerId
        if (id == null || !DeviceInfo.isValidDeviceId(id) || direction !in setOf(DIRECTION_VIEW, DIRECTION_RECEIVE)) {
            finish()
            return
        }
        val device = KdeConnect.getInstance().getDevice(id)
        if (device?.isPaired != true) {
            finish()
            return
        }
        if (KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(id) == null) {
            CastWindowSupport.launchLegacyMirror(this, id)
            finish()
            return
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        MirrorSession.expect(id)
        if (direction == DIRECTION_VIEW) {
            KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(id)?.request("mirror")
        }
        setContent {
            KdeTheme(this) {
                MirrorWindowScreen(
                    deviceId = id,
                    deviceName = device.name,
                    onAction = ::sendAction,
                    onClose = ::stopAndClose,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val id = peerId ?: return
        KdeConnect.getInstance().addDeviceListChangedCallback("MirrorWindow-$id") {
            runOnUiThread {
                if (KdeConnect.getInstance().getDevice(id)?.isReachable != true) {
                    MirrorSession.disconnected(id)
                    finish()
                }
            }
        }
    }

    override fun onStop() {
        peerId?.let { KdeConnect.getInstance().removeDeviceListChangedCallback("MirrorWindow-$it") }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        heartbeatHandler.removeCallbacks(castHeartbeat)
        castHeartbeat.run()
    }

    override fun onPause() {
        heartbeatHandler.removeCallbacks(castHeartbeat)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        CastWindowSupport.saveWindowBounds(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) CastWindowSupport.saveWindowBounds(this)
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) closeSession()
        super.onDestroy()
    }

    private fun sendAction(action: String, value: String = "") {
        val id = peerId ?: return
        KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(id)?.request(action, value)
    }

    private fun stopAndClose() {
        closeSession()
        finish()
    }

    private fun closeSession() {
        if (stopped) return
        stopped = true
        peerId?.let { id ->
            KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(id)?.request("stopMirror")
            MirrorSession.stop(this, id)
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_DIRECTION = "direction"
        const val DIRECTION_VIEW = "view"
        const val DIRECTION_RECEIVE = "receive"
    }
}

@Composable
private fun MirrorWindowScreen(deviceId: String, deviceName: String, onAction: (String, String) -> Unit, onClose: () -> Unit) {
    val views by MirrorSession.views.collectAsState()
    val view = views[deviceId]
    val plugin = KdeConnect.getInstance().getDevicePlugin<EcosystemPlugin>(deviceId)
    val remoteState by (plugin?.state?.collectAsState() ?: remember { mutableStateOf(RemoteControlState()) })
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var keyboard by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var hadFrame by remember { mutableStateOf(false) }
    var closedAfterStreamLoss by remember { mutableStateOf(false) }
    LaunchedEffect(deviceId) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    LaunchedEffect(view?.lastFrameAt) {
        val lastFrameAt = view?.lastFrameAt ?: return@LaunchedEffect
        hadFrame = true
        delay(12_000)
        val latest = views[deviceId]?.lastFrameAt ?: 0
        if (hadFrame && !closedAfterStreamLoss && System.currentTimeMillis() - latest > 12_000) {
            closedAfterStreamLoss = true
            onClose()
        }
    }
    val live = view?.frame != null && now - view.lastFrameAt < 5000

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(deviceName, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
                MirrorAction("back", R.string.cast_back, FeatureIcons.Back, live, onAction)
                MirrorAction("home", R.string.cast_home, FeatureIcons.RadioButtonUnchecked, live, onAction)
                MirrorAction("recent", R.string.cast_recents, FeatureIcons.CheckBoxOutlineBlank, live, onAction)
                MirrorAction("rotate", R.string.cast_rotate, FeatureIcons.Sync, live, onAction)
                IconButton(onClick = { keyboard = !keyboard }) { Icon(FeatureIcons.Keyboard, stringResource(R.string.cast_keyboard)) }
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.cast_stop)) }
            }
            Text(
                if (live) stringResource(R.string.cast_screen_live, deviceName) else stringResource(R.string.cast_screen_waiting),
                Modifier.padding(start = 6.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val bitmap = view?.frame
                if (bitmap == null) {
                    Text(stringResource(R.string.cast_screen_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.feature_phone_screen),
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .pointerInput(deviceId, live) {
                                detectTapGestures { position ->
                                    if (live) {
                                        val x = position.x / size.width
                                        val y = position.y / size.height
                                        onAction("touch", "$x,$y,$x,$y")
                                    }
                                }
                            }
                            .pointerInput(deviceId, live) {
                                var start = androidx.compose.ui.geometry.Offset.Zero
                                var end = start
                                detectDragGestures(onDragStart = { start = it; end = it }, onDragEnd = {
                                    if (live) onAction("touch", "${start.x / size.width},${start.y / size.height},${end.x / size.width},${end.y / size.height}")
                                }, onDrag = { change, delta -> change.consume(); end += delta })
                            }
                            .pointerInput(deviceId, live) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll && live) {
                                            val change = event.changes.firstOrNull() ?: continue
                                            val x = change.position.x / size.width
                                            val y = change.position.y / size.height
                                            val endY = (y + change.scrollDelta.y * 2f / size.height).coerceIn(0f, 1f)
                                            onAction("touch", "$x,$y,$x,$endY")
                                        }
                                    }
                                }
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (keyboard) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.cast_keyboard)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (text.isNotBlank() && live) { onAction("text", text); text = "" }
                        }),
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(onClick = {
                        if (text.isNotBlank() && live) { onAction("text", text); text = "" }
                    }, enabled = live && text.isNotBlank()) { Text(stringResource(R.string.cast_send_text)) }
                }
            }
            if (!live && remoteState.message.isNotBlank()) {
                Text(remoteState.message, Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MirrorAction(action: String, label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, onAction: (String, String) -> Unit) {
    FilledTonalIconButton(onClick = { onAction(action, "") }, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(icon, stringResource(label))
    }
}
