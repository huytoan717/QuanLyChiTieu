package com.nhom9.quanlychitieu.data

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.model.Category
import com.nhom9.quanlychitieu.model.Wallet
import com.nhom9.quanlychitieu.model.WalletBalance
import java.util.concurrent.Executors

class TransactionStore(context: Context, name: String = "transactions.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE transactions (
            id TEXT PRIMARY KEY NOT NULL,
            amount INTEGER NOT NULL CHECK(amount > 0),
            type TEXT NOT NULL,
            category TEXT NOT NULL,
            note TEXT NOT NULL,
            source_wallet TEXT NOT NULL,
            destination_wallet TEXT,
            occurred_at INTEGER NOT NULL,
            exclude_budget INTEGER NOT NULL,
            revision INTEGER NOT NULL DEFAULT 0,
            dirty INTEGER NOT NULL DEFAULT 0,
            deleted INTEGER NOT NULL DEFAULT 0,
            remote_known INTEGER NOT NULL DEFAULT 0,
            category_id TEXT
        )""")
        createWalletTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("revision", "dirty", "deleted", "remote_known").forEach {
                db.execSQL("ALTER TABLE transactions ADD COLUMN $it INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("ALTER TABLE transactions ADD COLUMN category_id TEXT")
        }
        if (oldVersion < 3) createWalletTable(db)
    }

    private fun createWalletTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE wallets (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, opening_balance INTEGER NOT NULL DEFAULT 0)")
        for (name in listOf("Tiền mặt", "Tài khoản cá nhân", "Ví điện tử")) {
            db.execSQL("INSERT INTO wallets(id, name) VALUES (?, ?)", arrayOf(name, name))
        }
        // Keep every old wallet reference, without inventing an opening balance.
        db.rawQuery("""SELECT source_wallet FROM transactions WHERE source_wallet != '' AND deleted = 0
            UNION SELECT destination_wallet FROM transactions
            WHERE type = 'TRANSFER' AND destination_wallet != '' AND deleted = 0 ORDER BY 1""", null).use { cursor ->
            while (cursor.moveToNext()) addImportedWallet(db, cursor.getString(0))
        }
    }

    fun wallets(): List<Wallet> {
        val transactions = all()
        val result = mutableListOf<Wallet>()
        readableDatabase.rawQuery("SELECT id, name, opening_balance FROM wallets ORDER BY rowid", null).use { cursor ->
            while (cursor.moveToNext()) {
                val wallet = Wallet(id = cursor.getString(0), name = cursor.getString(1), openingBalance = cursor.getLong(2))
                result.add(wallet.copy(balance = WalletBalance.calculate(wallet, transactions)))
            }
        }
        WalletBalance.total(result) // Do not silently wrap around when money exceeds Long.
        return result
    }

    fun saveWallet(id: String?, name: String, openingBalance: Long) = atomic {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 60) { "Tên ví cần có từ 1 đến 60 ký tự." }
        require(openingBalance in 0L..1_000_000_000_000L) { "Số dư ban đầu phải từ 0 đến 1.000.000.000.000 VNĐ." }
        val currentWallets = wallets()
        require(id == null || currentWallets.any { it.id == id }) { "Ví không còn tồn tại." }
        val currentName = currentWallets.find { it.id == id }?.name
        val nameChanged = currentName == null || !currentName.equals(cleanName, true)
        require(currentWallets.none { it.id != id &&
            (it.name.equals(cleanName, true) || (nameChanged && it.id.equals(cleanName, true))) }) {
            "Tên ví này đã được sử dụng. Hãy chọn tên khác."
        }
        val values = ContentValues().apply {
            put("name", cleanName)
            put("opening_balance", openingBalance)
        }
        if (id == null) {
            // Keep the original name as a stable key; renaming changes only the display name.
            values.put("id", cleanName)
            writableDatabase.insertOrThrow("wallets", null, values)
        } else {
            writableDatabase.update("wallets", values, "id = ?", arrayOf(id))
        }
        wallets()
    }

    fun deleteWallet(id: String) = atomic {
        val wallet = wallets().find { it.id == id }
        require(wallet != null) { "Ví không còn tồn tại." }
        val used = readableDatabase.rawQuery(
            "SELECT 1 FROM transactions WHERE deleted = 0 AND (source_wallet = ? OR destination_wallet = ?) LIMIT 1",
            arrayOf(id, id)).use { it.moveToFirst() }
        require(!used) { "Không thể xóa ví đang có giao dịch." }
        require(wallet.balance == 0L) { "Chỉ có thể xóa ví khi số dư bằng 0." }
        writableDatabase.delete("wallets", "id = ?", arrayOf(id))
    }

    private fun importWallets(transaction: SavedTransaction) {
        val ids = mutableListOf(transaction.sourceWallet)
        if (transaction.type == TransactionType.TRANSFER) ids.add(requireNotNull(transaction.destinationWallet))
        for (id in ids) {
            addImportedWallet(writableDatabase, id)
        }
    }

    private fun addImportedWallet(db: SQLiteDatabase, id: String) {
        val usedNames = mutableListOf<String>()
        db.rawQuery("SELECT id, name FROM wallets", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == id) return
                usedNames.add(cursor.getString(0))
                usedNames.add(cursor.getString(1))
            }
        }
        var name = id
        var suffix = 2
        while (usedNames.any { it.equals(name, ignoreCase = true) }) {
            name = "$id ($suffix)"
            suffix++
        }
        db.execSQL("INSERT INTO wallets(id, name) VALUES (?, ?)", arrayOf(id, name))
    }

    private fun checkWallets(transaction: SavedTransaction) {
        val ids = wallets().map { it.id }
        require(transaction.sourceWallet in ids &&
            (transaction.type != TransactionType.TRANSFER || transaction.destinationWallet in ids)) {
            "Ví đã thay đổi. Vui lòng chọn lại ví."
        }
    }

    fun save(transaction: SavedTransaction) = atomic {
        validate(transaction)
        importWallets(transaction)
        // Creation is idempotent. Editing has a separate explicit API.
        val inserted = writableDatabase.insertWithOnConflict(
            "transactions", null, values(transaction), SQLiteDatabase.CONFLICT_IGNORE)
        check(inserted != -1L || find(transaction.id) == transaction) { "Draft already saved with different data" }
        wallets()
    }

    private fun validate(transaction: SavedTransaction) {
        require(transaction.id.isNotBlank() && transaction.amount > 0)
        require(transaction.sourceWallet.isNotBlank())
        if (transaction.type == TransactionType.TRANSFER) {
            require(!transaction.destinationWallet.isNullOrBlank())
            require(transaction.destinationWallet != transaction.sourceWallet)
        } else {
            require(transaction.category.isNotBlank())
        }
    }

    private fun values(transaction: SavedTransaction) = ContentValues().apply {
            put("id", transaction.id)
            put("amount", transaction.amount)
            put("type", transaction.type.name)
            put("category", transaction.category)
            put("category_id", transaction.categoryId)
            put("note", transaction.note)
            put("source_wallet", transaction.sourceWallet)
            put("destination_wallet", transaction.destinationWallet)
            put("occurred_at", transaction.occurredAt)
            put("exclude_budget", if (transaction.excludeFromBudget) 1 else 0)
    }

    fun find(id: String): SavedTransaction? = readableDatabase.query(
        "transactions", null, "id = ? AND deleted = 0", arrayOf(id), null, null, null, "1"
    ).use { cursor -> if (cursor.moveToFirst()) readTransaction(cursor) else null }

    fun recent(): List<SavedTransaction> = readableDatabase.query(
        "transactions", null, "deleted = 0", null, null, null, "occurred_at DESC, rowid DESC", "50"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(readTransaction(cursor))
            }
        }
    }

    fun all(): List<SavedTransaction> = readableDatabase.query(
        "transactions", null, "deleted = 0", null, null, null, "occurred_at DESC, rowid DESC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(readTransaction(cursor)) } }

    fun containsDraft(id: String): Boolean = readableDatabase.query(
        "transactions", arrayOf("id"), "id = ?", arrayOf(id), null, null, null
    ).use { it.moveToFirst() }

    fun createPending(transaction: SavedTransaction) = atomic {
        validate(transaction)
        checkWallets(transaction)
        save(transaction)
        markPending(transaction.id, deleted = false)
    }

    fun update(transaction: SavedTransaction, expected: SavedTransaction? = null) = atomic {
        validate(transaction)
        val current = find(transaction.id)
        check(current != null) { "Transaction no longer exists" }
        check(expected == null || current == expected) { "Transaction changed while editing" }
        checkWallets(transaction)
        writableDatabase.update("transactions", values(transaction), "id = ?", arrayOf(transaction.id))
        markPending(transaction.id, deleted = false)
        wallets()
    }

    fun delete(id: String) = atomic {
        check(find(id) != null) { "Transaction no longer exists" }
        markPending(id, deleted = true)
        wallets()
    }

    private fun markPending(id: String, deleted: Boolean) {
        writableDatabase.execSQL(
            "UPDATE transactions SET revision = revision + 1, dirty = 1, deleted = ? WHERE id = ?",
            arrayOf<Any>(if (deleted) 1 else 0, id))
    }

    data class Pending(val transaction: SavedTransaction, val revision: Long, val deleted: Boolean)

    fun pending(): List<Pending> = readableDatabase.query(
        "transactions", null, "dirty = 1", null, null, null, "rowid"
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(Pending(readTransaction(cursor),
            cursor.getLong(cursor.getColumnIndexOrThrow("revision")),
            cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) != 0))
    } }

    fun acknowledge(pending: Pending) {
        // An older network response must not clear a more recent edit/delete.
        writableDatabase.update("transactions", ContentValues().apply {
            put("dirty", 0)
            put("remote_known", 1)
        }, "id = ? AND revision = ?", arrayOf(pending.transaction.id, pending.revision.toString()))
    }

    fun mergeRemote(transactions: List<SavedTransaction>, authoritative: Boolean, complete: Boolean = true) = atomic {
        transactions.forEach { transaction ->
            validate(transaction)
            val blocked = readableDatabase.query("transactions", arrayOf("dirty", "deleted"),
                "id = ?", arrayOf(transaction.id), null, null, null).use {
                it.moveToFirst() && (!authoritative || it.getInt(0) != 0 || it.getInt(1) != 0)
            }
            if (!blocked) {
                importWallets(transaction)
                val data = values(transaction).apply { put("remote_known", 1) }
                writableDatabase.insertWithOnConflict("transactions", null, data, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        if (authoritative && complete) {
            val remoteIds = transactions.map { it.id }.toSet()
            val removed = readableDatabase.query("transactions", arrayOf("id"),
                "remote_known = 1 AND dirty = 0 AND deleted = 0", null, null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) if (cursor.getString(0) !in remoteIds) add(cursor.getString(0)) }
            }
            removed.forEach { id -> writableDatabase.update("transactions",
                ContentValues().apply { put("deleted", 1) }, "id = ?", arrayOf(id)) }
        }
        wallets()
    }

    private fun atomic(block: () -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readTransaction(cursor: Cursor): SavedTransaction {
        fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column))
        fun number(column: String) = cursor.getLong(cursor.getColumnIndexOrThrow(column))
        return SavedTransaction(text("id"), number("amount"), TransactionType.valueOf(text("type")),
            text("category"), text("note"), text("source_wallet"), text("destination_wallet"),
            number("occurred_at"), number("exclude_budget") == 1L,
            text("category_id") ?: Category.fromName(text("category")).id)
    }

    companion object {
        // Serialize writes and following reads; database work never blocks the UI thread.
        val io = Executors.newSingleThreadExecutor()
    }
}
