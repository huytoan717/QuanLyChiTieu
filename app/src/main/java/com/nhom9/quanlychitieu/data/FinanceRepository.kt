package com.nhom9.quanlychitieu.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nhom9.quanlychitieu.model.Budget
import com.nhom9.quanlychitieu.model.CapCheckResult
import com.nhom9.quanlychitieu.model.Category
import com.nhom9.quanlychitieu.model.SavingsGoal
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.SpendingCaps
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.VndAmount
import java.util.Calendar

object FinanceRepository {

    private const val TAG = "FinanceRepository"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val authWaiters = mutableListOf<(String?) -> Unit>()
    private val seedRequested = mutableSetOf<String>()

    private fun seedOnce(uid: String) {
        if (seedRequested.add(uid)) seedInitialDataIfEmpty(uid)
    }

    fun ensureAuthenticated(onComplete: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            seedOnce(currentUser.uid)
            onComplete(currentUser.uid)
        } else {
            authWaiters.add(onComplete)
            if (authWaiters.size > 1) return
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    Log.d(TAG, "Anonymous Auth successful: $uid")
                    if (uid != null) seedOnce(uid)
                    val callbacks = authWaiters.toList()
                    authWaiters.clear()
                    callbacks.forEach { it(uid) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Auth failed: ${e.message}", e)
                    val callbacks = authWaiters.toList()
                    authWaiters.clear()
                    callbacks.forEach { it(null) }
                }
        }
    }

    private fun seedInitialDataIfEmpty(uid: String) {
        val userDoc = db.collection("users").document(uid)
        userDoc.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                userDoc.set(
                    hashMapOf(
                        "name" to "Toan Nguyen",
                        "email" to "toan.nguyen@quanlychitieu.com",
                        "createdAt" to System.currentTimeMillis()
                    )
                )

                // Initial Budgets with unique Category IDs
                val budgets = listOf(
                    Budget("b1", Category.FOOD.id, "Ăn uống & Cà phê", 4000000L, 3100000L, "MONTHLY", "77% (Cận trần)"),
                    Budget("b2", Category.TRANSPORT.id, "Di chuyển & Xăng xe", 2000000L, 950000L, "MONTHLY", "47% (An toàn)"),
                    Budget("b3", Category.HOUSING.id, "Thuê nhà & Hóa đơn", 4000000L, 4000000L, "MONTHLY", "100% (Đạt mức trần)"),
                    Budget("b4", Category.SHOPPING.id, "Mua sắm & Giải trí", 2500000L, 1200000L, "MONTHLY", "48% (Thong thả)")
                )
                budgets.forEach { b -> userDoc.collection("budgets").document(b.id).set(b) }

                // Initial Savings Goals
                val goals = listOf(
                    SavingsGoal("g1", "Du lịch Nhật Bản 🌸", 30000000L, 18500000L, "Tháng 04/2025", "ic_plane"),
                    SavingsGoal("g2", "Dự phòng khẩn cấp 🛡", 50000000L, 42000000L, "6 tháng sinh hoạt", "ic_shield")
                )
                goals.forEach { g -> userDoc.collection("savings_goals").document(g.id).set(g) }

                // Wallet opening balances and transactions are entered by the user.
            }
        }
    }

    // --- TRANSACTIONS API ---

    fun saveTransaction(context: Context, transaction: SavedTransaction, onComplete: ((Boolean) -> Unit)? = null) {
        TransactionRepository.get(context).create(transaction) { onComplete?.invoke(it.isSuccess) }
    }

    fun listenToTransactions(context: Context, onDataChanged: (List<SavedTransaction>) -> Unit): AutoCloseable =
        TransactionRepository.get(context).observe { snapshot ->
            if (!snapshot.loadFailed) onDataChanged(snapshot.transactions)
        }

    // --- BUDGETS API ---

    fun saveBudget(budget: Budget, onComplete: ((Boolean) -> Unit)? = null) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) {
                onComplete?.invoke(false)
                return@ensureAuthenticated
            }

            db.collection("users")
                .document(uid)
                .collection("budgets")
                .document(budget.id)
                .set(budget)
                .addOnSuccessListener { onComplete?.invoke(true) }
                .addOnFailureListener { onComplete?.invoke(false) }
        }
    }

    fun listenToBudgets(onBudgetsChanged: (List<Budget>) -> Unit) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("budgets")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val catName = doc.getString("categoryName") ?: ""
                                val catId = doc.getString("categoryId") ?: Category.fromName(catName).id
                                Budget(
                                    id = doc.getString("id") ?: doc.id,
                                    categoryId = catId,
                                    categoryName = catName,
                                    limitAmount = doc.getLong("limitAmount") ?: 0L,
                                    spentAmount = doc.getLong("spentAmount") ?: 0L,
                                    period = doc.getString("period") ?: "MONTHLY",
                                    statusTag = doc.getString("statusTag") ?: "An toàn"
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        onBudgetsChanged(list)
                    }
                }
        }
    }

    // --- SAVINGS GOALS API ---

    fun depositToSavingsGoal(goalId: String, addAmount: Long, onComplete: ((Boolean) -> Unit)? = null) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("savings_goals")
                .document(goalId)
                .update("currentAmount", FieldValue.increment(addAmount))
                .addOnSuccessListener { onComplete?.invoke(true) }
                .addOnFailureListener { onComplete?.invoke(false) }
        }
    }

    fun listenToSavingsGoals(onGoalsChanged: (List<SavingsGoal>) -> Unit) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("savings_goals")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                SavingsGoal(
                                    id = doc.getString("id") ?: doc.id,
                                    title = doc.getString("title") ?: "",
                                    targetAmount = doc.getLong("targetAmount") ?: 0L,
                                    currentAmount = doc.getLong("currentAmount") ?: 0L,
                                    targetDate = doc.getString("targetDate") ?: "",
                                    iconName = doc.getString("iconName") ?: "ic_plane"
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        onGoalsChanged(list)
                    }
                }
        }
    }

    // --- FINANCIAL CALCULATIONS & ENGINE ---

    fun calculateTotalIncome(transactions: List<SavedTransaction>): Long {
        return transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    fun calculateTotalExpense(transactions: List<SavedTransaction>): Long {
        return transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    fun calculateNetBalance(transactions: List<SavedTransaction>, baseBalance: Long = 0L): Long {
        val income = calculateTotalIncome(transactions)
        val expense = calculateTotalExpense(transactions)
        return baseBalance + income - expense
    }

    fun calculateCurrentMonthExpense(transactions: List<SavedTransaction>): Long {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val txCal = Calendar.getInstance()

        val monthSpent = transactions.filter {
            it.type == TransactionType.EXPENSE && !it.excludeFromBudget
        }.filter {
            txCal.timeInMillis = it.occurredAt
            txCal.get(Calendar.MONTH) == currentMonth && txCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.amount }

        return monthSpent
    }

    fun calculateCategoryMonthlySpent(transactions: List<SavedTransaction>, categoryNameOrId: String): Long {
        val category = if (categoryNameOrId.startsWith("cat_")) {
            Category.fromId(categoryNameOrId)
        } else {
            Category.fromName(categoryNameOrId)
        }

        val now = Calendar.getInstance()
        val date = Calendar.getInstance()
        return transactions.filter { it.type == TransactionType.EXPENSE && !it.excludeFromBudget }
            .filter {
                date.timeInMillis = it.occurredAt
                date.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    date.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    it.categoryId == category.id
            }.sumOf { it.amount }
    }

    // --- SPENDING CAPS & DAILY GUARD API ---

    fun calculateTodayExpense(transactions: List<SavedTransaction>): Long {
        return calculateExpenseOnDate(transactions, System.currentTimeMillis())
    }

    private fun calculateExpenseOnDate(transactions: List<SavedTransaction>, occurredAt: Long): Long {
        val now = Calendar.getInstance().apply { timeInMillis = occurredAt }
        val today = now.get(Calendar.DAY_OF_YEAR)
        val year = now.get(Calendar.YEAR)

        val txCal = Calendar.getInstance()
        return transactions.filter { it.type == TransactionType.EXPENSE }
            .filter {
                txCal.timeInMillis = it.occurredAt
                txCal.get(Calendar.DAY_OF_YEAR) == today && txCal.get(Calendar.YEAR) == year
            }.sumOf { it.amount }
    }

    fun checkCaps(amount: Long, transactions: List<SavedTransaction>, caps: SpendingCaps, occurredAt: Long = System.currentTimeMillis()): CapCheckResult {
        var singleExceeded = false
        var dailyExceeded = false
        var msg: String? = null

        if (caps.isSingleCapEnabled && amount > caps.maxSingleTransactionCap) {
            singleExceeded = true
            msg = "🚨 Cảnh báo: Khoản chi (${VndAmount.format(amount)} VNĐ) vượt hạn mức tối đa 1 giao dịch (${VndAmount.format(caps.maxSingleTransactionCap)} VNĐ)!"
        }

        val todaySpent = calculateExpenseOnDate(transactions, occurredAt)
        val newTotalToday = runCatching { Math.addExact(todaySpent, amount) }.getOrDefault(Long.MAX_VALUE)
        if (caps.isDailyCapEnabled && newTotalToday > caps.maxDailyCap) {
            dailyExceeded = true
            val dailyMsg = "⚠️ Cảnh báo: Tổng chi trong ngày giao dịch sẽ đạt ${VndAmount.format(newTotalToday)} VNĐ, vượt hạn mức ngày (${VndAmount.format(caps.maxDailyCap)} VNĐ)!"
            msg = if (msg != null) "$msg\n$dailyMsg" else dailyMsg
        }

        return CapCheckResult(
            isAllowed = !(singleExceeded || dailyExceeded),
            warningMessage = msg,
            isSingleCapExceeded = singleExceeded,
            isDailyCapExceeded = dailyExceeded
        )
    }

    private var cachedCaps: SpendingCaps? = null

    private fun parseSpendingCaps(snapshot: com.google.firebase.firestore.DocumentSnapshot?): SpendingCaps {
        if (snapshot == null || !snapshot.exists()) return SpendingCaps()
        val isSingle = snapshot.getBoolean("isSingleCapEnabled") ?: snapshot.getBoolean("singleCapEnabled") ?: true
        val singleMax = snapshot.getLong("maxSingleTransactionCap") ?: 2000000L
        val isDaily = snapshot.getBoolean("isDailyCapEnabled") ?: snapshot.getBoolean("dailyCapEnabled") ?: true
        val dailyMax = snapshot.getLong("maxDailyCap") ?: 1000000L
        return SpendingCaps(isSingle, singleMax, isDaily, dailyMax)
    }

    fun saveSpendingCaps(context: Context, caps: SpendingCaps, onComplete: ((Boolean) -> Unit)? = null) {
        cachedCaps = caps
        val prefs = context.getSharedPreferences("spending_caps_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isSingleCapEnabled", caps.isSingleCapEnabled)
            .putLong("maxSingleTransactionCap", caps.maxSingleTransactionCap)
            .putBoolean("isDailyCapEnabled", caps.isDailyCapEnabled)
            .putLong("maxDailyCap", caps.maxDailyCap)
            .apply()

        onComplete?.invoke(true)

        ensureAuthenticated { uid ->
            if (!uid.isNullOrEmpty()) {
                val map = hashMapOf(
                    "isSingleCapEnabled" to caps.isSingleCapEnabled,
                    "maxSingleTransactionCap" to caps.maxSingleTransactionCap,
                    "isDailyCapEnabled" to caps.isDailyCapEnabled,
                    "maxDailyCap" to caps.maxDailyCap
                )
                db.collection("users")
                    .document(uid)
                    .collection("settings")
                    .document("spending_caps")
                    .set(map)
            }
        }
    }

    fun getSpendingCaps(context: Context): SpendingCaps {
        if (cachedCaps != null) return cachedCaps!!
        val prefs = context.getSharedPreferences("spending_caps_prefs", Context.MODE_PRIVATE)
        val caps = SpendingCaps(
            isSingleCapEnabled = prefs.getBoolean("isSingleCapEnabled", true),
            maxSingleTransactionCap = prefs.getLong("maxSingleTransactionCap", 2000000L),
            isDailyCapEnabled = prefs.getBoolean("isDailyCapEnabled", true),
            maxDailyCap = prefs.getLong("maxDailyCap", 1000000L)
        )
        cachedCaps = caps
        return caps
    }

    fun listenToSpendingCaps(context: Context, onCapsChanged: (SpendingCaps) -> Unit) {
        val currentLocal = getSpendingCaps(context)
        onCapsChanged(currentLocal)

        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("settings")
                .document("spending_caps")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val caps = parseSpendingCaps(snapshot)
                        cachedCaps = caps
                        val prefs = context.getSharedPreferences("spending_caps_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("isSingleCapEnabled", caps.isSingleCapEnabled)
                            .putLong("maxSingleTransactionCap", caps.maxSingleTransactionCap)
                            .putBoolean("isDailyCapEnabled", caps.isDailyCapEnabled)
                            .putLong("maxDailyCap", caps.maxDailyCap)
                            .apply()

                        android.os.Handler(android.os.Looper.getMainLooper()).post { onCapsChanged(caps) }
                    }
                }
        }
    }
}
