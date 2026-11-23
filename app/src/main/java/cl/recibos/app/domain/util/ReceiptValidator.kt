package cl.recibos.app.domain.util

import cl.recibos.app.domain.model.Receipt

/** Errores de validación expresados en lenguaje claro para el vendedor. */
object ReceiptValidator {

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    data class Result(
        val itemErrors: Map<Int, String> = emptyMap(),
        val discountError: String? = null,
        val emailError: String? = null,
        val generalError: String? = null,
    ) {
        val isValid: Boolean
            get() = itemErrors.isEmpty() && discountError == null && emailError == null && generalError == null

        val firstMessage: String?
            get() = generalError ?: itemErrors.values.firstOrNull() ?: discountError ?: emailError
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
        return Result(itemErrors = itemErrors, discountError = discountError, emailError = emailError)
    }
}
