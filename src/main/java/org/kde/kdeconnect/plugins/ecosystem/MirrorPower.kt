/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.ui.background.ShizukuSupport
import rikka.shizuku.Shizuku

internal object MirrorPower {
    @Volatile var screenOff = false
        private set
    private var remote: IDisplayPower? = null
    private var pending: ((IDisplayPower?) -> Unit)? = null
    private var binding = false
    private val handler = Handler(Looper.getMainLooper())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            remote = IDisplayPower.Stub.asInterface(binder)
            pending?.invoke(remote); pending = null
        }
        override fun onServiceDisconnected(name: ComponentName?) { remote = null; binding = false; pending?.invoke(null); pending = null }
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }
    fun available(context: Context) = ShizukuSupport.state(context).authorized
    fun set(context: Context, on: Boolean, callback: (Boolean) -> Unit) {
        withController(context) { controller -> ThreadHelper.execute {
            val success = runCatching { controller?.setScreenOn(on) == true }.getOrDefault(false)
            if (success) screenOff = !on
            handler.post { callback(success) }
        } }
    }
    fun execute(context: Context, action: String, value: String = "", callback: (Boolean) -> Unit) {
        withController(context) { controller -> ThreadHelper.execute {
            val success = runCatching { when (action) {
                "wake" -> controller?.wake() == true
                "rotate" -> controller?.rotate() == true
                "launch" -> controller?.launchApp(value) == true
                else -> false
            } }.getOrDefault(false)
            handler.post { callback(success) }
        } }
    }
    private fun withController(context: Context, action: (IDisplayPower?) -> Unit) {
        handler.post {
            if (!available(context)) { action(null); return@post }
            if (remote != null) { action(remote); return@post }
            if (binding) { action(null); return@post }
            pending = action; binding = true
            try {
                Shizuku.bindUserService(Shizuku.UserServiceArgs(ComponentName(context, DisplayPowerUserService::class.java))
                    .tag("ecosystem_display_power").version(2).daemon(false).processNameSuffix("display_power"), connection)
                handler.postDelayed({ if (binding) { binding = false; pending?.invoke(null); pending = null } }, 8_000)
            } catch (e: Exception) { binding = false; pending = null; action(null) }
        }
    }
}
