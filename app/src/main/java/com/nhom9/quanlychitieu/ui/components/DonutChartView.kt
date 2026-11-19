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

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DonutData(
        val topLabel: String = "TỔNG CHI MẪU",
        val amountText: String = "8.450.000",
        val unitText: String = "VNĐ",
        val percentages: List<Int> = listOf(35, 25, 25, 15)
    )

    private val segmentPaints = listOf(
        R.color.chart_category_shopping,
        R.color.chart_category_transport,
        R.color.chart_category_home,
        R.color.chart_category_entertainment
    ).map { colorId ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, colorId)
            style = Paint.Style.STROKE
            strokeWidth = dp(14f)
            strokeCap = Paint.Cap.BUTT
        }
    }

    private val textPaints = listOf(
        textPaint(R.color.text_secondary, true),
        textPaint(R.color.text_primary, true),
        textPaint(R.color.text_secondary, false)
    )

    private var currentData = DonutData()
    private val oval = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.ui_donut_chart_description)
    }

    fun setChartData(data: DonutData) {
        this.currentData = data
        invalidate()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun textPaint(colorId: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorId)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = bold
    }

    private fun updateTextSize() {
        listOf(11f, 20f, 11f).forEachIndexed { index, size ->
            textPaints[index].textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
        }
    }

    private fun textBlockHeight(): Float = textPaints.sumOf {
        (it.fontMetrics.descent - it.fontMetrics.ascent).toDouble()
    }.toFloat() + dp(8f)

    private fun textBelowRing() = resources.configuration.fontScale > 1.3f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateTextSize()
        val desiredHeight = dp(220f) + if (textBelowRing()) textBlockHeight() + dp(12f) else 0f
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth.coerceAtLeast(dp(220f).roundToInt()), widthMeasureSpec),
            resolveSize(suggestedMinimumHeight.coerceAtLeast(desiredHeight.roundToInt()), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        updateTextSize()
        val textHeight = textBlockHeight()
        val ringAreaHeight = if (textBelowRing()) height - textHeight - dp(12f) else height.toFloat()
        val size = (minOf(width.toFloat(), ringAreaHeight) - dp(24f)).coerceAtLeast(0f)
        val left = (width - size) / 2f
        val top = (ringAreaHeight - size) / 2f
        oval.set(left, top, left + size, top + size)

        var startAngle = -90f
        currentData.percentages.forEachIndexed { index, percentage ->
            val sweep = percentage * 3.6f
            val paint = segmentPaints.getOrElse(index) { segmentPaints.last() }
            canvas.drawArc(oval, startAngle + 2f, sweep - 4f, false, paint)
            startAngle += sweep
        }

        var textTop = if (textBelowRing()) ringAreaHeight + dp(12f) else (height - textHeight) / 2f
        val labels = listOf(currentData.topLabel, currentData.amountText, currentData.unitText)
        labels.forEachIndexed { index, label ->
            val paint = textPaints[index]
            val metrics = paint.fontMetrics
            canvas.drawText(label, width / 2f, textTop - metrics.ascent, paint)
            textTop += metrics.descent - metrics.ascent + if (index < 2) dp(4f) else 0f
        }
    }
}