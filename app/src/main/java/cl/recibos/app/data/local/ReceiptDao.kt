package cl.recibos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    /**
     * Historial filtrado. Todos los filtros son opcionales:
     * - [query]: texto para cliente o producto ('' = sin filtro).
     * - [number]: número exacto de comprobante (null = sin filtro).
     * - [from]/[to]: rango de fecha en epoch millis, [to] exclusivo.
     * - [method]: nombre del enum PaymentMethod.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM receipts
        WHERE (:from IS NULL OR date_time >= :from)
          AND (:to IS NULL OR date_time < :to)
          AND (:method IS NULL OR payment_method = :method
               OR id IN (SELECT receipt_id FROM receipt_payments WHERE method = :method))
          AND (
                (:query = '' AND :number IS NULL)
                OR (:number IS NOT NULL AND number = :number)
                OR (:query != '' AND client_name LIKE '%' || :query || '%')
                OR (:query != '' AND id IN (
                        SELECT receipt_id FROM receipt_items
                        WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'))
          )
        ORDER BY is_draft DESC, number DESC, updated_at DESC
        """,
    )
    fun observeFiltered(query: String, number: Int?, from: Long?, to: Long?, method: String?): Flow<List<ReceiptWithItems>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeById(id: Long): Flow<ReceiptWithItems?>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): ReceiptWithItems?

    @Insert
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Insert
    suspend fun insertItems(items: List<ReceiptItemEntity>)

    @Query("DELETE FROM receipt_items WHERE receipt_id = :receiptId")
    suspend fun deleteItems(receiptId: Long)

    @Insert
    suspend fun insertPayments(payments: List<ReceiptPaymentEntity>)

    @Query("DELETE FROM receipt_payments WHERE receipt_id = :receiptId")
    suspend fun deletePayments(receiptId: Long)

    @Query("DELETE FROM receipts WHERE id = :id AND is_draft = 1")
    suspend fun deleteDraft(id: Long): Int

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceipt(id: Long): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE number = :number")
    suspend fun countWithNumber(number: Int): Int

    @Query("SELECT value FROM counters WHERE name = :name")
    suspend fun getCounter(name: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCounter(counter: CounterEntity)

    @Query("SELECT MAX(number) FROM receipts")
    suspend fun maxNumber(): Int?
}
