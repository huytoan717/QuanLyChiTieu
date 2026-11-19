package com.nhom9.quanlychitieu.model

data class SpendingCaps(
    val isSingleCapEnabled: Boolean = true,
    val maxSingleTransactionCap: Long = 2000000L,
    val isDailyCapEnabled: Boolean = true,
    val maxDailyCap: Long = 1000000L
)

data class CapCheckResult(
    val isAllowed: Boolean = true,
    val warningMessage: String? = null,
    val isSingleCapExceeded: Boolean = false,
    val isDailyCapExceeded: Boolean = false
)