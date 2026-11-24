package cl.recibos.app.domain

import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentPart
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

    @Test fun `rut opcional pero valido si se ingresa`() {
        val base = listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000))
        assertTrue(ReceiptValidator.validate(Receipt(dateTime = now, items = base, client = ClientInfo(rut = "12.345.678-5"))).isValid)
        assertNotNull(ReceiptValidator.validate(Receipt(dateTime = now, items = base, client = ClientInfo(rut = "12.345.678-9"))).rutError)
    }

    @Test fun `pago dividido debe sumar el total sin repetir medios`() {
        val items = listOf(ReceiptItem("Scooter", quantity = 1, unitPrice = 215_000))
        fun check(vararg parts: PaymentPart) = ReceiptValidator.validate(Receipt(dateTime = now, items = items, payments = parts.toList()))
        assertTrue(check(PaymentPart(PaymentMethod.TRANSFER, 150_000), PaymentPart(PaymentMethod.CASH, 65_000)).isValid)
        assertTrue(
            check(
                PaymentPart(PaymentMethod.TRANSFER, 100_000),
                PaymentPart(PaymentMethod.PAYMENT_LINK, 100_000),
                PaymentPart(PaymentMethod.CASH, 15_000),
            ).isValid,
        )
        assertEquals(
            "Falta asignar $5.000 entre los medios de pago.",
            check(PaymentPart(PaymentMethod.TRANSFER, 150_000), PaymentPart(PaymentMethod.CASH, 60_000)).paymentError,
        )
        assertNotNull(check(PaymentPart(PaymentMethod.TRANSFER, 150_000), PaymentPart(PaymentMethod.CASH, 70_000)).paymentError)
        assertNotNull(check(PaymentPart(PaymentMethod.CASH, 100_000), PaymentPart(PaymentMethod.CASH, 115_000)).paymentError)
        assertNotNull(check(PaymentPart(PaymentMethod.TRANSFER, 215_000), PaymentPart(PaymentMethod.CASH, 0)).paymentError)
    }

    @Test fun `correo invalido se informa`() {
        val result = ReceiptValidator.validate(receipt(listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000)), email = "juan@"))
        assertNotNull(result.emailError)
        assertTrue(ReceiptValidator.isValidEmail("juan@correo.cl"))
    }
}
