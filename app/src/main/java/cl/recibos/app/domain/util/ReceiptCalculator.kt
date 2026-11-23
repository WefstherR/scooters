package cl.recibos.app.domain.util

import cl.recibos.app.domain.model.ReceiptItem

data class ReceiptTotals(
    val subtotal: Long,
    /** Descuento efectivamente aplicado (nunca mayor que el subtotal). */
    val discount: Long,
    val total: Long,
)

/** Cálculos de montos en pesos chilenos (enteros, sin decimales). */
object ReceiptCalculator {

    /** Límite razonable por monto para evitar desbordes y errores de tipeo. */
    const val MAX_AMOUNT: Long = 999_999_999_999L

    fun lineTotal(quantity: Int, unitPrice: Long): Long {
        if (quantity <= 0 || unitPrice <= 0) return 0
        return Math.multiplyExact(quantity.toLong(), unitPrice)
    }

    fun compute(items: List<ReceiptItem>, discount: Long): ReceiptTotals {
        val subtotal = items.fold(0L) { acc, item -> Math.addExact(acc, item.lineTotal) }
        val applied = discount.coerceIn(0, subtotal)
        return ReceiptTotals(subtotal = subtotal, discount = applied, total = subtotal - applied)
    }
}
