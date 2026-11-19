package com.nhom9.quanlychitieu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.nhom9.quanlychitieu.R
import kotlin.math.roundToInt

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarData(
        val labels: List<String>,
        val incomeHeights: List<Float>,
        val expenseHeights: List<Float>
    )

    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_income)
    }
    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_expense)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textAlign = Paint.Align.CENTER
    }
    private val barRect = RectF()

    private var currentData = BarData(
        labels = listOf("T1", "T2", "T3", "T4"),
        incomeHeights = listOf(0.60f, 0.75f, 0.85f, 0.65f),
        expenseHeights = listOf(0.40f, 0.95f, 0.50f, 0.45f)
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.ui_bar_chart_description)
    }

    fun setChartData(data: BarData) {
        this.currentData = data
        invalidate()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun updateTextSize() {
        val labelSize = if (currentData.labels.size > 8) 10f else 12f
        labelPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, labelSize, resources.displayMetrics)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateTextSize()
        val labelHeight = labelPaint.fontMetrics.run { descent - ascent }
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth.coerceAtLeast(dp(240f).roundToInt()), widthMeasureSpec),
            resolveSize(suggestedMinimumHeight.coerceAtLeast((dp(160f) + labelHeight).roundToInt()), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        updateTextSize()
        val metrics = labelPaint.fontMetrics
        val labelBaseline = height - dp(4f) - metrics.descent
        val chartBottom = labelBaseline + metrics.ascent - dp(8f)
        val maxBarHeight = (chartBottom - dp(8f)).coerceAtLeast(0f)

        val count = currentData.labels.size
        if (count == 0) return
        val groupWidth = width.toFloat() / count
        val barWidth = minOf(dp(12f), groupWidth * 0.28f)
        val barGap = dp(3f)

        for (i in 0 until count) {
            val center = groupWidth * (i + 0.5f)
            val incH = currentData.incomeHeights.getOrElse(i) { 0f }
            val expH = currentData.expenseHeights.getOrElse(i) { 0f }

            val incomeLeft = center - barWidth - barGap / 2f
            barRect.set(incomeLeft, chartBottom - maxBarHeight * incH, incomeLeft + barWidth, chartBottom)
            canvas.drawRoundRect(barRect, dp(3f), dp(3f), incomePaint)

            val expenseLeft = center + barGap / 2f
            barRect.set(expenseLeft, chartBottom - maxBarHeight * expH, expenseLeft + barWidth, chartBottom)
            canvas.drawRoundRect(barRect, dp(3f), dp(3f), expensePaint)

            canvas.drawText(currentData.labels[i], center, labelBaseline, labelPaint)
        }
    }
}