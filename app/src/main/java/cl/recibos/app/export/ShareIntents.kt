package cl.recibos.app.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.ReceiptNumber
import java.io.File

/**
 * Construye los Intents para compartir. Nunca envía nada por sí mismo: siempre abre otra
 * aplicación donde el usuario decide si enviar.
 */
object ShareIntents {

    private val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun message(receipt: Receipt): String {
        val number = receipt.number?.let(ReceiptNumber::format).orEmpty()
        val amount = Clp.format(receipt.totals.total)
        val greeting = receipt.client.name.trim().takeIf { it.isNotEmpty() }?.let { "Hola $it, " } ?: ""
        return when (receipt.status) {
            PaymentStatus.PAID -> "${greeting}te comparto el recibo de pago N.º $number por $amount."
            PaymentStatus.PENDING -> "${greeting}te comparto el comprobante N.º $number por $amount (pago pendiente)."
            PaymentStatus.VOID -> "${greeting}te comparto el comprobante N.º $number (anulado)."
        }.replaceFirstChar { it.uppercase() }
    }

    private fun baseSend(uri: Uri, mimeType: String, text: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Abre WhatsApp si está instalado; si no, devuelve null para usar el menú general. */
    fun whatsApp(context: Context, uri: Uri, mimeType: String, receipt: Receipt): Intent? {
        val pkg = whatsAppPackages.firstOrNull { isInstalled(context, it) } ?: return null
        return baseSend(uri, mimeType, message(receipt)).apply {
            setPackage(pkg)
            whatsAppJid(receipt.client.phone)?.let { putExtra("jid", it) }
        }
    }

    /** Solo aplicaciones de correo (selector mailto:), con el archivo adjunto. */
    fun email(uri: Uri, mimeType: String, receipt: Receipt): Intent {
        val number = receipt.number?.let(ReceiptNumber::format).orEmpty()
        val send = baseSend(uri, mimeType, message(receipt)).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Recibo de pago N.º $number")
            receipt.client.email.trim().takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        return Intent.createChooser(send, "Enviar por correo")
    }

    /** Android Sharesheet con todas las aplicaciones compatibles. */
    fun chooser(uri: Uri, mimeType: String, receipt: Receipt): Intent =
        Intent.createChooser(baseSend(uri, mimeType, message(receipt)), "Compartir comprobante")

    fun view(uri: Uri, mimeType: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Convierte un teléfono chileno a formato internacional para abrir el chat del cliente
     * directamente: "9 8765 4321" → "56987654321@s.whatsapp.net".
     */
    fun whatsAppJid(phone: String): String? {
        val digits = phone.filter(Char::isDigit)
        val international = when {
            digits.length == 11 && digits.startsWith("56") -> digits
            digits.length == 9 && digits.startsWith("9") -> "56$digits"
            digits.length == 8 -> "569$digits"
            digits.length in 10..15 && phone.trim().startsWith("+") -> digits
            else -> return null
        }
        return "$international@s.whatsapp.net"
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
