/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.plugins.ecosystem

import android.content.Context
import android.os.Build
import android.os.IBinder

/** Shizuku's shell identity changes physical display power without locking the captured session.
 * Framework API compatibility follows the APIs documented by scrcpy's display wrappers:
 * https://github.com/Genymobile/scrcpy/tree/master/server/src/main/java/com/genymobile/scrcpy/wrappers
 */
class DisplayPowerUserService() : IDisplayPower.Stub() {
    constructor(context: Context) : this()
    override fun setScreenOn(on: Boolean): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 35) {
            val type = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val manager = type.getDeclaredMethod("getInstance").invoke(null)
            type.getMethod("requestDisplayPower", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(manager, 0, on) as Boolean
        } else {
            val type = Class.forName("android.view.SurfaceControl")
            val token = if (Build.VERSION.SDK_INT >= 29) {
                val ids = type.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
                type.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType).invoke(null, ids.first()) as IBinder
            } else type.getMethod("getBuiltInDisplay", Int::class.javaPrimitiveType).invoke(null, 0) as IBinder
            type.getMethod("setDisplayPowerMode", IBinder::class.java, Int::class.javaPrimitiveType).invoke(null, token, if (on) 2 else 0)
            true
        }
    }.getOrDefault(false)

    override fun wake(): Boolean = command("input", "keyevent", "KEYCODE_WAKEUP")
    override fun rotate(): Boolean {
        val rotation = runCatching { ProcessBuilder("settings", "get", "system", "user_rotation").start()
            .inputStream.bufferedReader().use { it.readText().trim().toInt() } }.getOrDefault(0)
        return command("settings", "put", "system", "accelerometer_rotation", "0") &&
            command("settings", "put", "system", "user_rotation", ((rotation + 1) % 4).toString())
    }
    override fun launchApp(packageName: String): Boolean {
        if (!packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) return false
        return command("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", packageName)
    }
    private fun command(vararg arguments: String): Boolean = runCatching {
        val process = ProcessBuilder(*arguments).redirectErrorStream(true).start()
        if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); false }
        else process.inputStream.bufferedReader().use { process.exitValue() == 0 && !it.readText().contains("Error:") }
    }.getOrDefault(false)

    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        if (code == 16777114) { setScreenOn(true); android.os.Process.killProcess(android.os.Process.myPid()); return true }
        return super.onTransact(code, data, reply, flags)
    }
}
