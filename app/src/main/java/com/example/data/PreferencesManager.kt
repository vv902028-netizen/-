package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShakeSensitivity(val labelRu: String, val threshold: Float) {
    LOW("Низкая", 16.5f),
    MEDIUM("Обычная", 13.0f),
    HIGH("Высокая", 10.0f)
}

enum class LockMethod(val labelRu: String) {
    AUTO("Автовыбор"),
    ACCESSIBILITY("Спец. возможности"),
    DEVICE_ADMIN("Администратор")
}

data class AppSettings(
    val isMasterServiceEnabled: Boolean = false,
    val isShakeEnabled: Boolean = true,
    val shakeSensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
    val isProximityEnabled: Boolean = true,
    val waveToWake: Boolean = true,
    val coverToLock: Boolean = true,
    val isPocketGuardEnabled: Boolean = true,
    val isDoubleTapPillEnabled: Boolean = false,
    val pillSizeDp: Int = 54,
    val pillAlpha: Float = 0.75f,
    val isHapticsEnabled: Boolean = true,
    val lockMethod: LockMethod = LockMethod.AUTO
)

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_screen_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): AppSettings {
        val sensitivityStr = prefs.getString(KEY_SHAKE_SENSITIVITY, ShakeSensitivity.MEDIUM.name)
        val sensitivity = try {
            ShakeSensitivity.valueOf(sensitivityStr ?: ShakeSensitivity.MEDIUM.name)
        } catch (_: Exception) {
            ShakeSensitivity.MEDIUM
        }

        val lockMethodStr = prefs.getString(KEY_LOCK_METHOD, LockMethod.AUTO.name)
        val lockMethod = try {
            LockMethod.valueOf(lockMethodStr ?: LockMethod.AUTO.name)
        } catch (_: Exception) {
            LockMethod.AUTO
        }

        return AppSettings(
            isMasterServiceEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, false),
            isShakeEnabled = prefs.getBoolean(KEY_SHAKE_ENABLED, true),
            shakeSensitivity = sensitivity,
            isProximityEnabled = prefs.getBoolean(KEY_PROXIMITY_ENABLED, true),
            waveToWake = prefs.getBoolean(KEY_WAVE_TO_WAKE, true),
            coverToLock = prefs.getBoolean(KEY_COVER_TO_LOCK, true),
            isPocketGuardEnabled = prefs.getBoolean(KEY_POCKET_GUARD, true),
            isDoubleTapPillEnabled = prefs.getBoolean(KEY_PILL_ENABLED, false),
            pillSizeDp = prefs.getInt(KEY_PILL_SIZE, 54),
            pillAlpha = prefs.getFloat(KEY_PILL_ALPHA, 0.75f),
            isHapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true),
            lockMethod = lockMethod
        )
    }

    fun setMasterServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isMasterServiceEnabled = enabled)
    }

    fun setShakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isShakeEnabled = enabled)
    }

    fun setShakeSensitivity(sensitivity: ShakeSensitivity) {
        prefs.edit().putString(KEY_SHAKE_SENSITIVITY, sensitivity.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(shakeSensitivity = sensitivity)
    }

    fun setProximityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROXIMITY_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isProximityEnabled = enabled)
    }

    fun setWaveToWake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAVE_TO_WAKE, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(waveToWake = enabled)
    }

    fun setCoverToLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COVER_TO_LOCK, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(coverToLock = enabled)
    }

    fun setPocketGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POCKET_GUARD, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isPocketGuardEnabled = enabled)
    }

    fun setDoubleTapPillEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PILL_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isDoubleTapPillEnabled = enabled)
    }

    fun setPillSizeDp(sizeDp: Int) {
        prefs.edit().putInt(KEY_PILL_SIZE, sizeDp).apply()
        _settingsFlow.value = _settingsFlow.value.copy(pillSizeDp = sizeDp)
    }

    fun setPillAlpha(alpha: Float) {
        prefs.edit().putFloat(KEY_PILL_ALPHA, alpha).apply()
        _settingsFlow.value = _settingsFlow.value.copy(pillAlpha = alpha)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isHapticsEnabled = enabled)
    }

    fun setLockMethod(method: LockMethod) {
        prefs.edit().putString(KEY_LOCK_METHOD, method.name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(lockMethod = method)
    }

    companion object {
        private const val KEY_MASTER_ENABLED = "key_master_enabled"
        private const val KEY_SHAKE_ENABLED = "key_shake_enabled"
        private const val KEY_SHAKE_SENSITIVITY = "key_shake_sensitivity"
        private const val KEY_PROXIMITY_ENABLED = "key_proximity_enabled"
        private const val KEY_WAVE_TO_WAKE = "key_wave_to_wake"
        private const val KEY_COVER_TO_LOCK = "key_cover_to_lock"
        private const val KEY_POCKET_GUARD = "key_pocket_guard"
        private const val KEY_PILL_ENABLED = "key_pill_enabled"
        private const val KEY_PILL_SIZE = "key_pill_size"
        private const val KEY_PILL_ALPHA = "key_pill_alpha"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled"
        private const val KEY_LOCK_METHOD = "key_lock_method"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
