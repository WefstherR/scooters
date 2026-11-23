package cl.recibos.app.data.repository

import cl.recibos.app.domain.model.PaymentMethod
import java.time.LocalDate
import java.time.YearMonth

/** Rango de fechas predefinido o personalizado para filtrar el historial. */
sealed interface DateFilter {
    val label: String

    /** Devuelve [desde, hasta) como fechas; null = sin límite. */
    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?>

    data object All : DateFilter {
        override val label = "Todas las fechas"
        override fun range(today: LocalDate) = null to null
    }

    data object Today : DateFilter {
        override val label = "Hoy"
        override fun range(today: LocalDate) = today to today.plusDays(1)
    }

    data object Last7Days : DateFilter {
        override val label = "Últimos 7 días"
        override fun range(today: LocalDate) = today.minusDays(6) to today.plusDays(1)
    }

    data object ThisMonth : DateFilter {
        override val label = "Este mes"
        override fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> {
            val month = YearMonth.from(today)
            return month.atDay(1) to month.plusMonths(1).atDay(1)
        }
    }

    data object LastMonth : DateFilter {
        override val label = "Mes anterior"
        override fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> {
            val month = YearMonth.from(today).minusMonths(1)
            return month.atDay(1) to month.plusMonths(1).atDay(1)
        }
    }

    /** Rango inclusivo [start, end]. */
    data class Custom(val start: LocalDate, val end: LocalDate) : DateFilter {
        override val label: String
            get() = "%02d/%02d – %02d/%02d".format(start.dayOfMonth, start.monthValue, end.dayOfMonth, end.monthValue)

        override fun range(today: LocalDate) = start to end.plusDays(1)
    }
}

data class ReceiptFilter(
    val query: String = "",
    val date: DateFilter = DateFilter.All,
    val method: PaymentMethod? = null,
)
