package com.nhom9.quanlychitieu.ui.budget

import android.os.Bundle
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nhom9.quanlychitieu.R
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.model.SpendingCaps
import com.nhom9.quanlychitieu.model.VndAmount
import com.nhom9.quanlychitieu.ui.components.BottomNavHelper
import com.nhom9.quanlychitieu.ui.components.UiChrome

class BudgetActivity : AppCompatActivity() {

    private val viewModel: BudgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)
        UiChrome.setup(this, R.id.main_budget)
        BottomNavHelper.setup(this, selectedTabIndex = 2)

        setupClickListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        // Listen to Spending Caps Guard Settings
        FinanceRepository.listenToSpendingCaps(applicationContext) { caps ->
            val singleText = if (caps.isSingleCapEnabled) "> ${VndAmount.format(caps.maxSingleTransactionCap)}đ/lần" else "Tắt"
            val dailyText = if (caps.isDailyCapEnabled) "> ${VndAmount.format(caps.maxDailyCap)}đ/ngày" else "Tắt"
            findViewById<TextView>(R.id.txtSpendingCapSubtitle)?.text = "Cảnh báo: $singleText | $dailyText"
        }

        viewModel.state.observe(this) { state ->
            findViewById<TextView>(R.id.txtOverallTotalLimit)?.text = "${VndAmount.format(state.totalLimit)} VNĐ"
            findViewById<TextView>(R.id.txtOverallSpentSummary)?.text =
                "Đã chi ${VndAmount.format(state.totalSpent)} VNĐ | Trần: ${VndAmount.format(state.totalLimit)} VNĐ"

            val overallPercent = if (state.totalLimit > 0) ((state.totalSpent.toDouble() / state.totalLimit) * 100).toInt().coerceIn(0, 100) else 0
            findViewById<TextView>(R.id.txtOverallPercentSpent)?.text = "$overallPercent% Đã chi"
            findViewById<ProgressBar>(R.id.progressOverall)?.progress = overallPercent

            findViewById<TextView>(R.id.txtSafeRemaining)?.text = "${VndAmount.format(state.safeRemaining)} VNĐ"
            findViewById<TextView>(R.id.txtDailyAllowance)?.text = "~${VndAmount.format(state.dailyAllowance)} VNĐ / ngày"
            findViewById<TextView>(R.id.txtRemainingDays)?.text = "${state.remainingDays} ngày"

            // Dynamic Category Budget Sync
            state.budgets.forEach { budget ->
                when {
                    budget.categoryName.contains("Ăn", ignoreCase = true) -> {
                        findViewById<TextView>(R.id.txtBudgetLimitFood)?.text = "Hạn mức tháng: ${VndAmount.format(budget.limitAmount)} VNĐ"
                        findViewById<TextView>(R.id.txtBudgetSpentFood)?.text = VndAmount.format(budget.spentAmount)
                        findViewById<TextView>(R.id.txtBudgetStatusFood)?.text = budget.statusTag
                        findViewById<ProgressBar>(R.id.progressBudgetFood)?.progress = budget.progressPercentage
                    }
                    budget.categoryName.contains("chuyển", ignoreCase = true) -> {
                        findViewById<TextView>(R.id.txtBudgetLimitTransport)?.text = "Hạn mức tháng: ${VndAmount.format(budget.limitAmount)} VNĐ"
                        findViewById<TextView>(R.id.txtBudgetSpentTransport)?.text = VndAmount.format(budget.spentAmount)
                        findViewById<TextView>(R.id.txtBudgetStatusTransport)?.text = budget.statusTag
                        findViewById<ProgressBar>(R.id.progressBudgetTransport)?.progress = budget.progressPercentage
                    }
                    budget.categoryName.contains("Thuê", ignoreCase = true) || budget.categoryName.contains("nhà", ignoreCase = true) -> {
                        findViewById<TextView>(R.id.txtBudgetLimitHouse)?.text = "Hạn mức tháng: ${VndAmount.format(budget.limitAmount)} VNĐ"
                        findViewById<TextView>(R.id.txtBudgetSpentHouse)?.text = VndAmount.format(budget.spentAmount)
                        findViewById<TextView>(R.id.txtBudgetStatusHouse)?.text = budget.statusTag
                        findViewById<ProgressBar>(R.id.progressBudgetHouse)?.progress = budget.progressPercentage
                    }
                    budget.categoryName.contains("Mua", ignoreCase = true) || budget.categoryName.contains("Giải", ignoreCase = true) -> {
                        findViewById<TextView>(R.id.txtBudgetLimitShopping)?.text = "Hạn mức tháng: ${VndAmount.format(budget.limitAmount)} VNĐ"
                        findViewById<TextView>(R.id.txtBudgetSpentShopping)?.text = VndAmount.format(budget.spentAmount)
                        findViewById<TextView>(R.id.txtBudgetStatusShopping)?.text = budget.statusTag
                        findViewById<ProgressBar>(R.id.progressBudgetShopping)?.progress = budget.progressPercentage
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Spending Caps Config Button
        findViewById<android.view.View>(R.id.btnConfigSpendingCaps)?.setOnClickListener {
            showSpendingCapsDialog()
        }

        // Add New Budget Category Button in Category Groups List Header
        findViewById<android.view.View>(R.id.btnAddBudget)?.setOnClickListener {
            showAddBudgetBottomSheet()
        }

        // Individual Category Items -> Edit Category Budget
        findViewById<android.view.View>(R.id.itemBudgetFood)?.setOnClickListener {
            showEditBudgetBottomSheet("b1", "Ăn uống & Cà phê", "4.000.000", "3.100.000 VNĐ (77%)")
        }

        findViewById<android.view.View>(R.id.itemBudgetTransport)?.setOnClickListener {
            showEditBudgetBottomSheet("b2", "Di chuyển & Xăng xe", "2.000.000", "950.000 VNĐ (47%)")
        }

        findViewById<android.view.View>(R.id.itemBudgetHouse)?.setOnClickListener {
            showEditBudgetBottomSheet("b3", "Thuê nhà & Hóa đơn", "4.000.000", "4.000.000 VNĐ (100% - Đạt trần)")
        }

        findViewById<android.view.View>(R.id.itemBudgetShopping)?.setOnClickListener {
            showEditBudgetBottomSheet("b4", "Mua sắm & Giải trí", "2.500.000", "1.200.000 VNĐ (48%)")
        }

        findViewById<android.view.View>(R.id.btnAddSavingsGoal)?.setOnClickListener {
            Toast.makeText(this, "Mở form tạo Hũ tiết kiệm / Mục tiêu tích lũy mới", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.btnDepositJapan)?.setOnClickListener {
            viewModel.depositToSavingsGoal("g1", 1000000L) {
                Toast.makeText(this, "Đã nạp +1.000.000 VNĐ vào hũ Du lịch Nhật Bản trên Firebase!", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<android.view.View>(R.id.btnDepositEmergency)?.setOnClickListener {
            viewModel.depositToSavingsGoal("g2", 2000000L) {
                Toast.makeText(this, "Đã nạp +2.000.000 VNĐ vào hũ Dự phòng khẩn cấp trên Firebase!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSpendingCapsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.layout_dialog_spending_caps, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val switchSingle = dialogView.findViewById<SwitchCompat>(R.id.switchSingleCap)
        val edtSingleMax = dialogView.findViewById<EditText>(R.id.edtSingleMaxCap)
        val switchDaily = dialogView.findViewById<SwitchCompat>(R.id.switchDailyCap)
        val edtDailyMax = dialogView.findViewById<EditText>(R.id.edtDailyMaxCap)

        edtSingleMax?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) edtSingleMax.setText("")
        }
        edtDailyMax?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) edtDailyMax.setText("")
        }

        // Pre-fill current active caps from local cache
        val caps = FinanceRepository.getSpendingCaps(applicationContext)
        switchSingle?.isChecked = caps.isSingleCapEnabled
        edtSingleMax?.setText(VndAmount.format(caps.maxSingleTransactionCap))
        switchDaily?.isChecked = caps.isDailyCapEnabled
        edtDailyMax?.setText(VndAmount.format(caps.maxDailyCap))

        dialogView.findViewById<android.view.View>(R.id.btnSaveCaps)?.setOnClickListener {
            val singleOn = switchSingle?.isChecked ?: true
            val singleVal = (VndAmount.parse(edtSingleMax?.text.toString()) ?: 2000000L).coerceAtMost(1000000000L)
            val dailyOn = switchDaily?.isChecked ?: true
            val dailyVal = (VndAmount.parse(edtDailyMax?.text.toString()) ?: 1000000L).coerceAtMost(1000000000L)

            val newCaps = SpendingCaps(
                isSingleCapEnabled = singleOn,
                maxSingleTransactionCap = singleVal,
                isDailyCapEnabled = dailyOn,
                maxDailyCap = dailyVal
            )

            FinanceRepository.saveSpendingCaps(applicationContext, newCaps) { success ->
                if (success) {
                    Toast.makeText(this, "Đã lưu cấu hình hạn mức bảo vệ!", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btnCancelCaps)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddBudgetBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_bottom_sheet_add_budget, null)
        dialog.setContentView(dialogView)

        val edtCatName = dialogView.findViewById<EditText>(R.id.edtCategoryName)

        val chipFood = dialogView.findViewById<TextView>(R.id.chipFood)
        val chipTransport = dialogView.findViewById<TextView>(R.id.chipTransport)
        val chipShopping = dialogView.findViewById<TextView>(R.id.chipShopping)
        val chipHouse = dialogView.findViewById<TextView>(R.id.chipHouse)
        val chipEntertainment = dialogView.findViewById<TextView>(R.id.chipEntertainment)
        val chipHealth = dialogView.findViewById<TextView>(R.id.chipHealth)
        val chipCustom = dialogView.findViewById<TextView>(R.id.chipCustom)

        val presetChips = listOf(chipFood, chipTransport, chipShopping, chipHouse, chipEntertainment, chipHealth)

        fun updateChipsSelection(activeText: String) {
            presetChips.forEach { c ->
                val isMatched = c != null && c.text.toString().trim().equals(activeText.trim(), ignoreCase = true)
                c?.background = ContextCompat.getDrawable(
                    this,
                    if (isMatched) R.drawable.bg_category_active else R.drawable.bg_category_inactive
                )
                c?.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (isMatched) R.color.text_primary else R.color.text_secondary
                    )
                )
            }
        }

        edtCatName?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val typedText = s?.toString()?.trim() ?: ""
                updateChipsSelection(typedText)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        presetChips.forEach { chip ->
            chip?.setOnClickListener {
                val catText = chip.text.toString()
                edtCatName?.setText(catText)
                edtCatName?.setSelection(edtCatName.text.length)
            }
        }

        chipCustom?.setOnClickListener {
            edtCatName?.setText("")
            edtCatName?.requestFocus()
        }

        val edtLimit = dialogView.findViewById<EditText>(R.id.edtBudgetLimit)

        dialogView.findViewById<TextView>(R.id.btnLimit2M)?.setOnClickListener {
            edtLimit?.setText("2.000.000")
        }
        dialogView.findViewById<TextView>(R.id.btnLimit5M)?.setOnClickListener {
            edtLimit?.setText("5.000.000")
        }
        dialogView.findViewById<TextView>(R.id.btnLimit10M)?.setOnClickListener {
            edtLimit?.setText("10.000.000")
        }

        dialogView.findViewById<android.view.View>(R.id.btnCloseSheet)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btnSaveNewBudget)?.setOnClickListener {
            val categoryName = edtCatName?.text.toString().trim().ifEmpty { "Danh mục mới" }
            val amountStr = edtLimit?.text.toString().replace(".", "").trim()
            val amountVal = amountStr.toLongOrNull() ?: 5000000L

            viewModel.saveNewBudget(categoryName, amountVal) {
                Toast.makeText(
                    this,
                    "Đã lưu ngân sách '$categoryName' hạn mức ${VndAmount.format(amountVal)} VNĐ lên Firebase!",
                    Toast.LENGTH_LONG
                ).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditBudgetBottomSheet(budgetId: String, categoryName: String, currentLimit: String, spentInfo: String) {
        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_bottom_sheet_edit_budget, null)
        dialog.setContentView(dialogView)

        dialogView.findViewById<TextView>(R.id.txtEditCategoryTitle)?.text = "Chỉnh sửa ngân sách $categoryName"
        dialogView.findViewById<TextView>(R.id.txtCurrentSpentInfo)?.text = "Đã chi tiêu tháng này: $spentInfo"

        val edtLimit = dialogView.findViewById<EditText>(R.id.edtEditBudgetLimit)
        edtLimit?.setText(currentLimit)

        fun adjustLimit(addValue: Long) {
            val currentStr = edtLimit?.text.toString().replace(".", "").trim()
            val currentVal = currentStr.toLongOrNull() ?: 0L
            val newVal = currentVal + addValue
            edtLimit?.setText(String.format("%,d", newVal).replace(",", "."))
        }

        dialogView.findViewById<TextView>(R.id.btnPlus500k)?.setOnClickListener { adjustLimit(500000L) }
        dialogView.findViewById<TextView>(R.id.btnPlus1M)?.setOnClickListener { adjustLimit(1000000L) }
        dialogView.findViewById<TextView>(R.id.btnPlus10Percent)?.setOnClickListener {
            val currentStr = edtLimit?.text.toString().replace(".", "").trim()
            val currentVal = currentStr.toLongOrNull() ?: 0L
            val newVal = (currentVal * 1.10).toLong()
            edtLimit?.setText(String.format("%,d", newVal).replace(",", "."))
        }

        dialogView.findViewById<android.view.View>(R.id.btnCloseEditSheet)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btnUpdateBudget)?.setOnClickListener {
            val newLimitStr = edtLimit?.text.toString().replace(".", "").trim()
            val newLimitVal = newLimitStr.toLongOrNull() ?: 4000000L

            viewModel.updateBudgetLimit(budgetId, categoryName, newLimitVal) {
                Toast.makeText(
                    this,
                    "Đã cập nhật hạn mức ngân sách $categoryName thành ${VndAmount.format(newLimitVal)} VNĐ lên Firebase!",
                    Toast.LENGTH_LONG
                ).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        BottomNavHelper.setup(this, selectedTabIndex = 2)
        viewModel.loadData()
    }
}