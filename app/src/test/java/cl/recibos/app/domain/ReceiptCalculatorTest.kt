package cl.recibos.app.domain

import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.util.ReceiptCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptCalculatorTest {

    @Test fun `subtotal de un producto`() {
        val totals = ReceiptCalculator.compute(listOf(ReceiptItem("Scooter eléctrico", "Modelo E9", 1, 215_000)), 0)
        assertEquals(215_000, totals.subtotal)
        assertEquals(0, totals.discount)
        assertEquals(215_000, totals.total)
    }

    @Test fun `varios productos con cantidades`() {
        val items = listOf(
            ReceiptItem("Scooter", quantity = 2, unitPrice = 215_000),
            ReceiptItem("Casco", quantity = 3, unitPrice = 19_990),
            ReceiptItem("Candado", quantity = 1, unitPrice = 8_500),
        )
        val totals = ReceiptCalculator.compute(items, 0)
        assertEquals(430_000 + 59_970 + 8_500, totals.subtotal)
        assertEquals(totals.subtotal, totals.total)
    }

    @Test fun `descuento de monto fijo`() {
        val totals = ReceiptCalculator.compute(listOf(ReceiptItem("Scooter", quantity = 1, unitPrice = 215_000)), 15_000)
        assertEquals(215_000, totals.subtotal)
        assertEquals(15_000, totals.discount)
        assertEquals(200_000, totals.total)
    }

    @Test fun `el descuento nunca deja el total negativo`() {
        val totals = ReceiptCalculator.compute(listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000)), 5_000)
        assertEquals(1_000, totals.discount)
        assertEquals(0, totals.total)
    }

    @Test fun `descuento negativo se ignora`() {
        val totals = ReceiptCalculator.compute(listOf(ReceiptItem("A", quantity = 1, unitPrice = 1_000)), -500)
        assertEquals(0, totals.discount)
        assertEquals(1_000, totals.total)
    }

    @Test fun `cantidades o precios invalidos no suman`() {
        assertEquals(0, ReceiptCalculator.lineTotal(0, 1_000))
        assertEquals(0, ReceiptCalculator.lineTotal(2, 0))
        assertEquals(0, ReceiptCalculator.lineTotal(-1, 1_000))
    }

    @Test(expected = ArithmeticException::class)
    fun `desborde se detecta en lugar de dar un monto incorrecto`() {
        ReceiptCalculator.lineTotal(Int.MAX_VALUE, Long.MAX_VALUE / 2)
    }
}
