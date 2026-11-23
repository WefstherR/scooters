package cl.recibos.app.data.local

import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
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
    client = ClientInfo(receipt.clientName, receipt.clientPhone, receipt.clientEmail),
    items = items.sortedBy { it.position }.map {
        ReceiptItem(it.name, it.description, it.quantity, it.unitPrice)
    },
    discount = receipt.discount,
    paymentMethod = enumOr(receipt.paymentMethod, PaymentMethod.CASH),
    paymentReference = receipt.paymentReference,
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
        discount = totals.discount,
        subtotal = totals.subtotal,
        total = totals.total,
        paymentMethod = paymentMethod.name,
        paymentReference = paymentReference.trim(),
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
