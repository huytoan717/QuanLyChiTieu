package com.nhom9.quanlychitieu.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.Category

interface TransactionRemote : AutoCloseable {
    fun listen(onSnapshot: (List<SavedTransaction>, Boolean, Boolean) -> Unit, onError: () -> Unit)
    fun write(pending: TransactionStore.Pending, onComplete: (Boolean) -> Unit)
}

class FirestoreTransactionRemote : TransactionRemote {
    private var registration: ListenerRegistration? = null
    private var generation = 0

    override fun listen(onSnapshot: (List<SavedTransaction>, Boolean, Boolean) -> Unit, onError: () -> Unit) {
        val currentGeneration = ++generation
        registration?.remove()
        FinanceRepository.ensureAuthenticated { uid ->
            if (currentGeneration != generation) return@ensureAuthenticated
            if (uid == null) {
                onError()
                return@ensureAuthenticated
            }
            registration = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("transactions").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (currentGeneration != generation) return@addSnapshotListener
                    if (error != null || snapshot == null) {
                        onError()
                        return@addSnapshotListener
                    }
                    val rows = snapshot.documents.mapNotNull { doc -> runCatching {
                        SavedTransaction(doc.id, requireNotNull(doc.getLong("amount")),
                            TransactionType.valueOf(requireNotNull(doc.getString("type"))),
                            doc.getString("category").orEmpty(), doc.getString("note").orEmpty(),
                            doc.getString("sourceWallet").orEmpty(), doc.getString("destinationWallet"),
                            requireNotNull(doc.getLong("occurredAt")), doc.getBoolean("excludeFromBudget") ?: false,
                            doc.getString("categoryId") ?: Category.fromName(doc.getString("category").orEmpty()).id
                        ).also { transaction ->
                            require(transaction.amount > 0 && transaction.sourceWallet.isNotBlank())
                            if (transaction.type == TransactionType.TRANSFER) {
                                require(!transaction.destinationWallet.isNullOrBlank() &&
                                    transaction.destinationWallet != transaction.sourceWallet)
                            } else require(transaction.category.isNotBlank())
                        }
                    }.getOrNull() }
                    // A cache/partially parsed snapshot cannot prove that a record was deleted remotely.
                    onSnapshot(rows, !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites(),
                        rows.size == snapshot.size())
                }
        }
    }

    override fun write(pending: TransactionStore.Pending, onComplete: (Boolean) -> Unit) {
        FinanceRepository.ensureAuthenticated { uid ->
            if (uid == null) {
                onComplete(false)
                return@ensureAuthenticated
            }
            val t = pending.transaction
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("transactions").document(t.id)
            val task = if (pending.deleted) doc.delete() else doc.set(mapOf(
                "id" to t.id, "amount" to t.amount, "type" to t.type.name,
                "category" to t.category, "categoryId" to t.categoryId, "note" to t.note,
                "sourceWallet" to t.sourceWallet, "destinationWallet" to t.destinationWallet,
                "occurredAt" to t.occurredAt, "excludeFromBudget" to t.excludeFromBudget, "userId" to uid))
            task.addOnSuccessListener { onComplete(true) }.addOnFailureListener { onComplete(false) }
        }
    }

    override fun close() {
        generation++
        registration?.remove()
        registration = null
    }
}
