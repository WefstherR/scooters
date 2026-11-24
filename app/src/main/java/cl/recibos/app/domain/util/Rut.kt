package cl.recibos.app.domain.util

/** RUT chileno: validación del dígito verificador (módulo 11) y formato "12.345.678-5". */
object Rut {

    /** Deja solo dígitos y K, en mayúscula: "12.345.678-k" → "12345678K". */
    fun clean(input: String): String = input.uppercase().filter { it.isDigit() || it == 'K' }

    fun checkDigit(body: String): Char {
        var sum = 0
        var factor = 2
        for (c in body.reversed()) {
            sum += (c - '0') * factor
            factor = if (factor == 7) 2 else factor + 1
        }
        return when (val dv = 11 - sum % 11) {
            11 -> '0'
            10 -> 'K'
            else -> '0' + dv
        }
    }

    fun isValid(input: String): Boolean {
        val rut = clean(input)
        if (rut.length !in 2..9) return false
        val body = rut.dropLast(1)
        if (!body.all(Char::isDigit) || body.toLong() == 0L) return false
        return checkDigit(body) == rut.last()
    }

    /** Formato con puntos y guion. Si el texto no es un RUT completo, lo devuelve limpio. */
    fun format(input: String): String {
        val rut = clean(input)
        if (rut.length < 2) return rut
        val body = rut.dropLast(1).trimStart('0')
        if (!body.all(Char::isDigit)) return rut
        return Clp.groupThousands(body) + "-" + rut.last()
    }
}
