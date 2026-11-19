package com.example.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.data.LockMethod
import com.example.data.PreferencesManager
import com.example.receiver.ScreenAdminReceiver
import com.example.util.HapticHelper

object ScreenLockManager {

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, ScreenAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isAdminActive(getAdminComponent(context)) == true
    }

    fun isAccessibilityActive(): Boolean {
        return ScreenLockAccessibilityService.isConnected()
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    fun lockScreen(context: Context): Boolean {
        val prefs = PreferencesManager.getInstance(context)
        val settings = prefs.settingsFlow.value

        if (settings.isHapticsEnabled) {
            HapticHelper.performHeavyClick(context, true)
        }

        val a11yConnected = ScreenLockAccessibilityService.isConnected()
        val adminActive = isDeviceAdminActive(context)

        return when (settings.lockMethod) {
            LockMethod.ACCESSIBILITY -> {
                if (a11yConnected) {
                    ScreenLockAccessibilityService.get()?.performLock() == true
                } else {
                    false
                }
            }
            LockMethod.DEVICE_ADMIN -> {
                if (adminActive) {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    dpm?.lockNow()
                    true
                } else {
                    false
                }
            }
            LockMethod.AUTO -> {
                if (a11yConnected) {
                    val locked = ScreenLockAccessibilityService.get()?.performLock() == true
                    if (locked) return true
                }
                if (adminActive) {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    dpm?.lockNow()
                    return true
                }
                false
            }
        }
    }

    fun wakeScreen(context: Context) {
        val prefs = PreferencesManager.getInstance(context)
        val settings = prefs.settingsFlow.value
        if (settings.isHapticsEnabled) {
            HapticHelper.performClick(context, true)
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (pm.isInteractive) return

        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "smartscreen:wakelock"
        )
        try {
            wakeLock.acquire(4000L) // 4 seconds max
        } catch (_: Exception) {}
    }

    fun openDeviceAdminSettings(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Разрешите приложению блокировать экран без нажатия физической кнопки"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }
}
