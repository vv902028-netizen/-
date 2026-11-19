package com.example.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.LockMethod
import com.example.data.PreferencesManager
import com.example.data.ShakeSensitivity
import com.example.service.ScreenControlService
import com.example.service.ScreenLockManager
import com.example.ui.components.MasterPowerPill
import com.example.ui.components.PillButton
import com.example.ui.components.PillSegmentedGroup
import com.example.ui.components.PillShape
import com.example.ui.components.PillSwitch
import com.example.ui.components.SectionContainer
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.HapticHelper

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val settings by prefsManager.settingsFlow.collectAsState()

    var isAdminActive by remember { mutableStateOf(ScreenLockManager.isDeviceAdminActive(context)) }
    var isA11yActive by remember { mutableStateOf(ScreenLockManager.isAccessibilityActive()) }
    var isOverlayGranted by remember { mutableStateOf(ScreenLockManager.canDrawOverlays(context)) }
    var isBatteryOptExempt by remember {
        mutableStateOf(ScreenLockManager.isIgnoringBatteryOptimizations(context))
    }

    var showLockMethodDialog by remember { mutableStateOf(false) }

    // Re-check permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAdminActive = ScreenLockManager.isDeviceAdminActive(context)
                isA11yActive = ScreenLockManager.isAccessibilityActive()
                isOverlayGranted = ScreenLockManager.canDrawOverlays(context)
                isBatteryOptExempt = ScreenLockManager.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLockCapable = isAdminActive || isA11yActive

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Умный Экран",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Замена кнопки питания",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                PillButton(
                    text = "Тест",
                    icon = Icons.Default.Lock,
                    onClick = {
                        val locked = ScreenLockManager.lockScreen(context)
                        if (!locked) {
                            Toast.makeText(
                                context,
                                "Предоставьте права администратора или спец. возможностей ниже",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    isPrimary = true,
                    enableHaptics = settings.isHapticsEnabled,
                    testTag = "test_lock_button"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Master switch
            MasterPowerPill(
                isActive = settings.isMasterServiceEnabled && isLockCapable,
                onToggle = {
                    if (!isLockCapable) {
                        Toast.makeText(
                            context,
                            "Сначала активируйте блокировку (Администратор или Спец. возможности)",
                            Toast.LENGTH_LONG
                        ).show()
                        return@MasterPowerPill
                    }
                    val newActive = !settings.isMasterServiceEnabled
                    prefsManager.setMasterServiceEnabled(newActive)
                    if (newActive) {
                        ScreenControlService.start(context)
                    } else {
                        ScreenControlService.stop(context)
                    }
                },
                enableHaptics = settings.isHapticsEnabled
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permissions Status
            SectionContainer(title = "СИСТЕМНЫЕ РАЗРЕШЕНИЯ") {
                // Screen Lock Capability
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isLockCapable) AccentEmerald.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLockCapable) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isLockCapable) AccentEmerald else AccentRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Блокировка экрана",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    isA11yActive -> "Спец. возможности (без сброса биометрии)"
                                    isAdminActive -> "Администратор устройства"
                                    else -> "Требуется разрешение для выключения экрана"
                                },
                                color = if (isLockCapable) AccentEmerald else AccentAmber,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!isLockCapable) {
                        PillButton(
                            text = "Включить",
                            onClick = { showLockMethodDialog = true },
                            isPrimary = true,
                            enableHaptics = settings.isHapticsEnabled
                        )
                    } else {
                        PillButton(
                            text = "Метод",
                            onClick = { showLockMethodDialog = true },
                            isPrimary = false,
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Battery Optimization
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isBatteryOptExempt) AccentEmerald.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBatteryOptExempt) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isBatteryOptExempt) AccentEmerald else AccentAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Работа в спящем режиме",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isBatteryOptExempt) "Без ограничений батареи" else "Исключите из экономии заряда",
                                color = if (isBatteryOptExempt) AccentEmerald else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!isBatteryOptExempt) {
                        PillButton(
                            text = "Разрешить",
                            onClick = { ScreenLockManager.openBatteryOptimizationSettings(context) },
                            isPrimary = false,
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }
                }

                // Overlay Permission if pill enabled
                if (settings.isDoubleTapPillEnabled && !isOverlayGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(AccentAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Поверх других окон",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Нужно для плавающего овала блокировки",
                                    color = AccentAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        PillButton(
                            text = "Разрешить",
                            onClick = { ScreenLockManager.openOverlaySettings(context) },
                            isPrimary = true,
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shake Gesture Section
            SectionContainer(title = "ВСТРЯХИВАНИЕ (SHAKE)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Включение и выключение потряхиванием",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Выключает экран когда включен, будит когда спит",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    PillSwitch(
                        checked = settings.isShakeEnabled,
                        onCheckedChange = { prefsManager.setShakeEnabled(it) },
                        enableHaptics = settings.isHapticsEnabled
                    )
                }

                if (settings.isShakeEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Чувствительность датчика движения:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val sensitivityOptions = listOf("Низкая", "Обычная", "Высокая")
                    val selectedIdx = when (settings.shakeSensitivity) {
                        ShakeSensitivity.LOW -> 0
                        ShakeSensitivity.MEDIUM -> 1
                        ShakeSensitivity.HIGH -> 2
                    }

                    PillSegmentedGroup(
                        options = sensitivityOptions,
                        selectedIndex = selectedIdx,
                        onSelectIndex = { index ->
                            val selected = when (index) {
                                0 -> ShakeSensitivity.LOW
                                1 -> ShakeSensitivity.MEDIUM
                                else -> ShakeSensitivity.HIGH
                            }
                            prefsManager.setShakeSensitivity(selected)
                        },
                        enableHaptics = settings.isHapticsEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Proximity Sensor Section
            SectionContainer(title = "ДАТЧИК ПРИБЛИЖЕНИЯ") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Включение по датчику приближения",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Взмах рукой перед экраном или извлечение из кармана",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    PillSwitch(
                        checked = settings.isProximityEnabled,
                        onCheckedChange = { prefsManager.setProximityEnabled(it) },
                        enableHaptics = settings.isHapticsEnabled
                    )
                }

                if (settings.isProximityEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Wave to wake sub-toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Пробуждение взмахом (Wave to wake)",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        PillSwitch(
                            checked = settings.waveToWake,
                            onCheckedChange = { prefsManager.setWaveToWake(it) },
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cover to lock sub-toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Блокировка прикрытием (~1 сек)",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        PillSwitch(
                            checked = settings.coverToLock,
                            onCheckedChange = { prefsManager.setCoverToLock(it) },
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pocket guard sub-toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Защита в кармане (Pocket Guard)",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Запрещает случайное включение при закрытом датчике",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        PillSwitch(
                            checked = settings.isPocketGuardEnabled,
                            onCheckedChange = { prefsManager.setPocketGuardEnabled(it) },
                            enableHaptics = settings.isHapticsEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Double Tap Floating Pill Section
            SectionContainer(title = "ПЛАВАЮЩИЙ ОВАЛ ДВОЙНОГО ТАПА") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Кнопка блокировки на экране",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Перемещаемый овал: двойной тап мгновенно гасит экран",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    PillSwitch(
                        checked = settings.isDoubleTapPillEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !ScreenLockManager.canDrawOverlays(context)) {
                                ScreenLockManager.openOverlaySettings(context)
                            }
                            prefsManager.setDoubleTapPillEnabled(enabled)
                        },
                        enableHaptics = settings.isHapticsEnabled
                    )
                }

                if (settings.isDoubleTapPillEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Прозрачность овала:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val alphaOptions = listOf("50%", "75%", "100%")
                    val selectedAlphaIdx = when {
                        settings.pillAlpha < 0.6f -> 0
                        settings.pillAlpha < 0.85f -> 1
                        else -> 2
                    }

                    PillSegmentedGroup(
                        options = alphaOptions,
                        selectedIndex = selectedAlphaIdx,
                        onSelectIndex = { idx ->
                            val alpha = when (idx) {
                                0 -> 0.5f
                                1 -> 0.75f
                                else -> 1.0f
                            }
                            prefsManager.setPillAlpha(alpha)
                        },
                        enableHaptics = settings.isHapticsEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Haptics & General Section
            SectionContainer(title = "ВИБРООТКЛИК И НАСТРОЙКИ") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Тактильная отдача (Гаптика)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Короткая вибрация при срабатывании жестов и переключении",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    PillSwitch(
                        checked = settings.isHapticsEnabled,
                        onCheckedChange = { prefsManager.setHapticsEnabled(it) },
                        enableHaptics = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Modal Dialog to Select / Grant Lock Method
    if (showLockMethodDialog) {
        AlertDialog(
            onDismissRequest = { showLockMethodDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Способ блокировки экрана",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Выберите предпочтительный механизм выключения экрана:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    // Option 1: Accessibility
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PillShape)
                            .background(if (settings.lockMethod == LockMethod.ACCESSIBILITY) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceVariant)
                            .border(1.dp, if (settings.lockMethod == LockMethod.ACCESSIBILITY) AccentCyan else DarkOutline, PillShape)
                            .clickable {
                                HapticHelper.performClick(context, settings.isHapticsEnabled)
                                prefsManager.setLockMethod(LockMethod.ACCESSIBILITY)
                                if (!isA11yActive) {
                                    ScreenLockManager.openAccessibilitySettings(context)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Специальные возможности",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isA11yActive) "Включено • Не сбрасывает отпечаток пальца" else "Нажмите чтобы предоставить доступ",
                                color = if (isA11yActive) AccentEmerald else AccentAmber,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Device Admin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PillShape)
                            .background(if (settings.lockMethod == LockMethod.DEVICE_ADMIN) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceVariant)
                            .border(1.dp, if (settings.lockMethod == LockMethod.DEVICE_ADMIN) AccentCyan else DarkOutline, PillShape)
                            .clickable {
                                HapticHelper.performClick(context, settings.isHapticsEnabled)
                                prefsManager.setLockMethod(LockMethod.DEVICE_ADMIN)
                                if (!isAdminActive) {
                                    ScreenLockManager.openDeviceAdminSettings(context)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Администратор устройства",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAdminActive) "Включено • Надежно на старых Android" else "Нажмите чтобы активировать",
                                color = if (isAdminActive) AccentEmerald else AccentAmber,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Auto
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PillShape)
                            .background(if (settings.lockMethod == LockMethod.AUTO) AccentCyan.copy(alpha = 0.15f) else DarkSurfaceVariant)
                            .border(1.dp, if (settings.lockMethod == LockMethod.AUTO) AccentCyan else DarkOutline, PillShape)
                            .clickable {
                                HapticHelper.performClick(context, settings.isHapticsEnabled)
                                prefsManager.setLockMethod(LockMethod.AUTO)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Автовыбор (Рекомендуется)",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Использует Спец. возможности, а при их отсутствии — Администратора",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                PillButton(
                    text = "Готово",
                    onClick = { showLockMethodDialog = false },
                    isPrimary = true,
                    enableHaptics = settings.isHapticsEnabled
                )
            }
        )
    }
}
