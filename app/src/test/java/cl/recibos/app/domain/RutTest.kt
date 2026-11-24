package cl.recibos.app.domain

import cl.recibos.app.domain.util.Rut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RutTest {
    @Test fun `digito verificador`() {
        assertEquals('5', Rut.checkDigit("12345678"))
        assertEquals('K', Rut.checkDigit("10000013"))
        assertEquals('0', Rut.checkDigit("11111117"))
    }

    @Test fun `valida con o sin formato`() {
        assertTrue(Rut.isValid("12.345.678-5"))
        assertTrue(Rut.isValid("123456785"))
        assertTrue(Rut.isValid("10.000.013-k"))
        assertFalse(Rut.isValid("12.345.678-9"))
        assertFalse(Rut.isValid("5"))
        assertFalse(Rut.isValid("0-0"))
    }

    @Test fun `formato con puntos y guion`() {
        assertEquals("12.345.678-5", Rut.format("123456785"))
        assertEquals("10.000.013-K", Rut.format("10000013k"))
        assertEquals("7.654.321-6", Rut.format("7654321-6"))
    }
}
