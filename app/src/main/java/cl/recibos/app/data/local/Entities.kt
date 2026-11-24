package cl.recibos.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "receipts",
    indices = [Index(value = ["number"], unique = true), Index("date_time")],
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int?,
    @ColumnInfo(name = "is_draft") val isDraft: Boolean,
    @ColumnInfo(name = "date_time") val dateTime: Long,
    val status: String,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "client_phone") val clientPhone: String,
    @ColumnInfo(name = "client_email") val clientEmail: String,
    @ColumnInfo(name = "client_rut", defaultValue = "''") val clientRut: String,
    val discount: Long,
    val subtotal: Long,
    val total: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "payment_reference") val paymentReference: String,
    @ColumnInfo(name = "seller_name") val sellerName: String,
    @ColumnInfo(name = "seller_phone") val sellerPhone: String,
    @ColumnInfo(name = "seller_email") val sellerEmail: String,
    @ColumnInfo(name = "seller_thanks") val sellerThanks: String,
    @ColumnInfo(name = "seller_logo_path") val sellerLogoPath: String?,
    val accent: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "issued_at") val issuedAt: Long?,
    @ColumnInfo(name = "status_changed_at") val statusChangedAt: Long?,
    /** Número a recuperar cuando un borrador rehace un comprobante borrado. */
    @ColumnInfo(name = "reserved_number") val reservedNumber: Int?,
)

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receipt_id")],
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "receipt_id") val receiptId: Long,
    val position: Int,
    val name: String,
    val description: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
)

/** Una modalidad de pago del comprobante (1 a 3 por comprobante). */
@Entity(
    tableName = "receipt_payments",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receipt_id")],
)
data class ReceiptPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "receipt_id") val receiptId: Long,
    val position: Int,
    val method: String,
    val amount: Long,
    val reference: String,
)

/** Contador persistente de la numeración correlativa. Nunca retrocede. */
@Entity(tableName = "counters")
data class CounterEntity(
    @PrimaryKey val name: String,
    val value: Int,
)

data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receipt_id")
    val items: List<ReceiptItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "receipt_id")
    val payments: List<ReceiptPaymentEntity>,
)
