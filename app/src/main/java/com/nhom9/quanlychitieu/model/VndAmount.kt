package com.nhom9.quanlychitieu.model

object VndAmount {
    private val validNumber = Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)")

    private fun parseNumber(raw: String): Long? {
        val value = raw.trim()
        if (!validNumber.matches(value)) return null
        return value.replace(".", "").toLongOrNull()
    }

    fun parse(raw: String): Long? {
        return parseNumber(raw)?.takeIf { it > 0 }
    }

    fun parseNonNegative(raw: String): Long? = parseNumber(raw)?.takeIf { it >= 0 }

    fun add(raw: String, increment: Long): Long? {
        if (increment <= 0) return null
        val current = if (raw.isBlank()) 0L else parseNumber(raw) ?: return null
        if (current > Long.MAX_VALUE - increment) return null
        return current + increment
    }

    fun format(value: Long): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN")).format(value)
}
