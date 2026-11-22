package com.example.nidankeyboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * One vowel-rail key: a faint left hint, the main kana, a faint right hint.
 * Tap commits the plain kana; a horizontal drag past the threshold commits
 * [onResult] with the row's right/left variant instead (see Kana.kt).
 */
class VowelKeyView(context: Context) : LinearLayout(context) {
    val hintLeft = TextView(context)
    val kanaText = TextView(context)
    val hintRight = TextView(context)

    var onResult: ((String) -> Unit)? = null
    var getRowIndex: (() -> Pair<RowDef, Int>)? = null

    private var downX = 0f
    private var dragging = false
    private val threshPx = 26f * resources.displayMetrics.density
    private val dragClampPx = 22f * resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        isClickable = true
        isFocusable = true

        hintLeft.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        hintLeft.setTextColor(Color.parseColor("#8C9080"))
        hintLeft.textSize = 11f
        hintLeft.alpha = 0.6f

        kanaText.gravity = Gravity.CENTER
        kanaText.textSize = 20f
        kanaText.setTextColor(Color.parseColor("#27261F"))

        hintRight.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        hintRight.setTextColor(Color.parseColor("#8C9080"))
        hintRight.textSize = 11f
        hintRight.alpha = 0.6f

        val pad = (10 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)

        addView(hintLeft, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(kanaText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(hintRight, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = event.rawX - downX
                    translationX = max(-dragClampPx, min(dragClampPx, dx))
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val dx = event.rawX - downX
                    translationX = 0f
                    dragging = false
                    val (row, index) = getRowIndex?.invoke() ?: return true
                    val base = row.kana[index] ?: return true
                    val ch = when {
                        dx > threshPx -> Kana.rightChar(row, index)
                        dx < -threshPx -> Kana.leftChar(row, index)
                        else -> base
                    }
                    if (ch != null) onResult?.invoke(ch)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                translationX = 0f
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
