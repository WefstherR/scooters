package cl.recibos.app.data.local

import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentPart
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptAccent
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.model.SellerSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()

internal fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(default)

fun ReceiptWithItems.toDomain(): Receipt = Receipt(
    id = receipt.id,
    number = receipt.number,
    isDraft = receipt.isDraft,
    dateTime = receipt.dateTime.toLocalDateTime(),
    status = enumOr(receipt.status, PaymentStatus.PENDING),
    client = ClientInfo(receipt.clientName, receipt.clientPhone, receipt.clientEmail, receipt.clientRut),
    items = items.sortedBy { it.position }.map {
        ReceiptItem(it.name, it.description, it.quantity, it.unitPrice)
    },
    discount = receipt.discount,
    payments = payments.sortedBy { it.position }
        .map { PaymentPart(enumOr(it.method, PaymentMethod.CASH), it.amount, it.reference) }
        .ifEmpty { listOf(PaymentPart(enumOr(receipt.paymentMethod, PaymentMethod.CASH), receipt.total, receipt.paymentReference)) },
    reservedNumber = receipt.reservedNumber,
    seller = SellerSnapshot(
        name = receipt.sellerName,
        phone = receipt.sellerPhone,
        email = receipt.sellerEmail,
        thanksMessage = receipt.sellerThanks,
        logoPath = receipt.sellerLogoPath,
        accent = enumOr(receipt.accent, ReceiptAccent.GRAPHITE),
    ),
    createdAt = receipt.createdAt,
    updatedAt = receipt.updatedAt,
    issuedAt = receipt.issuedAt,
    statusChangedAt = receipt.statusChangedAt,
)

fun Receipt.toEntity(): ReceiptEntity {
    val totals = totals
    return ReceiptEntity(
        id = id,
        number = number,
        isDraft = isDraft,
        dateTime = dateTime.toEpochMillis(),
        status = status.name,
        clientName = client.name.trim(),
        clientPhone = client.phone.trim(),
        clientEmail = client.email.trim(),
        clientRut = client.rut.trim(),
        discount = totals.discount,
        subtotal = totals.subtotal,
        total = totals.total,
        paymentMethod = primaryMethod.name,
        paymentReference = payments.firstOrNull()?.reference?.trim().orEmpty(),
        sellerName = seller.name,
        sellerPhone = seller.phone,
        sellerEmail = seller.email,
        sellerThanks = seller.thanksMessage,
        sellerLogoPath = seller.logoPath,
        accent = seller.accent.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        issuedAt = issuedAt,
        statusChangedAt = statusChangedAt,
        reservedNumber = reservedNumber,
    )
}

fun Receipt.itemEntities(receiptId: Long): List<ReceiptItemEntity> = items.mapIndexed { index, item ->
    ReceiptItemEntity(
        receiptId = receiptId,
        position = index,
        name = item.name.trim(),
        description = item.description.trim(),
        quantity = item.quantity,
        unitPrice = item.unitPrice,
    )
}

/** Guarda cada medio con su monto definitivo (un solo medio = total del comprobante). */
fun Receipt.paymentEntities(receiptId: Long): List<ReceiptPaymentEntity> = resolvedPayments.mapIndexed { index, part ->
    ReceiptPaymentEntity(
        receiptId = receiptId,
        position = index,
        method = part.method.name,
        amount = part.amount,
        reference = if (part.method == PaymentMethod.CASH) "" else part.reference.trim(),
    )
}
