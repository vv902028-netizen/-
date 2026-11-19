package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.PreferencesManager
import com.example.service.ScreenControlService
import com.example.service.ScreenLockManager

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val settings = PreferencesManager.getInstance(context).settingsFlow.value
        val isLockCapable = ScreenLockManager.isDeviceAdminActive(context) ||
            ScreenLockManager.isAccessibilityActive()

        if (settings.isMasterServiceEnabled && isLockCapable) {
            try {
                ScreenControlService.start(context)
            } catch (_: Exception) {
                // System may restrict FGS launch from boot on some OEM ROMs
            }
        }
    }
}
