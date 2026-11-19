package com.nhom9.quanlychitieu.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import com.nhom9.quanlychitieu.ui.components.BottomNavHelper
import com.nhom9.quanlychitieu.ui.components.UiChrome
import com.nhom9.quanlychitieu.ui.components.TransactionSyncBanner
import com.nhom9.quanlychitieu.ui.components.WaveChartView
import com.nhom9.quanlychitieu.ui.transaction.TransactionHistoryActivity
import com.nhom9.quanlychitieu.ui.transaction.TransactionDetailActivity
import com.nhom9.quanlychitieu.ui.wallet.WalletActivity
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UiChrome.setup(this, R.id.main)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.savedTransactionsHeading), true)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnRetryTransactions)?.setOnClickListener { viewModel.retry() }

        findViewById<View>(R.id.layoutNotification)?.setOnClickListener {
            android.widget.Toast.makeText(this, "Thông báo: Ngân sách tháng này của bạn đang rất an toàn!", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnViewAllHistory)?.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }

        findViewById<View>(R.id.btnManageWalletsHome).setOnClickListener {
            startActivity(Intent(this, WalletActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            TransactionSyncBanner.bind(findViewById(R.id.transactionSyncBanner), state.pendingCount,
                state.syncFailed, state.loadFailed) { viewModel.retry() }
            findViewById<TextView>(R.id.txtNetBalance)?.text = "${VndAmount.format(state.netBalance)} VNĐ"
            findViewById<TextView>(R.id.txtMonthlyExpense)?.text = "${VndAmount.format(state.monthlyExpense)} VNĐ"
            findViewById<TextView>(R.id.txtRemainingLimit)?.text = "${VndAmount.format(state.remainingLimit)} VNĐ"

            // Wave Chart
            findViewById<WaveChartView>(R.id.waveChartView)?.setWaveData(state.totalIncome, state.monthlyExpense)

            // Dynamic 4 Category Cards Updates
            findViewById<TextView>(R.id.txtCatAmountTransport)?.text = "${VndAmount.format(state.transportSpent)} VNĐ"
            findViewById<TextView>(R.id.txtCatPercentTransport)?.text = "${state.transportPercent}%"
            findViewById<ProgressBar>(R.id.progressCatTransport)?.progress = state.transportPercent

            findViewById<TextView>(R.id.txtCatAmountDigital)?.text = "${VndAmount.format(state.digitalSpent)} VNĐ"
            findViewById<TextView>(R.id.txtCatPercentDigital)?.text = "${state.digitalPercent}%"
            findViewById<ProgressBar>(R.id.progressCatDigital)?.progress = state.digitalPercent

            findViewById<TextView>(R.id.txtCatAmountRent)?.text = "${VndAmount.format(state.rentSpent)} VNĐ"
            findViewById<TextView>(R.id.txtCatPercentRent)?.text = "${state.rentPercent}%"
            findViewById<ProgressBar>(R.id.progressCatRent)?.progress = state.rentPercent

            findViewById<TextView>(R.id.txtCatAmountGame)?.text = "${VndAmount.format(state.gameSpent)} VNĐ"
            findViewById<TextView>(R.id.txtCatPercentGame)?.text = "${state.gamePercent}%"
            findViewById<ProgressBar>(R.id.progressCatGame)?.progress = state.gamePercent

            renderTransactions(state.recentTransactions, state.wallets)
        }
    }

    override fun onResume() {
        super.onResume()
        BottomNavHelper.setup(this, selectedTabIndex = 0)
        viewModel.loadData()
    }

    private fun renderTransactions(transactions: List<SavedTransaction>, wallets: List<Wallet>) {
        val list = findViewById<LinearLayout>(R.id.savedTransactionList) ?: return
        list.removeAllViews()
        findViewById<TextView>(R.id.savedTransactionStatus)?.apply {
            setText(R.string.ui_transactions_empty)
            visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
        }
        val dateFormat = SimpleDateFormat(
            getString(R.string.ui_transaction_date_time_pattern),
            Locale.forLanguageTag("vi-VN")
        )

        transactions.forEach { transaction ->
            val row = layoutInflater.inflate(R.layout.item_saved_transaction, list, false)
            val (typeLabel, amountFormat, color) = when (transaction.type) {
                TransactionType.EXPENSE -> Triple(
                    R.string.ui_transaction_type_expense,
                    R.string.ui_transaction_amount_expense,
                    R.color.red_expense
                )
                TransactionType.INCOME -> Triple(
                    R.string.ui_transaction_type_income,
                    R.string.ui_transaction_amount_income,
                    R.color.green_accent
                )
                TransactionType.TRANSFER -> Triple(
                    R.string.ui_transaction_type_transfer,
                    R.string.ui_transaction_amount_transfer,
                    R.color.cyan_accent
                )
            }
            val type = getString(typeLabel)
            row.findViewById<TextView>(R.id.savedTransactionTitle).text =
                if (transaction.type == TransactionType.TRANSFER) type
                else getString(R.string.ui_transaction_title_category, type, transaction.category)
            row.findViewById<TextView>(R.id.savedTransactionAmount).apply {
                text = getString(amountFormat, VndAmount.format(transaction.amount))
                setTextColor(ContextCompat.getColor(context, color))
            }
            val source = WalletBalance.name(transaction.sourceWallet, wallets)
            val wallet = if (transaction.type == TransactionType.TRANSFER) {
                val destination = WalletBalance.name(transaction.destinationWallet, wallets)
                getString(R.string.ui_transaction_wallet_transfer, source, destination)
            } else source
            val detailsFormat = if (transaction.excludeFromBudget)
                R.string.ui_transaction_details_excluded else R.string.ui_transaction_details
            row.findViewById<TextView>(R.id.savedTransactionDetails).text = getString(
                detailsFormat, wallet, dateFormat.format(transaction.occurredAt)
            )
            row.findViewById<TextView>(R.id.savedTransactionNote).apply {
                text = transaction.note
                visibility = if (transaction.note.isBlank()) View.GONE else View.VISIBLE
            }

            ViewCompat.replaceAccessibilityAction(row,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.transaction_open_details), null)
            row.setOnClickListener {
                startActivity(TransactionDetailActivity.intent(this, transaction.id))
            }

            list.addView(row)
        }
    }
}
