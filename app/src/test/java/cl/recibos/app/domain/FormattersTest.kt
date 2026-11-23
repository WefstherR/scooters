package cl.recibos.app.domain

import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.ExportFileName
import cl.recibos.app.domain.util.ReceiptNumber
import cl.recibos.app.export.ShareIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormattersTest {

    @Test fun `formato CLP sin decimales y con punto de miles`() {
        assertEquals("$0", Clp.format(0))
        assertEquals("$999", Clp.format(999))
        assertEquals("$1.000", Clp.format(1_000))
        assertEquals("$215.000", Clp.format(215_000))
        assertEquals("$1.234.567", Clp.format(1_234_567))
        assertEquals("-$15.000", Clp.format(-15_000))
    }

    @Test fun `interpretar montos escritos por el usuario`() {
        assertEquals(215_000L, Clp.parse("215.000"))
        assertEquals(215_000L, Clp.parse("$ 215000"))
        assertEquals(0L, Clp.parse("000"))
        assertNull(Clp.parse(""))
        assertNull(Clp.parse("9999999999999")) // 13 dígitos: fuera de rango
    }

    @Test fun `numero correlativo con cuatro digitos`() {
        assertEquals("0001", ReceiptNumber.format(1))
        assertEquals("0042", ReceiptNumber.format(42))
        assertEquals("12345", ReceiptNumber.format(12_345))
        assertEquals(3, ReceiptNumber.parseQuery("0003"))
        assertEquals(3, ReceiptNumber.parseQuery("N.º 3"))
        assertNull(ReceiptNumber.parseQuery("Juan"))
    }

    @Test fun `nombre de archivo exportado`() {
        val date = LocalDate.of(2026, 11, 23)
        assertEquals("RECIBO-0001-20261123.pdf", ExportFileName.of(1, date, "pdf"))
        assertEquals("RECIBO-0128-20261123.png", ExportFileName.of(128, date, "png"))
    }

    @Test fun `telefono chileno a identificador de WhatsApp`() {
        assertEquals("56987654321@s.whatsapp.net", ShareIntents.whatsAppJid("9 8765 4321"))
        assertEquals("56987654321@s.whatsapp.net", ShareIntents.whatsAppJid("+56 9 8765 4321"))
        assertEquals("56987654321@s.whatsapp.net", ShareIntents.whatsAppJid("87654321"))
        assertNull(ShareIntents.whatsAppJid("123"))
    }
}
