package cl.recibos.app.domain.util

import cl.recibos.app.domain.model.Receipt

/** Errores de validación expresados en lenguaje claro para el vendedor. */
object ReceiptValidator {

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    data class Result(
        val itemErrors: Map<Int, String> = emptyMap(),
        val discountError: String? = null,
        val emailError: String? = null,
        val rutError: String? = null,
        val paymentError: String? = null,
        val generalError: String? = null,
    ) {
        val isValid: Boolean
            get() = itemErrors.isEmpty() && discountError == null && emailError == null &&
                rutError == null && paymentError == null && generalError == null

        val firstMessage: String?
            get() = generalError ?: itemErrors.values.firstOrNull() ?: discountError ?: paymentError ?: rutError ?: emailError
    }

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    fun validate(receipt: Receipt): Result {
        if (receipt.items.isEmpty()) return Result(generalError = "Agrega al menos un producto.")
        val itemErrors = mutableMapOf<Int, String>()
        receipt.items.forEachIndexed { index, item ->
            val error = when {
                item.name.isBlank() -> "Escribe el nombre del producto."
                item.quantity < 1 -> "La cantidad debe ser 1 o más."
                item.unitPrice <= 0 -> "Ingresa el precio unitario."
                item.unitPrice > ReceiptCalculator.MAX_AMOUNT -> "El precio es demasiado alto."
                else -> null
            }
            if (error != null) itemErrors[index] = error
        }
        val subtotal = runCatching { receipt.totals.subtotal }.getOrElse {
            return Result(generalError = "Los montos son demasiado altos.")
        }
        val discountError = when {
            receipt.discount < 0 -> "El descuento no puede ser negativo."
            receipt.discount > 0 && receipt.discount >= subtotal && itemErrors.isEmpty() ->
                "El descuento debe ser menor que el subtotal."
            else -> null
        }
        val email = receipt.client.email
        val emailError = if (email.isNotBlank() && !isValidEmail(email)) "El correo no parece válido." else null
        val rut = receipt.client.rut
        val rutError = if (rut.isNotBlank() && !Rut.isValid(rut)) "El RUT no es válido. Revisa el dígito verificador." else null
        val paymentError = if (itemErrors.isEmpty() && discountError == null) validatePayments(receipt) else null
        return Result(
            itemErrors = itemErrors,
            discountError = discountError,
            emailError = emailError,
            rutError = rutError,
            paymentError = paymentError,
        )
    }

    /** Con pago dividido: 2 o 3 medios distintos, cada uno con monto, y que sumen el total. */
    fun validatePayments(receipt: Receipt): String? {
        val parts = receipt.payments
        if (parts.isEmpty()) return "Elige un método de pago."
        if (parts.size == 1) return null
        if (parts.size > MAX_PAYMENT_PARTS) return "Puedes combinar como máximo $MAX_PAYMENT_PARTS medios de pago."
        if (parts.map { it.method }.toSet().size != parts.size) return "No repitas el mismo medio de pago."
        if (parts.any { it.amount <= 0 }) return "Ingresa el monto de cada medio de pago."
        val sum = parts.fold(0L) { acc, p -> acc + p.amount }
        val total = receipt.totals.total
        return when {
            sum < total -> "Falta asignar ${Clp.format(total - sum)} entre los medios de pago."
            sum > total -> "Los medios de pago suman ${Clp.format(sum - total)} más que el total."
            else -> null
        }
    }

    const val MAX_PAYMENT_PARTS = 3
}
