package cl.recibos.app.data.repository

import androidx.room.withTransaction
import cl.recibos.app.data.local.AppDatabase
import cl.recibos.app.data.local.CounterEntity
import cl.recibos.app.data.local.itemEntities
import cl.recibos.app.data.local.toDomain
import cl.recibos.app.data.local.toEntity
import cl.recibos.app.data.local.toEpochMillis
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.SellerSnapshot
import cl.recibos.app.domain.util.ReceiptNumber
import cl.recibos.app.domain.util.ReceiptValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

class ReceiptRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val dao = db.receiptDao()

    fun observe(filter: ReceiptFilter): Flow<List<Receipt>> {
        val trimmed = filter.query.trim()
        val number = ReceiptNumber.parseQuery(trimmed)?.takeIf { trimmed.any(Char::isDigit) }
        val (from, to) = filter.date.range(today())
        return dao.observeFiltered(
            query = trimmed,
            number = number,
            from = from?.atStartOfDay()?.toEpochMillis(),
            to = to?.atStartOfDay()?.toEpochMillis(),
            method = filter.method?.name,
        ).map { list -> list.map { it.toDomain() } }
    }

    fun observeById(id: Long): Flow<Receipt?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: Long): Receipt? = dao.getById(id)?.toDomain()

    /** Número que recibirá el próximo comprobante emitido (solo informativo). */
    suspend fun nextNumber(): Int = currentCounter() + 1

    /** Guarda o actualiza un borrador. Devuelve su id. */
    suspend fun saveDraft(receipt: Receipt): Long = db.withTransaction {
        val now = clock()
        if (receipt.id == 0L) {
            val id = dao.insertReceipt(
                receipt.copy(isDraft = true, number = null, createdAt = now, updatedAt = now).toEntity(),
            )
            dao.insertItems(receipt.itemEntities(id))
            id
        } else {
            val existing = dao.getById(receipt.id) ?: error("El borrador ya no existe.")
            check(existing.receipt.isDraft) { "Un comprobante emitido no puede editarse." }
            dao.updateReceipt(
                receipt.copy(isDraft = true, number = null, createdAt = existing.receipt.createdAt, updatedAt = now)
                    .toEntity(),
            )
            dao.deleteItems(receipt.id)
            dao.insertItems(receipt.itemEntities(receipt.id))
            receipt.id
        }
    }

    /**
     * Emite un borrador: le asigna el siguiente número correlativo y congela los datos del
     * vendedor. Todo ocurre en una transacción, por lo que los números nunca se repiten ni
     * se reutilizan (tampoco los de comprobantes anulados).
     */
    suspend fun issue(draftId: Long, seller: SellerSnapshot): Receipt = db.withTransaction {
        val draft = dao.getById(draftId)?.toDomain() ?: error("El borrador ya no existe.")
        check(draft.isDraft) { "Este comprobante ya fue emitido." }
        check(draft.status != PaymentStatus.VOID) { "No se puede emitir un comprobante anulado." }
        val validation = ReceiptValidator.validate(draft)
        check(validation.isValid) { validation.firstMessage ?: "Datos incompletos." }

        val number = currentCounter() + 1
        dao.setCounter(CounterEntity(COUNTER_NAME, number))
        val now = clock()
        val issued = draft.copy(
            number = number,
            isDraft = false,
            seller = seller,
            issuedAt = now,
            updatedAt = now,
            statusChangedAt = now,
        )
        dao.updateReceipt(issued.toEntity())
        issued
    }

    /** Confirmación manual del vendedor de que recibió el pago de un comprobante pendiente. */
    suspend fun markAsPaid(id: Long) = changeStatus(id, PaymentStatus.PAID) {
        check(it.status == PaymentStatus.PENDING) { "Solo un comprobante pendiente puede marcarse como pagado." }
    }

    /** Anula un comprobante emitido. Se conserva en el historial con su número. */
    suspend fun void(id: Long) = changeStatus(id, PaymentStatus.VOID) {
        check(it.status != PaymentStatus.VOID) { "El comprobante ya está anulado." }
    }

    suspend fun deleteDraft(id: Long): Boolean = dao.deleteDraft(id) > 0

    /** Crea un nuevo borrador a partir de un comprobante existente, con fecha actual. */
    suspend fun duplicateAsDraft(id: Long, now: LocalDateTime = LocalDateTime.now()): Long {
        val source = get(id) ?: error("El comprobante ya no existe.")
        val status = if (source.status == PaymentStatus.VOID) PaymentStatus.PAID else source.status
        return saveDraft(
            Receipt(
                dateTime = now,
                status = status,
                client = source.client,
                items = source.items,
                discount = source.discount,
                paymentMethod = source.paymentMethod,
            ),
        )
    }

    private suspend fun changeStatus(id: Long, status: PaymentStatus, guard: (Receipt) -> Unit) {
        db.withTransaction {
            val receipt = dao.getById(id)?.toDomain() ?: error("El comprobante ya no existe.")
            check(!receipt.isDraft) { "Primero debes emitir el comprobante." }
            guard(receipt)
            val now = clock()
            dao.updateReceipt(receipt.copy(status = status, statusChangedAt = now, updatedAt = now).toEntity())
        }
    }

    private suspend fun currentCounter(): Int {
        // El contador se mantiene aunque se borren filas; maxNumber es una red de seguridad.
        val counter = dao.getCounter(COUNTER_NAME) ?: 0
        val max = dao.maxNumber() ?: 0
        return maxOf(counter, max)
    }

    private companion object {
        const val COUNTER_NAME = "receipt_number"
    }
}
