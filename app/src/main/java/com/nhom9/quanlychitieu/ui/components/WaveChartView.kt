package com.nhom9.quanlychitieu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nhom9.quanlychitieu.R

class WaveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val incomeColor = ContextCompat.getColor(context, R.color.chart_income)
    private val expenseColor = ContextCompat.getColor(context, R.color.chart_expense)

    private var incomeYRatio = 0.35f
    private var expenseYRatio = 0.50f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.ui_wave_chart_description)
    }

    fun setWaveData(income: Long, expense: Long) {
        val total = (income + expense).toFloat().coerceAtLeast(1f)
        incomeYRatio = (1.0f - (income.toFloat() / total)).coerceIn(0.15f, 0.85f)
        expenseYRatio = (1.0f - (expense.toFloat() / total)).coerceIn(0.15f, 0.85f)
        invalidate()
    }

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = incomeColor
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(30f)
    }

    private val cyanFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = expenseColor
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(30f)
    }

    private val pinkFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cyanPath = Path()
    private val cyanFillPath = Path()

    private val pinkPath = Path()
    private val pinkFillPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            cyanFillPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                ColorUtils.setAlphaComponent(incomeColor, 32),
                ColorUtils.setAlphaComponent(incomeColor, 0),
                Shader.TileMode.CLAMP
            )
            pinkFillPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                ColorUtils.setAlphaComponent(expenseColor, 32),
                ColorUtils.setAlphaComponent(expenseColor, 0),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        cyanPath.reset()
        cyanFillPath.reset()
        pinkPath.reset()
        pinkFillPath.reset()

        val incY = h * incomeYRatio
        val expY = h * expenseYRatio

        // Income - smooth wave based on incomeYRatio
        cyanPath.moveTo(0f, h * 0.65f)
        cyanPath.cubicTo(
            w * 0.25f, h * 0.70f,
            w * 0.45f, (incY + h * 0.1f).coerceIn(0f, h),
            w * 0.70f, (incY + h * 0.05f).coerceIn(0f, h)
        )
        cyanPath.cubicTo(
            w * 0.82f, (incY + h * 0.15f).coerceIn(0f, h),
            w * 0.92f, (incY * 0.8f).coerceIn(0f, h),
            w, incY
        )

        cyanFillPath.addPath(cyanPath)
        cyanFillPath.lineTo(w, h)
        cyanFillPath.lineTo(0f, h)
        cyanFillPath.close()

        // Expense - smooth crossing wave based on expenseYRatio
        pinkPath.moveTo(0f, h * 0.45f)
        pinkPath.cubicTo(
            w * 0.20f, (expY * 0.7f).coerceIn(0f, h),
            w * 0.40f, (expY + h * 0.1f).coerceIn(0f, h),
            w * 0.65f, expY
        )
        pinkPath.cubicTo(
            w * 0.80f, (expY + h * 0.15f).coerceIn(0f, h),
            w * 0.90f, (expY * 0.9f).coerceIn(0f, h),
            w, expY
        )

        pinkFillPath.addPath(pinkPath)
        pinkFillPath.lineTo(w, h)
        pinkFillPath.lineTo(0f, h)
        pinkFillPath.close()

        // Draw fills first
        canvas.drawPath(cyanFillPath, cyanFillPaint)
        canvas.drawPath(pinkFillPath, pinkFillPaint)

        // Draw line strokes over fills
        canvas.drawPath(cyanPath, cyanPaint)
        canvas.drawPath(pinkPath, pinkPaint)
    }
}