package com.nhom9.quanlychitieu.ui.components

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nhom9.quanlychitieu.R

/** Call once, after setContentView. The app uses a dark surface in both OS themes. */
object UiChrome {
    fun setup(activity: AppCompatActivity, @IdRes rootId: Int, includeIme: Boolean = false) {
        val surface = ContextCompat.getColor(activity, R.color.bg_main_dark)
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(surface),
            navigationBarStyle = SystemBarStyle.dark(surface)
        )
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }

        val root = activity.findViewById<android.view.View>(rootId)
        val originalLeft = root.paddingLeft
        val originalTop = root.paddingTop
        val originalRight = root.paddingRight
        val originalBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = if (includeIme) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            view.setPadding(
                originalLeft + safe.left,
                originalTop + safe.top,
                originalRight + safe.right,
                originalBottom + maxOf(safe.bottom, keyboard)
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
