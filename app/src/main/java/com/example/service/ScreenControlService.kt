package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettings
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ScreenControlService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private lateinit var prefsManager: PreferencesManager
    private var floatingOverlayManager: FloatingOverlayManager? = null

    private var currentSettings = AppSettings()
    private var isScreenOn = true
    private var isProximityNear = false

    // Shake detection state
    private var lastShakeTimestamp: Long = 0
    private var shakeCount = 0
    private val shakeWindowMs = 600L

    // Proximity wave / cover detection state
    private val mainHandler = Handler(Looper.getMainLooper())
    private var coverLockRunnable: Runnable? = null
    private var lastProximityChangeTime: Long = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    cancelCoverToLock()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    cancelCoverToLock()
                }
                ACTION_TRIGGER_LOCK -> {
                    ScreenLockManager.lockScreen(this@ScreenControlService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager.getInstance(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        floatingOverlayManager = FloatingOverlayManager(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = powerManager?.isInteractive ?: true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(ACTION_TRIGGER_LOCK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        registerSensors()

        serviceScope.launch {
            prefsManager.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                floatingOverlayManager?.updateSettings()
                if (!settings.isMasterServiceEnabled) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_LOCK) {
            ScreenLockManager.lockScreen(this)
            return START_STICKY
        }
        floatingOverlayManager?.updateSettings()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}

        sensorManager.unregisterListener(this)
        floatingOverlayManager?.hide()
        cancelCoverToLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !currentSettings.isMasterServiceEnabled) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (currentSettings.isShakeEnabled) {
                    handleAccelerometer(event)
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                if (currentSettings.isProximityEnabled) {
                    handleProximity(event)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        val threshold = currentSettings.shakeSensitivity.threshold / SensorManager.GRAVITY_EARTH

        if (gForce > threshold) {
            val now = System.currentTimeMillis()

            // Pocket Guard: if proximity sensor detects object right against phone, suppress shake
            if (currentSettings.isPocketGuardEnabled && isProximityNear) {
                return
            }

            if (now - lastShakeTimestamp < shakeWindowMs) {
                shakeCount++
                if (shakeCount >= 2) {
                    shakeCount = 0
                    lastShakeTimestamp = 0
                    onShakeDetected()
                }
            } else {
                shakeCount = 1
                lastShakeTimestamp = now
            }
        }
    }

    private fun onShakeDetected() {
        if (isScreenOn) {
            ScreenLockManager.lockScreen(this)
        } else {
            ScreenLockManager.wakeScreen(this)
        }
    }

    private fun handleProximity(event: SensorEvent) {
        val distance = event.values[0]
        val maxRange = event.sensor.maximumRange
        val isNear = distance < 3.0f || (distance < maxRange && distance <= 4.0f)
        val stateChanged = isNear != isProximityNear
        isProximityNear = isNear
        val now = System.currentTimeMillis()

        if (stateChanged) {
            lastProximityChangeTime = now

            if (isNear) {
                // Sensor covered
                if (isScreenOn && currentSettings.coverToLock) {
                    scheduleCoverToLock()
                }
            } else {
                // Sensor uncovered (wave / removed from pocket)
                cancelCoverToLock()
                if (!isScreenOn && currentSettings.waveToWake) {
                    ScreenLockManager.wakeScreen(this)
                }
            }
        }
    }

    private fun scheduleCoverToLock() {
        cancelCoverToLock()
        val runnable = Runnable {
            if (isScreenOn && isProximityNear && currentSettings.coverToLock) {
                ScreenLockManager.lockScreen(this@ScreenControlService)
            }
        }
        coverLockRunnable = runnable
        mainHandler.postDelayed(runnable, 1100L) // 1.1s hold to lock
    }

    private fun cancelCoverToLock() {
        coverLockRunnable?.let { mainHandler.removeCallbacks(it) }
        coverLockRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, ScreenControlService::class.java).apply {
            action = ACTION_TRIGGER_LOCK
        }
        val lockPendingIntent = PendingIntent.getService(
            this,
            1,
            lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_lock_idle_lock,
                getString(R.string.notification_action_lock),
                lockPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "screen_control_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER_LOCK = "com.example.action.TRIGGER_LOCK"

        fun start(context: Context) {
            val intent = Intent(context, ScreenControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenControlService::class.java)
            context.stopService(intent)
        }
    }
}
