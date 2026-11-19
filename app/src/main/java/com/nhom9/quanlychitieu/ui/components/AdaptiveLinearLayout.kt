package com.nhom9.quanlychitieu.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.nhom9.quanlychitieu.R

/**
 * Keeps compact rows at ordinary text sizes and gives each item its own line
 * when larger text or a narrow container needs more room. Text is never scaled
 * down. XML supplies the ordinary horizontal LayoutParams.
 */
class AdaptiveLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private class ChildParams(var horizontal: LayoutParams) {
        val vertical = LayoutParams(horizontal)
    }

    private val childParams = mutableMapOf<View, ChildParams>()
    private var stacked: Boolean? = null
    private var childrenChanged = false
    private var stackWidth = 300 * resources.displayMetrics.density

    init {
        context.withStyledAttributes(attrs, intArrayOf(R.attr.adaptive_stackWidth)) {
            stackWidth = getDimension(0, stackWidth)
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        // Keep the ordinary params untouched, including relative-margin state.
        // Allocate the alternative only when children enter the hierarchy.
        childParams[child] = ChildParams(child.layoutParams as LayoutParams)
        childrenChanged = true
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        val params = childParams.remove(child)
        if (params != null && child.layoutParams === params.vertical) {
            child.layoutParams = params.horizontal
        }
        childrenChanged = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val narrow = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED &&
            availableWidth < stackWidth
        val shouldStack = resources.configuration.fontScale >= 1.3f || narrow

        // Accept replacement params from callers without allocating during measure.
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = childParams.getValue(child)
            val current = child.layoutParams as LayoutParams
            if (current !== params.horizontal && current !== params.vertical) {
                params.horizontal = current
                childrenChanged = true
            }
        }
        if (stacked != shouldStack || childrenChanged) {
            stacked = shouldStack
            childrenChanged = false
            orientation = if (shouldStack) VERTICAL else HORIZONTAL
            gravity = if (shouldStack) Gravity.START else Gravity.CENTER_VERTICAL
            setBaselineAligned(false)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val params = childParams.getValue(child)
                val original = params.horizontal
                val next = if (shouldStack) params.vertical else original
                if (shouldStack) {
                    next.width = if (original.width <= 0 || child is TextView) {
                        LayoutParams.MATCH_PARENT
                    } else original.width
                    next.height = if (original.height == LayoutParams.MATCH_PARENT) {
                        LayoutParams.WRAP_CONTENT
                    } else original.height
                    next.weight = 0f
                    next.marginStart = 0
                    next.marginEnd = 0
                    next.leftMargin = 0
                    next.rightMargin = 0
                    next.topMargin = if (index > 0) {
                        maxOf(original.topMargin, (8 * resources.displayMetrics.density).toInt())
                    } else original.topMargin
                    next.bottomMargin = original.bottomMargin
                    next.gravity = Gravity.START
                }
                child.layoutParams = next
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
