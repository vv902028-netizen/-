package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class ScreenLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() == this) {
            instanceRef = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception needed, service is used solely for global lock action
    }

    override fun onInterrupt() {}

    fun performLock(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    companion object {
        private var instanceRef: WeakReference<ScreenLockAccessibilityService>? = null

        fun get(): ScreenLockAccessibilityService? = instanceRef?.get()

        fun isConnected(): Boolean = get() != null
    }
}
