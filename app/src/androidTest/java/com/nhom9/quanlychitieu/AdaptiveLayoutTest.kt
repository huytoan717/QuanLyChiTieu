package com.nhom9.quanlychitieu

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.ui.components.AdaptiveLinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    private fun context(fontScale: Float = 1f): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
        })
    }

    private fun measure(row: AdaptiveLinearLayout, widthDp: Int) {
        val width = (widthDp * row.resources.displayMetrics.density).toInt()
        row.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    @Test fun widthChangesRestoreWeightsAndRelativeMarginsAndReuseParams() = onMain {
        val context = context()
        val row = AdaptiveLinearLayout(context)
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val child = TextView(context).apply { text = "Số dư khả dụng" }
        val original = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = 13
            marginEnd = 17
            topMargin = 3
            bottomMargin = 5
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(child, original)

        measure(row, 600)
        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        measure(row, 200)
        assertEquals(LinearLayout.VERTICAL, row.orientation)
        val vertical = child.layoutParams as LinearLayout.LayoutParams
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, vertical.width)
        assertEquals(LinearLayout.LayoutParams.WRAP_CONTENT, vertical.height)
        assertEquals(0f, vertical.weight, 0f)
        assertEquals(0, vertical.marginStart)
        assertEquals(0, vertical.marginEnd)

        measure(row, 600)
        val restored = child.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, restored.width)
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, restored.height)
        assertEquals(1f, restored.weight, 0f)
        assertEquals(13, restored.marginStart)
        assertEquals(17, restored.marginEnd)
        assertEquals(3, restored.topMargin)
        assertEquals(5, restored.bottomMargin)
        assertEquals(Gravity.CENTER_VERTICAL, restored.gravity)
        measure(row, 200)
        assertSame("Stacking reuses the prepared params", vertical, child.layoutParams)
        measure(row, 600)
        assertSame("Unstacking reuses the original params", restored, child.layoutParams)
    }

    @Test fun fontScaleThresholdStacksEvenWhenTheRowIsWide() = onMain {
        for (fontScale in listOf(1.29f, 1.3f)) {
            val context = context(fontScale)
            val row = AdaptiveLinearLayout(context)
            row.addView(TextView(context).apply { text = "Báo cáo chi tiêu" })
            measure(row, 600)
            assertEquals(
                if (fontScale >= 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL,
                row.orientation,
            )
        }
    }

    @Test fun addingRemovingAndReaddingChildrenWhileStackedKeepsTheirOrdinaryParams() = onMain {
        val context = context()
        val row = AdaptiveLinearLayout(context)
        val first = TextView(context).apply { text = "Thu nhập" }
        row.addView(first)
        measure(row, 200)

        val added = TextView(context).apply { text = "Chi tiêu" }
        val original = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        original.marginStart = 11
        row.addView(added, original)
        measure(row, 200)
        var params = added.layoutParams as LinearLayout.LayoutParams
        assertEquals(LinearLayout.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(0f, params.weight, 0f)
        assertTrue(params.topMargin >= (8 * row.resources.displayMetrics.density).toInt())

        row.removeView(first)
        measure(row, 200)
        params = added.layoutParams as LinearLayout.LayoutParams
        assertEquals("The new first child does not retain inter-item spacing", 0, params.topMargin)

        row.removeView(added)
        row.addView(added)
        measure(row, 200)
        measure(row, 600)
        params = added.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, params.width)
        assertEquals(2f, params.weight, 0f)
        assertEquals(11, params.marginStart)
    }

    @Test fun replacingParamsWhileStackedUsesTheNewHorizontalConfiguration() = onMain {
        val context = context()
        val row = AdaptiveLinearLayout(context)
        val child = TextView(context).apply { text = "Ngân sách" }
        row.addView(child)
        measure(row, 200)
        child.layoutParams = LinearLayout.LayoutParams(0, 40, 3f).apply {
            marginStart = 19
        }
        measure(row, 200)
        assertEquals(0f, (child.layoutParams as LinearLayout.LayoutParams).weight, 0f)
        measure(row, 600)
        val restored = child.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, restored.width)
        assertEquals(40, restored.height)
        assertEquals(3f, restored.weight, 0f)
        assertEquals(19, restored.marginStart)
    }
}
