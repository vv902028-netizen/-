package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.data.PreferencesManager

class FloatingOverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var isShowing = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing || !ScreenLockManager.canDrawOverlays(context)) return

        val prefs = PreferencesManager.getInstance(context)
        val settings = prefs.settingsFlow.value

        val sizePx = dpToPx(settings.pillSizeDp.toFloat()).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        val view = ImageView(context).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E2024"))
                setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#383C46"))
            }
            background = bg
            alpha = settings.pillAlpha
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(12f).toInt()
            setPadding(pad, pad, pad, pad)
            setImageResource(android.R.drawable.ic_lock_power_off)
            setColorFilter(Color.parseColor("#00E5FF"))
        }

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                ScreenLockManager.lockScreen(context)
                return true
            }
        })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            isShowing = true
        } catch (_: Exception) {
            isShowing = false
        }
    }

    fun hide() {
        if (!isShowing || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
        isShowing = false
    }

    fun updateSettings() {
        val prefs = PreferencesManager.getInstance(context)
        val settings = prefs.settingsFlow.value
        if (settings.isDoubleTapPillEnabled && settings.isMasterServiceEnabled) {
            if (!isShowing) {
                show()
            } else {
                overlayView?.let { v ->
                    v.alpha = settings.pillAlpha
                }
            }
        } else {
            hide()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
