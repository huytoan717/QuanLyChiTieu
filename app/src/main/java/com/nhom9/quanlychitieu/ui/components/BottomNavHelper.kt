package com.nhom9.quanlychitieu.ui.components

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.ui.account.AccountActivity
import com.nhom9.quanlychitieu.ui.budget.BudgetActivity
import com.nhom9.quanlychitieu.ui.main.MainActivity
import com.nhom9.quanlychitieu.ui.report.ReportActivity
import com.nhom9.quanlychitieu.ui.transaction.AddTransactionActivity

object BottomNavHelper {

    fun setup(activity: Activity, selectedTabIndex: Int) {
        val bottomNav = activity.findViewById<View>(R.id.bottom_nav_container) ?: return

        val cyanColor = ContextCompat.getColor(activity, R.color.cyan_accent)
        val mutedColor = ContextCompat.getColor(activity, R.color.text_muted)

        val btnHome = bottomNav.findViewById<View>(R.id.btnTabHome)
        val imgHome = bottomNav.findViewById<ImageView>(R.id.imgTabHome)
        val txtHome = bottomNav.findViewById<TextView>(R.id.txtTabHome)

        val btnReport = bottomNav.findViewById<View>(R.id.btnTabReport)
        val imgReport = bottomNav.findViewById<ImageView>(R.id.imgTabReport)
        val txtReport = bottomNav.findViewById<TextView>(R.id.txtTabReport)

        val btnBudget = bottomNav.findViewById<View>(R.id.btnTabBudget)
        val imgBudget = bottomNav.findViewById<ImageView>(R.id.imgTabBudget)
        val txtBudget = bottomNav.findViewById<TextView>(R.id.txtTabBudget)

        val btnAccount = bottomNav.findViewById<View>(R.id.btnTabAccount)
        val imgAccount = bottomNav.findViewById<ImageView>(R.id.imgTabAccount)
        val txtAccount = bottomNav.findViewById<TextView>(R.id.txtTabAccount)

        val btnFabAdd = bottomNav.findViewById<View>(R.id.btnFabAdd)

        // Helper to update a single tab's state
        fun setTabState(button: View?, img: ImageView?, txt: TextView?, isSelected: Boolean) {
            val color = if (isSelected) cyanColor else mutedColor
            val style = if (isSelected) Typeface.BOLD else Typeface.NORMAL

            img?.imageTintList = ColorStateList.valueOf(color)
            txt?.setTextColor(color)
            txt?.setTypeface(null, style)
            button?.apply {
                this.isSelected = isSelected
                contentDescription = txt?.text
                ViewCompat.setScreenReaderFocusable(this, true)
                ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Button"
                        info.isSelected = host.isSelected
                    }
                })
            }
        }

        // Apply visual active/inactive states
        setTabState(btnHome, imgHome, txtHome, selectedTabIndex == 0)
        setTabState(btnReport, imgReport, txtReport, selectedTabIndex == 1)
        setTabState(btnBudget, imgBudget, txtBudget, selectedTabIndex == 2)
        setTabState(btnAccount, imgAccount, txtAccount, selectedTabIndex == 3)

        // FAB Add Transaction Click Handler
        btnFabAdd?.setOnClickListener {
            val intent = Intent(activity, AddTransactionActivity::class.java)
            activity.startActivity(intent)
        }
        btnFabAdd?.let {
            ViewCompat.setAccessibilityDelegate(it, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Button"
                }
            })
        }

        // Navigation Click Handlers
        btnHome?.setOnClickListener {
            if (selectedTabIndex != 0) {
                val intent = Intent(activity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
            }
        }

        btnReport?.setOnClickListener {
            if (selectedTabIndex != 1) {
                val intent = Intent(activity, ReportActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
            }
        }

        btnBudget?.setOnClickListener {
            if (selectedTabIndex != 2) {
                val intent = Intent(activity, BudgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
            }
        }

        btnAccount?.setOnClickListener {
            if (selectedTabIndex != 3) {
                val intent = Intent(activity, AccountActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(intent)
            }
        }
    }
}
