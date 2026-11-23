package cl.recibos.app.domain.model

import cl.recibos.app.domain.util.ReceiptCalculator
import cl.recibos.app.domain.util.ReceiptTotals
import java.time.LocalDateTime

/** Estado de pago declarado por el vendedor. La app no verifica pagos. */
enum class PaymentStatus(val label: String) {
    PAID("Pagado"),
    PENDING("Pendiente"),
    VOID("Anulado"),
}

/** Método de pago declarado. Solo se registra, nunca se procesa. */
enum class PaymentMethod(val label: String) {
    CASH("Efectivo"),
    TRANSFER("Transferencia bancaria"),
    PAYMENT_LINK("Link de pago"),
}

/** Colores de acento disponibles para el comprobante (neutros y sobrios). */
enum class ReceiptAccent(val label: String, val argb: Long) {
    GRAPHITE("Grafito", 0xFF1F2328),
    NAVY("Azul marino", 0xFF1E3A5F),
    FOREST("Verde bosque", 0xFF1F5135),
    WINE("Burdeos", 0xFF6B1F2E),
    TEAL("Petróleo", 0xFF0F5C63),
}

data class ClientInfo(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
) {
    val isEmpty: Boolean get() = name.isBlank() && phone.isBlank() && email.isBlank()
}

data class ReceiptItem(
    val name: String = "",
    val description: String = "",
    val quantity: Int = 1,
    val unitPrice: Long = 0,
) {
    val lineTotal: Long get() = ReceiptCalculator.lineTotal(quantity, unitPrice)
}

/**
 * Copia de los datos del vendedor congelada al emitir el comprobante.
 * Así un comprobante emitido conserva su información original aunque
 * después cambie la configuración.
 */
data class SellerSnapshot(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val thanksMessage: String = "",
    val logoPath: String? = null,
    val accent: ReceiptAccent = ReceiptAccent.GRAPHITE,
)

data class Receipt(
    val id: Long = 0,
    /** Número correlativo. Es nulo mientras el comprobante es borrador. */
    val number: Int? = null,
    val isDraft: Boolean = true,
    val dateTime: LocalDateTime,
    val status: PaymentStatus = PaymentStatus.PAID,
    val client: ClientInfo = ClientInfo(),
    val items: List<ReceiptItem> = listOf(ReceiptItem()),
    val discount: Long = 0,
    val paymentMethod: PaymentMethod = PaymentMethod.TRANSFER,
    val paymentReference: String = "",
    val seller: SellerSnapshot = SellerSnapshot(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val issuedAt: Long? = null,
    val statusChangedAt: Long? = null,
) {
    val totals: ReceiptTotals get() = ReceiptCalculator.compute(items, discount)
}
