package cl.recibos.app.domain.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Formato de pesos chilenos: "$215.000", sin decimales y con punto de miles. */
object Clp {
    fun format(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        return "$sign$" + groupThousands(kotlin.math.abs(amount).toString())
    }

    /** Agrupa una cadena de dígitos con puntos: "1234567" → "1.234.567". */
    fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder(digits.length + digits.length / 3)
        val firstGroup = digits.length % 3
        digits.forEachIndexed { index, c ->
            if (index > 0 && (index - firstGroup) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Convierte texto ingresado por el usuario a monto, ignorando todo lo que no sea dígito. */
    fun parse(input: String): Long? {
        val digits = input.filter(Char::isDigit).trimStart('0')
        if (digits.isEmpty()) return if (input.any(Char::isDigit)) 0 else null
        if (digits.length > 12) return null
        return digits.toLong()
    }
}

object ReceiptNumber {
    /** 1 → "0001". Números sobre 9999 se muestran completos. */
    fun format(number: Int): String = number.toString().padStart(4, '0')

    /** Interpreta lo que el usuario escribe en el buscador ("0003", "n° 3") como número. */
    fun parseQuery(query: String): Int? {
        val digits = query.filter(Char::isDigit)
        if (digits.isEmpty() || digits.length > 9) return null
        return digits.toInt()
    }
}

object ExportFileName {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** "RECIBO-0001-20261123.pdf" */
    fun of(number: Int, date: LocalDate, extension: String): String =
        "RECIBO-${ReceiptNumber.format(number)}-${date.format(dateFormat)}.$extension"
}

object DateFormats {
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")

    fun date(value: LocalDateTime): String = value.format(date)
    fun dateTime(value: LocalDateTime): String = value.format(dateTime)
}
