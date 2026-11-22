package com.example.nidankeyboard

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class NidanInputMethodService : InputMethodService() {

    private var activeRow = "a"
    private var currentOrientation = Configuration.ORIENTATION_PORTRAIT
    private val consonantButtons = mutableMapOf<String, TextView>()
    private lateinit var vowelKeys: List<VowelKeyView>
    private lateinit var punctButton: TextView
    private val repeatHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PUNCT_KEY = "punct"
        private val PUNCT_ROW = RowDef(PUNCT_KEY, "ー", arrayOf("ー", "、", "。", "!", "?"))
    }

    override fun onCreate() {
        super.onCreate()
        currentOrientation = resources.configuration.orientation
    }

    override fun onCreateInputView(): View = buildKeyboard()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientation = newConfig.orientation
        try {
            setInputView(buildKeyboard())
        } catch (e: Exception) {
            // Keep whatever view was already showing rather than leaving the
            // IME with nothing attached.
            Log.e("NidanIME", "rebuild on config change failed", e)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun isLandscape(): Boolean = currentOrientation == Configuration.ORIENTATION_LANDSCAPE

    private fun buildKeyboard(): View {
        val landscape = isLandscape()

        consonantButtons.clear()
        val consonantGrid = buildConsonantGrid(landscape)

        val vowelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D7E0EA"))
        }
        vowelKeys = (0 until 5).map { idx ->
            VowelKeyView(this).apply {
                getRowIndex = { currentRow() to idx }
                onResult = { ch -> commitText(ch) }
            }
        }
        vowelKeys.forEach { v ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            vowelColumn.addView(v, lp)
        }

        punctButton = makePunctBtn()
        val modifiersRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modifiersRow.addView(makeGlobeBtn(), modParams(0.6f))
        modifiersRow.addView(punctButton, modParams())
        modifiersRow.addView(makeModBtn("っ") { commitText("っ") }, modParams())
        modifiersRow.addView(
            makeRepeatableBtn("⌫", danger = true) { deleteOne() },
            modParams()
        )

        val utilityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utilityRow.addView(makeRepeatableBtn("space") { commitText("　") }, utilParams(2f))
        utilityRow.addView(makeRepeatableBtn("←") { moveCursor(-1) }, utilParams(1f))
        utilityRow.addView(makeRepeatableBtn("→") { moveCursor(1) }, utilParams(1f))
        utilityRow.addView(makeRepeatableBtn("改行") { commitText("\n") }, utilParams(1f))

        val view = if (landscape) {
            assembleLandscape(modifiersRow, consonantGrid, utilityRow, vowelColumn)
        } else {
            assemblePortrait(modifiersRow, consonantGrid, utilityRow, vowelColumn)
        }

        activeRow = "a"
        render()
        return view
    }

    private fun buildConsonantGrid(landscape: Boolean): GridLayout {
        val consonantGrid = GridLayout(this).apply {
            columnCount = if (landscape) Kana.COL_SPLIT else 2
            rowCount = if (landscape) 2 else Kana.COL_SPLIT
            setBackgroundColor(Color.WHITE)
        }
        Kana.ROWS.forEachIndexed { i, row ->
            val btn = makeConsonantBtn(row)
            consonantButtons[row.key] = btn
            val (col, r) = gridPosition(i, landscape)
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(r, GridLayout.FILL, 1f),
                GridLayout.spec(col, GridLayout.FILL, 1f)
            )
            lp.width = 0
            lp.height = 0
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            consonantGrid.addView(btn, lp)
        }
        return consonantGrid
    }

    /** Portrait: modifiers row, then rails (consonant | vowel) side by side, then utility row. */
    private fun assemblePortrait(
        modifiersRow: View,
        consonantGrid: View,
        utilityRow: View,
        vowelColumn: View
    ): View {
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val railsHeightDp = minOf(220f, screenHeightDp * 0.34f).toInt()
        val rootPad = 8
        val gap = 8
        val modUtilHeight = 40

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E2E5DA"))
            setPadding(dp(rootPad), dp(rootPad), dp(rootPad), dp(rootPad))
        }

        root.addView(
            modifiersRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(modUtilHeight)).also {
                it.bottomMargin = dp(gap)
            }
        )

        val railsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        railsRow.addView(consonantGrid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        railsRow.addView(vowelColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(
            railsRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(railsHeightDp)).also {
                it.bottomMargin = dp(gap)
            }
        )

        root.addView(utilityRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(modUtilHeight)))
        return root
    }

    /** Landscape: modifiers/consonant/utility stacked in a left column only as
     *  wide as the consonant grid; the vowel column spans the full height on
     *  the right, filling what would otherwise be empty space beside the
     *  shorter modifiers/utility rows. */
    private fun assembleLandscape(
        modifiersRow: View,
        consonantGrid: View,
        utilityRow: View,
        vowelColumn: View
    ): View {
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val totalHeightDp = minOf(170f, screenHeightDp * 0.42f).toInt()
        val rootPad = 5
        val gap = 4
        val modUtilHeight = 30

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E2E5DA"))
            setPadding(dp(rootPad), dp(rootPad), dp(rootPad), dp(rootPad))
        }

        val leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftColumn.addView(
            modifiersRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(modUtilHeight)).also {
                it.bottomMargin = dp(gap)
            }
        )
        leftColumn.addView(
            consonantGrid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        leftColumn.addView(
            utilityRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(modUtilHeight)).also {
                it.topMargin = dp(gap)
            }
        )

        root.addView(leftColumn, LinearLayout.LayoutParams(0, dp(totalHeightDp), 1f))
        root.addView(vowelColumn, LinearLayout.LayoutParams(0, dp(totalHeightDp), 1f))
        return root
    }

    private fun gridPosition(i: Int, landscape: Boolean): Pair<Int, Int> {
        return if (!landscape) {
            val col = if (i < Kana.COL_SPLIT) 0 else 1
            val r = if (i < Kana.COL_SPLIT) i else i - Kana.COL_SPLIT
            col to r
        } else if (i < Kana.COL_SPLIT) {
            (Kana.COL_SPLIT - 1 - i) to 0
        } else {
            (Kana.COL_SPLIT - 1 - (i - Kana.COL_SPLIT)) to 1
        }
    }

    private fun currentRow(): RowDef = if (activeRow == PUNCT_KEY) PUNCT_ROW else Kana.getRow(activeRow)

    private fun render() {
        val row = currentRow()
        consonantButtons.forEach { (key, btn) ->
            if (key == activeRow) {
                btn.setBackgroundColor(Color.parseColor("#2A4566"))
                btn.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundColor(Color.parseColor("#E2E5DA"))
                btn.setTextColor(Color.parseColor("#27261F"))
            }
        }
        if (activeRow == PUNCT_KEY) {
            punctButton.setBackgroundColor(Color.parseColor("#2A4566"))
            punctButton.setTextColor(Color.WHITE)
        } else {
            punctButton.setBackgroundColor(Color.WHITE)
            punctButton.setTextColor(Color.parseColor("#27261F"))
        }
        vowelKeys.forEachIndexed { i, v ->
            val ch = row.kana[i]
            v.kanaText.text = ch ?: "―"
            v.isEnabled = ch != null
            v.alpha = if (ch != null) 1f else 0.35f
            if (ch != null) {
                val rc = Kana.rightChar(row, i)
                val lc = Kana.leftChar(row, i)
                v.hintRight.text = if (rc != null && rc != ch) rc else ""
                v.hintLeft.text = if (lc != null && lc != ch) lc else ""
            } else {
                v.hintRight.text = ""
                v.hintLeft.text = ""
            }
        }
    }

    private fun makeConsonantBtn(row: RowDef): TextView = TextView(this).apply {
        text = row.label
        gravity = Gravity.CENTER
        textSize = 18f
        isClickable = true
        isFocusable = true
        setOnClickListener {
            activeRow = row.key
            render()
        }
    }

    /** Selects the punctuation pseudo-row: the vowel keys then show
     *  ー・、・。・!・? top to bottom instead of a kana row. */
    private fun makePunctBtn(): TextView = TextView(this).apply {
        text = "ー、。!?"
        gravity = Gravity.CENTER
        textSize = 11f
        isClickable = true
        isFocusable = true
        setOnClickListener {
            activeRow = PUNCT_KEY
            render()
        }
    }

    private fun makeModBtn(label: String, danger: Boolean = false, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(if (danger) Color.parseColor("#A04B33") else Color.parseColor("#27261F"))
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    /** Globe/keyboard-switch button: tap cycles to the next enabled IME,
     *  long-press always opens the full system picker (e.g. to reach an
     *  alphanumeric/symbol keyboard). */
    private fun makeGlobeBtn(): TextView = TextView(this).apply {
        text = "🌐"
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(Color.parseColor("#27261F"))
        setBackgroundColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                showInputMethodPickerCompat()
            }
        }
        setOnLongClickListener {
            showInputMethodPickerCompat()
            true
        }
    }

    private fun showInputMethodPickerCompat() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    /** A button that fires once immediately, then auto-repeats while held,
     *  starting slow and accelerating down to a fast minimum interval. */
    private fun makeRepeatableBtn(label: String, danger: Boolean = false, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (label.length <= 2) 18f else 15f
            setTextColor(if (danger) Color.parseColor("#A04B33") else Color.parseColor("#27261F"))
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            bindRepeatable(this, action)
        }

    private fun bindRepeatable(view: View, action: () -> Unit) {
        val initialDelay = 300L
        val minDelay = 35L
        var currentDelay = initialDelay
        var repeating = false
        lateinit var tick: Runnable
        tick = Runnable {
            action()
            currentDelay = (currentDelay * 0.65f).toLong().coerceAtLeast(minDelay)
            repeatHandler.postDelayed(tick, currentDelay)
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = true
                    action()
                    currentDelay = initialDelay
                    repeatHandler.postDelayed(tick, initialDelay)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (repeating) {
                        repeatHandler.removeCallbacks(tick)
                        repeating = false
                    }
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun modParams(weight: Float = 1f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
            it.setMargins(dp(3), 0, dp(3), 0)
        }

    private fun utilParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).also {
            it.setMargins(dp(3), 0, dp(3), 0)
        }

    private fun commitText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun deleteOne() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun moveCursor(delta: Int) {
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
