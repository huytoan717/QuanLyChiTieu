package com.nhom9.quanlychitieu.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nhom9.quanlychitieu.model.FirebaseBudget
import com.nhom9.quanlychitieu.model.FirebaseSavingsGoal
import com.nhom9.quanlychitieu.model.FirebaseWallet
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType

object FirebaseRepository {

    private const val TAG = "FirebaseRepository"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun ensureAuthenticated(onComplete: (String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            onComplete(currentUser.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    Log.d(TAG, "Anonymous Auth successful: $uid")
                    if (uid != null) seedInitialDataIfEmpty(uid)
                    onComplete(uid)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Auth failed: ${e.message}", e)
                    onComplete(null)
                }
        }
    }

    private fun seedInitialDataIfEmpty(uid: String) {
        val userDoc = db.collection("users").document(uid)
        userDoc.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // Seed initial profile
                userDoc.set(
                    hashMapOf(
                        "name" to "Toan Nguyen",
                        "email" to "toan.nguyen@quanlychitieu.com",
                        "createdAt" to System.currentTimeMillis()
                    )
                )

                // Seed initial Budgets
                val budgets = listOf(
                    FirebaseBudget("b1", "Ăn uống & Cà phê", 4000000L, 3100000L, "MONTHLY", "77% (Cận trần)"),
                    FirebaseBudget("b2", "Di chuyển & Xăng xe", 2000000L, 950000L, "MONTHLY", "47% (An toàn)"),
                    FirebaseBudget("b3", "Thuê nhà & Hóa đơn", 4000000L, 4000000L, "MONTHLY", "100% (Đạt mức trần)"),
                    FirebaseBudget("b4", "Mua sắm & Giải trí", 2500000L, 1200000L, "MONTHLY", "48% (Thong thả)")
                )
                budgets.forEach { b ->
                    userDoc.collection("budgets").document(b.id).set(b)
                }

                // Seed initial Savings Goals
                val goals = listOf(
                    FirebaseSavingsGoal("g1", "Du lịch Nhật Bản 🌸", 30000000L, 18500000L, "Tháng 04/2025", "ic_plane"),
                    FirebaseSavingsGoal("g2", "Dự phòng khẩn cấp 🛡", 50000000L, 42000000L, "6 tháng sinh hoạt", "ic_shield")
                )
                goals.forEach { g ->
                    userDoc.collection("savings_goals").document(g.id).set(g)
                }

                // Seed initial Wallets
                val wallets = listOf(
                    FirebaseWallet("w1", "Visa Platinum", "Techcombank •• 4092", 15750000L, true, "BANK"),
                    FirebaseWallet("w2", "Ví MoMo & ZaloPay", "Ví điện tử cá nhân", 2350000L, false, "WALLET"),
                    FirebaseWallet("w3", "Tiết kiệm tích lũy", "Kỳ hạn 12 tháng (5.8%/năm)", 110400000L, false, "SAVINGS")
                )
                wallets.forEach { w ->
                    userDoc.collection("wallets").document(w.id).set(w)
                }
            }
        }
    }

    // --- TRANSACTIONS API ---

    fun saveTransaction(context: Context, transaction: SavedTransaction, onComplete: ((Boolean) -> Unit)? = null) {
        // 1. Save locally
        TransactionStore.io.execute {
            runCatching {
                TransactionStore(context).use { it.save(transaction) }
            }.onFailure { e ->
                Log.e(TAG, "Local save error: ${e.message}")
            }
        }

        // 2. Save to Firestore
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) {
                onComplete?.invoke(false)
                return@ensureAuthenticated
            }

            val docData = hashMapOf(
                "id" to transaction.id,
                "amount" to transaction.amount,
                "type" to transaction.type.name,
                "category" to transaction.category,
                "note" to transaction.note,
                "sourceWallet" to transaction.sourceWallet,
                "destinationWallet" to transaction.destinationWallet,
                "occurredAt" to transaction.occurredAt,
                "excludeFromBudget" to transaction.excludeFromBudget,
                "userId" to uid
            )

            db.collection("users")
                .document(uid)
                .collection("transactions")
                .document(transaction.id)
                .set(docData)
                .addOnSuccessListener {
                    Log.d(TAG, "Transaction synced to Firebase: ${transaction.id}")
                    onComplete?.invoke(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firebase sync failed: ${e.message}")
                    onComplete?.invoke(false)
                }
        }
    }

    fun listenToTransactions(context: Context, onDataChanged: (List<SavedTransaction>) -> Unit) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("transactions")
                .orderBy("occurredAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Firestore listen error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val transactions = snapshot.documents.mapNotNull { doc ->
                            try {
                                SavedTransaction(
                                    id = doc.getString("id") ?: doc.id,
                                    amount = doc.getLong("amount") ?: 0L,
                                    type = TransactionType.valueOf(doc.getString("type") ?: "EXPENSE"),
                                    category = doc.getString("category") ?: "",
                                    note = doc.getString("note") ?: "",
                                    sourceWallet = doc.getString("sourceWallet") ?: "",
                                    destinationWallet = doc.getString("destinationWallet"),
                                    occurredAt = doc.getLong("occurredAt") ?: System.currentTimeMillis(),
                                    excludeFromBudget = doc.getBoolean("excludeFromBudget") ?: false
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        // Sync to SQLite local cache
                        TransactionStore.io.execute {
                            val store = TransactionStore(context)
                            transactions.forEach { t ->
                                runCatching { store.save(t) }
                            }
                        }

                        onDataChanged(transactions)
                    }
                }
        }
    }

    // --- BUDGETS API ---

    fun saveBudget(context: Context, budget: FirebaseBudget, onComplete: ((Boolean) -> Unit)? = null) {
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
                .addOnSuccessListener {
                    onComplete?.invoke(true)
                }
                .addOnFailureListener {
                    onComplete?.invoke(false)
                }
        }
    }

    fun listenToBudgets(onBudgetsChanged: (List<FirebaseBudget>) -> Unit) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("budgets")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FirebaseBudget::class.java)
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
                .addOnSuccessListener {
                    onComplete?.invoke(true)
                }
                .addOnFailureListener {
                    onComplete?.invoke(false)
                }
        }
    }

    fun listenToSavingsGoals(onGoalsChanged: (List<FirebaseSavingsGoal>) -> Unit) {
        ensureAuthenticated { uid ->
            if (uid.isNullOrEmpty()) return@ensureAuthenticated

            db.collection("users")
                .document(uid)
                .collection("savings_goals")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(FirebaseSavingsGoal::class.java)
                        }
                        onGoalsChanged(list)
                    }
                }
        }
    }
}