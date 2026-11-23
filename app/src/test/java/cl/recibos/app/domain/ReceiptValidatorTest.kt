package cl.recibos.app.domain

import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.util.ReceiptValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReceiptValidatorTest {
    private val now = LocalDateTime.of(2026, 11, 23, 12, 0)
    private fun receipt(items: List<ReceiptItem>, discount: Long = 0, email: String = "") =
        Receipt(dateTime = now, items = items, discount = discount, client = ClientInfo(email = email))

    @Test fun `un comprobante simple es valido sin datos del cliente`() {
        assertTrue(ReceiptValidator.validate(receipt(listOf(ReceiptItem("Scooter", quantity = 1, unitPrice = 215_000)))).isValid)
    }

    @Test fun `producto sin nombre o sin precio no es valido`() {
        val result = ReceiptValidator.validate(
            receipt(listOf(ReceiptItem("", quantity = 1, unitPrice = 1_000), ReceiptItem("Casco", quantity = 1, unitPrice = 0))),
        )
        assertFalse(result.isValid)
        assertEquals(setOf(0, 1), result.itemErrors.keys)
    }

    @Test fun `descuento igual o mayor al subtotal no es valido`() {
        val result = ReceiptValidator.validate(receipt(listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000)), discount = 1_000))
        assertNotNull(result.discountError)
    }

    @Test fun `correo invalido se informa`() {
        val result = ReceiptValidator.validate(receipt(listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000)), email = "juan@"))
        assertNotNull(result.emailError)
        assertTrue(ReceiptValidator.isValidEmail("juan@correo.cl"))
    }
}
