package com.nhom9.quanlychitieu.ui.components

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import com.nhom9.quanlychitieu.R

/** Keeps unfinished controls readable and discoverable without promising an action. */
object UnavailableFeature {
    fun mark(view: View?, @StringRes labelRes: Int) {
        if (view == null) return
        disable(view)
        view.contentDescription = view.context.getString(
            R.string.ui_feature_coming_soon, view.context.getString(labelRes)
        )
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setScreenReaderFocusable(view, true)
        ViewCompat.setStateDescription(view, view.context.getString(R.string.ui_feature_unavailable))
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                view.getChildAt(index).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
    }

    private fun disable(view: View) {
        view.setOnClickListener(null)
        view.isClickable = false
        view.isLongClickable = false
        view.isFocusable = false
        view.isEnabled = false
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) disable(view.getChildAt(index))
        }
    }
}
