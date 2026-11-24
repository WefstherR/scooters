package cl.recibos.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.DateFormats
import cl.recibos.app.domain.util.ReceiptNumber
import kotlin.math.ceil
import kotlin.math.min

/**
 * Diseña el comprobante como una lista de bloques medidos en puntos tipográficos (1/72").
 * El mismo diseño se dibuja en una página PDF A5 o en un PNG de alta resolución, de modo
 * que ambos formatos comparten exactamente la misma identidad gráfica.
 */
class ReceiptRenderer(
    private val logoLoader: (String) -> Bitmap? = { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() },
) {

    /** Unidad mínima de diseño: altura conocida y una función que dibuja desde (0,0). */
    class Block(val height: Float, val keepWithNext: Boolean = false, val draw: Canvas.() -> Unit)

    class ReceiptLayout(val blocks: List<Block>, val accent: Int) {
        val contentHeight: Float = blocks.fold(0f) { acc, b -> acc + b.height }
    }

    fun layout(receipt: Receipt, previewNumber: Int? = null): ReceiptRenderer.ReceiptLayout {
        val accent = receipt.seller.accent.argb.toInt()
        val blocks = mutableListOf<Block>()
        val number = receipt.number ?: previewNumber
        val totals = receipt.totals

        // ENCABEZADO
        val logo = receipt.seller.logoPath?.let(logoLoader)
        blocks += header(
            logo = logo,
            accent = accent,
            subtitle = "Comprobante particular · N.º " + (number?.let(ReceiptNumber::format) ?: "—"),
        )
        blocks += divider()

        // INFORMACIÓN
        blocks += sectionLabel("INFORMACIÓN")
        blocks += infoRow("Fecha", DateFormats.dateTime(receipt.dateTime))
        if (receipt.seller.name.isNotBlank()) blocks += infoRow("Vendedor", receipt.seller.name)
        val sellerContact = listOf(receipt.seller.phone, receipt.seller.email).filter { it.isNotBlank() }
        if (sellerContact.isNotEmpty()) blocks += infoRow("Contacto", sellerContact.joinToString(" · "))
        if (receipt.client.name.isNotBlank()) blocks += infoRow("Cliente", receipt.client.name.trim())
        if (receipt.client.rut.isNotBlank()) blocks += infoRow("RUT cliente", receipt.client.rut.trim())
        blocks += divider()

        // DETALLE DE VENTA
        blocks += sectionLabel("DETALLE DE VENTA")
        receipt.items.forEach { item ->
            val unit = if (item.quantity == 1) "1 unidad" else "${item.quantity} unidades × ${Clp.format(item.unitPrice)}"
            val detail = listOf(item.description.trim(), unit).filter { it.isNotEmpty() }.joinToString(" · ")
            blocks += itemRow(item.name.trim(), detail, Clp.format(item.lineTotal))
        }
        if (totals.discount > 0) {
            blocks += spacer(4f)
            blocks += amountRow("Subtotal", Clp.format(totals.subtotal))
            blocks += amountRow("Descuento", "−" + Clp.format(totals.discount))
        }
        blocks += divider()

        // TOTAL Y PAGO
        val totalLabel = when (receipt.status) {
            PaymentStatus.PAID -> "TOTAL RECIBIDO"
            PaymentStatus.PENDING -> "TOTAL PENDIENTE"
            PaymentStatus.VOID -> "TOTAL (ANULADO)"
        }
        blocks += total(totalLabel, Clp.format(totals.total), voided = receipt.status == PaymentStatus.VOID)
        if (receipt.isSplitPayment) {
            // Pago dividido: cada medio con su monto y su referencia.
            blocks += infoRow("Forma de pago", "Pago dividido en ${receipt.payments.size} medios")
            receipt.resolvedPayments.forEach { part ->
                blocks += paymentRow(part.method.label, Clp.format(part.amount), part.reference.trim().takeIf { it.isNotEmpty() })
            }
            blocks += spacer(2f)
        } else {
            val part = receipt.resolvedPayments.first()
            blocks += infoRow("Método de pago", part.method.label)
            if (part.reference.isNotBlank()) blocks += infoRow("N.º operación", part.reference.trim())
        }
        blocks += statusRow(receipt.status)
        blocks += divider()

        // PIE
        val disclaimer = when (receipt.status) {
            PaymentStatus.PAID ->
                "Recibo particular que acredita la recepción del pago. No constituye boleta ni factura tributaria."
            PaymentStatus.PENDING ->
                "Registro de una venta con pago pendiente. No acredita la recepción de dinero. " +
                    "No constituye boleta ni factura tributaria."
            PaymentStatus.VOID ->
                "Comprobante anulado. No acredita la recepción de pago. No constituye boleta ni factura tributaria."
        }
        blocks += footer(receipt.seller.thanksMessage.takeIf { it.isNotBlank() && receipt.status != PaymentStatus.VOID }, disclaimer)

        return ReceiptLayout(blocks, accent)
    }

    // ---------------------------------------------------------------- Dibujo

    /** PNG de ancho fijo y alto adaptado al contenido. */
    fun renderBitmap(layout: ReceiptRenderer.ReceiptLayout, widthPx: Int = PNG_WIDTH_PX): Bitmap {
        val scale = widthPx / PAGE_WIDTH
        val heightPt = ACCENT_BAR + MARGIN + layout.contentHeight + MARGIN
        val bitmap = Bitmap.createBitmap(widthPx, ceil(heightPt * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        drawAccentBar(canvas, layout.accent)
        canvas.translate(MARGIN, ACCENT_BAR + MARGIN)
        layout.blocks.forEach { block ->
            block.draw(canvas)
            canvas.translate(0f, block.height)
        }
        return bitmap
    }

    /** Reparte los bloques en páginas A5 sin cortar ninguno por la mitad. */
    fun paginate(layout: ReceiptRenderer.ReceiptLayout): List<List<Block>> {
        val pages = mutableListOf<MutableList<Block>>(mutableListOf())
        var available = PAGE_HEIGHT - ACCENT_BAR - 2 * MARGIN - PAGE_FOOTER
        var used = 0f
        val blocks = layout.blocks
        var i = 0
        while (i < blocks.size) {
            // Un título de sección viaja junto con su primer elemento.
            val group = if (blocks[i].keepWithNext && i + 1 < blocks.size) listOf(blocks[i], blocks[i + 1]) else listOf(blocks[i])
            val groupHeight = group.fold(0f) { acc, b -> acc + b.height }
            if (used + groupHeight > available && pages.last().isNotEmpty()) {
                pages += mutableListOf<Block>()
                available = PAGE_HEIGHT - 2 * MARGIN - PAGE_FOOTER
                used = 0f
            }
            pages.last().addAll(group)
            used += groupHeight
            i += group.size
        }
        return pages
    }

    /** Dibuja una página del PDF (coordenadas en puntos). */
    fun drawPdfPage(canvas: Canvas, layout: ReceiptRenderer.ReceiptLayout, blocks: List<Block>, pageIndex: Int, pageCount: Int) {
        canvas.drawColor(Color.WHITE)
        var top = MARGIN
        if (pageIndex == 0) {
            drawAccentBar(canvas, layout.accent)
            top += ACCENT_BAR
        }
        canvas.save()
        canvas.translate(MARGIN, top)
        blocks.forEach { block ->
            block.draw(canvas)
            canvas.translate(0f, block.height)
        }
        canvas.restore()
        if (pageCount > 1) {
            val paint = textPaint(7.5f, MUTED)
            val text = "Página ${pageIndex + 1} de $pageCount"
            canvas.drawText(text, PAGE_WIDTH - MARGIN - paint.measureText(text), PAGE_HEIGHT - MARGIN / 2, paint)
        }
    }

    private fun drawAccentBar(canvas: Canvas, accent: Int) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH, ACCENT_BAR, Paint().apply { color = accent })
    }

    // ---------------------------------------------------------------- Bloques

    private fun header(logo: Bitmap?, accent: Int, subtitle: String): Block {
        val title = textLayout("RECIBO DE PAGO", textPaint(21f, accent, bold = true).apply { letterSpacing = 0.06f }, CONTENT_WIDTH)
        val sub = textLayout(subtitle, textPaint(10f, MUTED), CONTENT_WIDTH)
        var logoW = 0f
        var logoH = 0f
        if (logo != null && logo.width > 0 && logo.height > 0) {
            val s = min(LOGO_MAX_W / logo.width, LOGO_MAX_H / logo.height)
            logoW = logo.width * s
            logoH = logo.height * s
        }
        val logoSpace = if (logoH > 0) logoH + 14f else 0f
        val height = logoSpace + title.height + 4f + sub.height + 6f
        return Block(height) {
            if (logo != null && logoH > 0) {
                drawBitmap(logo, null, RectF(0f, 0f, logoW, logoH), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            save(); translate(0f, logoSpace); title.draw(this)
            translate(0f, title.height + 4f); sub.draw(this); restore()
        }
    }

    private fun divider(): Block = Block(22f) {
        drawLine(0f, 11f, CONTENT_WIDTH, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIVIDER; strokeWidth = 0.8f })
    }

    private fun spacer(height: Float): Block = Block(height) {}

    private fun sectionLabel(text: String): Block {
        val layout = textLayout(text, textPaint(7.5f, MUTED, bold = true).apply { letterSpacing = 0.16f }, CONTENT_WIDTH)
        return Block(layout.height + 8f, keepWithNext = true) { layout.draw(this) }
    }

    private fun infoRow(label: String, value: String): Block {
        val labelLayout = textLayout(label, textPaint(9.5f, MUTED), LABEL_WIDTH)
        val valueLayout = textLayout(value, textPaint(9.5f, INK), CONTENT_WIDTH - LABEL_WIDTH - 8f)
        val height = maxOf(labelLayout.height, valueLayout.height) + 5f
        return Block(height) {
            labelLayout.draw(this)
            save(); translate(LABEL_WIDTH + 8f, 0f); valueLayout.draw(this); restore()
        }
    }

    private fun itemRow(name: String, detail: String, amount: String): Block {
        val amountPaint = textPaint(10.5f, INK, medium = true)
        val amountWidth = amountPaint.measureText(amount)
        val nameWidth = CONTENT_WIDTH - amountWidth - 14f
        val nameLayout = textLayout(name, textPaint(10.5f, INK, medium = true), nameWidth)
        val detailLayout = textLayout(detail, textPaint(9f, MUTED), nameWidth)
        val height = nameLayout.height + 2f + detailLayout.height + 10f
        return Block(height) {
            nameLayout.draw(this)
            val baseline = nameLayout.getLineBaseline(0).toFloat()
            drawText(amount, CONTENT_WIDTH - amountWidth, baseline, amountPaint)
            save(); translate(0f, nameLayout.height + 2f); detailLayout.draw(this); restore()
        }
    }

    /** Fila de un medio dentro de un pago dividido: "Transferencia bancaria ··· $150.000". */
    private fun paymentRow(method: String, amount: String, reference: String?): Block {
        val indent = LABEL_WIDTH + 8f
        val amountPaint = textPaint(9.5f, INK, medium = true)
        val amountWidth = amountPaint.measureText(amount)
        val textWidth = CONTENT_WIDTH - indent - amountWidth - 10f
        val methodLayout = textLayout(method, textPaint(9.5f, INK), textWidth)
        val refLayout = reference?.let { textLayout("Op. $it", textPaint(8.5f, MUTED), textWidth) }
        val height = methodLayout.height + (refLayout?.let { it.height + 1f } ?: 0f) + 5f
        return Block(height) {
            save(); translate(indent, 0f)
            methodLayout.draw(this)
            if (refLayout != null) { translate(0f, methodLayout.height + 1f); refLayout.draw(this) }
            restore()
            drawText(amount, CONTENT_WIDTH - amountWidth, methodLayout.getLineBaseline(0).toFloat(), amountPaint)
        }
    }

    private fun amountRow(label: String, value: String): Block {
        val labelPaint = textPaint(9.5f, MUTED)
        val valuePaint = textPaint(9.5f, INK)
        val fm = labelPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.15f
        return Block(lineHeight + 3f) {
            drawText(label, 0f, -fm.ascent, labelPaint)
            drawText(value, CONTENT_WIDTH - valuePaint.measureText(value), -fm.ascent, valuePaint)
        }
    }

    private fun total(label: String, amount: String, voided: Boolean): Block {
        val labelLayout = textLayout(label, textPaint(8f, MUTED, bold = true).apply { letterSpacing = 0.14f }, CONTENT_WIDTH)
        val amountPaint = textPaint(28f, if (voided) MUTED else INK, bold = true).apply { isStrikeThruText = voided }
        val amountLayout = textLayout(amount, amountPaint, CONTENT_WIDTH)
        val height = labelLayout.height + 2f + amountLayout.height + 12f
        return Block(height) {
            labelLayout.draw(this)
            save(); translate(0f, labelLayout.height + 2f); amountLayout.draw(this); restore()
        }
    }

    private fun statusRow(status: PaymentStatus): Block {
        val (text, badgeColor) = when (status) {
            PaymentStatus.PAID -> "PAGADO ✓" to PAID_COLOR
            PaymentStatus.PENDING -> "PENDIENTE" to PENDING_COLOR
            PaymentStatus.VOID -> "ANULADO" to VOID_COLOR
        }
        val labelLayout = textLayout("Estado", textPaint(9.5f, MUTED), LABEL_WIDTH)
        val badgePaint = textPaint(9f, badgeColor, bold = true).apply { letterSpacing = 0.06f }
        val fm = badgePaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val padH = 8f
        val padV = 3.5f
        val badgeW = badgePaint.measureText(text) + padH * 2
        val badgeH = textH + padV * 2
        val height = maxOf(labelLayout.height.toFloat(), badgeH) + 6f
        return Block(height) {
            val labelOffset = (badgeH - labelLayout.height) / 2f
            save(); translate(0f, labelOffset.coerceAtLeast(0f)); labelLayout.draw(this); restore()
            val left = LABEL_WIDTH + 8f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (badgeColor and 0x00FFFFFF) or 0x1F000000 }
            drawRoundRect(RectF(left, 0f, left + badgeW, badgeH), badgeH / 2, badgeH / 2, bg)
            drawText(text, left + padH, padV - fm.ascent, badgePaint)
        }
    }

    private fun footer(thanks: String?, disclaimer: String): Block {
        val thanksLayout = thanks?.let {
            textLayout(it, textPaint(10f, INK, italic = true), CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
        }
        val disclaimerLayout = textLayout(disclaimer, textPaint(7.5f, MUTED), CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
        val thanksSpace = thanksLayout?.let { it.height + 10f } ?: 0f
        return Block(4f + thanksSpace + disclaimerLayout.height) {
            save(); translate(0f, 4f)
            thanksLayout?.draw(this)
            translate(0f, thanksSpace)
            disclaimerLayout.draw(this)
            restore()
        }
    }

    // ---------------------------------------------------------------- Utilidades

    private fun textPaint(size: Float, color: Int, bold: Boolean = false, medium: Boolean = false, italic: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when {
                bold -> Typeface.create("sans-serif", Typeface.BOLD)
                medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                italic -> Typeface.create("sans-serif", Typeface.ITALIC)
                else -> Typeface.create("sans-serif", Typeface.NORMAL)
            }
        }

    private fun textLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(align)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.15f)
        .build()

    companion object {
        /** A5 vertical en puntos (148 × 210 mm). */
        const val PAGE_WIDTH = 420f
        const val PAGE_HEIGHT = 595f
        const val MARGIN = 36f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val ACCENT_BAR = 5f
        const val PAGE_FOOTER = 14f
        const val LABEL_WIDTH = 86f
        const val LOGO_MAX_W = 120f
        const val LOGO_MAX_H = 48f

        /** ~3,4 px por punto: nítido en cualquier pantalla y al imprimir. */
        const val PNG_WIDTH_PX = 1440

        private const val INK = 0xFF1C1F23.toInt()
        private const val MUTED = 0xFF6B7280.toInt()
        private const val DIVIDER = 0xFFE5E7EB.toInt()
        private const val PAID_COLOR = 0xFF1B7F3B.toInt()
        private const val PENDING_COLOR = 0xFFB45309.toInt()
        private const val VOID_COLOR = 0xFFB3261E.toInt()
    }
}
