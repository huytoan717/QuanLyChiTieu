package com.nhom9.quanlychitieu.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.data.TransactionSnapshot
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.WalletBalance
import com.nhom9.quanlychitieu.ui.components.BottomNavHelper
import com.nhom9.quanlychitieu.ui.components.UiChrome
import com.nhom9.quanlychitieu.ui.wallet.EditWalletActivity
import com.nhom9.quanlychitieu.ui.wallet.WalletActivity
import com.nhom9.quanlychitieu.ui.wallet.showWalletRows

class AccountActivity : AppCompatActivity() {
    private var observation: AutoCloseable? = null
    private var openingWalletScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        UiChrome.setup(this, R.id.main_account)
        BottomNavHelper.setup(this, selectedTabIndex = 3)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.txtAccountWalletHeading), true)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<android.view.View>(R.id.btnLogout)?.setOnClickListener {
            Toast.makeText(this, "Đã đăng xuất khỏi tài khoản cá nhân", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnExportData)?.setOnClickListener {
            Toast.makeText(this, "Xuất báo cáo giao dịch ra file Excel / PDF", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnCloudSync)?.setOnClickListener {
            Toast.makeText(this, "Đồng bộ dữ liệu tài chính lên Cloud thành công", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnEditProfile)?.setOnClickListener {
            Toast.makeText(this, "Chỉnh sửa thông tin hồ sơ cá nhân", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnAddWallet).setOnClickListener {
            openWalletScreen(EditWalletActivity::class.java)
        }
        findViewById<View>(R.id.btnManageWallets).setOnClickListener {
            openWalletScreen(WalletActivity::class.java)
        }
    }

    override fun onStart() {
        super.onStart()
        observation = TransactionRepository.get(this).observe(::renderWallets)
    }

    override fun onResume() {
        super.onResume()
        openingWalletScreen = false
        BottomNavHelper.setup(this, selectedTabIndex = 3)
    }

    override fun onStop() {
        observation?.close()
        observation = null
        super.onStop()
    }

    private fun renderWallets(snapshot: TransactionSnapshot) {
        val wallets = if (snapshot.loadFailed) emptyList() else snapshot.wallets
        val status = when {
            snapshot.loadFailed -> R.string.wallet_load_failed
            wallets.isEmpty() -> R.string.wallet_empty
            else -> null
        }
        findViewById<TextView>(R.id.txtAccountWalletStatus).apply {
            isVisible = status != null
            status?.let { setText(it) }
        }
        findViewById<TextView>(R.id.txtWalletTotal).text = if (snapshot.loadFailed) {
            getString(R.string.wallet_total_unavailable)
        } else {
            getString(R.string.ui_transaction_amount_transfer, VndAmount.format(WalletBalance.total(wallets)))
        }
        findViewById<View>(R.id.btnAddWallet).apply {
            isEnabled = !snapshot.loadFailed
            alpha = if (isEnabled) 1f else 0.54f
        }
        findViewById<LinearLayout>(R.id.accountWalletList).apply {
            isVisible = wallets.isNotEmpty()
            showWalletRows(this, wallets)
        }
    }

    private fun openWalletScreen(activity: Class<out AppCompatActivity>) {
        if (openingWalletScreen) return
        openingWalletScreen = true
        startActivity(Intent(this, activity))
    }
}
