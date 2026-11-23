package cl.recibos.app.domain.model

/** Configuración actual del vendedor (editable en cualquier momento). */
data class SellerProfile(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val thanksMessage: String = "¡Gracias por tu compra!",
    val logoPath: String? = null,
    val showLogo: Boolean = true,
    val accent: ReceiptAccent = ReceiptAccent.GRAPHITE,
) {
    val isConfigured: Boolean get() = name.isNotBlank()

    fun toSnapshot(): SellerSnapshot = SellerSnapshot(
        name = name.trim(),
        phone = phone.trim(),
        email = email.trim(),
        thanksMessage = thanksMessage.trim(),
        logoPath = if (showLogo) logoPath else null,
        accent = accent,
    )
}
