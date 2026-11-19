package com.nhom9.quanlychitieu.model

object WalletBalance {
    fun calculate(wallet: Wallet, transactions: List<SavedTransaction>): Long {
        var balance = wallet.openingBalance
        for (transaction in transactions) {
            if (transaction.sourceWallet == wallet.id) {
                balance = if (transaction.type == TransactionType.INCOME) {
                    Math.addExact(balance, transaction.amount)
                } else {
                    Math.subtractExact(balance, transaction.amount)
                }
            }
            if (transaction.type == TransactionType.TRANSFER && transaction.destinationWallet == wallet.id) {
                balance = Math.addExact(balance, transaction.amount)
            }
        }
        return balance
    }

    fun total(wallets: List<Wallet>): Long {
        var total = 0L
        for (wallet in wallets) total = Math.addExact(total, wallet.balance)
        return total
    }

    fun name(id: String?, wallets: List<Wallet>): String {
        return wallets.find { it.id == id }?.name ?: id.orEmpty()
    }
}
