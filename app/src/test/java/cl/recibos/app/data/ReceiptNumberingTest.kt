package cl.recibos.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.recibos.app.data.local.AppDatabase
import cl.recibos.app.data.repository.DateFilter
import cl.recibos.app.data.repository.ReceiptFilter
import cl.recibos.app.data.repository.ReceiptRepository
import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentPart
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.model.SellerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReceiptNumberingTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ReceiptRepository
    private val seller = SellerSnapshot(name = "Scooters Pro")

    @Before fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = ReceiptRepository(db, today = { LocalDate.of(2026, 11, 23) })
    }

    @After fun tearDown() = db.close()

    private fun draft(
        product: String = "Scooter eléctrico",
        client: String = "",
        method: PaymentMethod = PaymentMethod.TRANSFER,
        date: LocalDateTime = LocalDateTime.of(2026, 11, 23, 10, 0),
        status: PaymentStatus = PaymentStatus.PAID,
    ) = Receipt(
        dateTime = date,
        status = status,
        client = ClientInfo(name = client),
        items = listOf(ReceiptItem(product, "Modelo E9", 1, 215_000)),
        payments = listOf(PaymentPart(method)),
    )

    private suspend fun issueNew(r: Receipt = draft()) = repo.issue(repo.saveDraft(r), seller)

    @Test fun `numeracion correlativa desde 0001`() = runBlocking {
        assertEquals(1, repo.nextNumber())
        assertEquals(1, issueNew().number)
        assertEquals(2, issueNew().number)
        assertEquals(3, issueNew().number)
        assertEquals(4, repo.nextNumber())
    }

    @Test fun `los borradores no consumen numeros`() = runBlocking {
        val draftId = repo.saveDraft(draft())
        repo.saveDraft(draft())
        assertNull(repo.get(draftId)!!.number)
        assertEquals(1, issueNew().number)
        repo.deleteDraft(draftId)
        assertEquals(2, issueNew().number)
    }

    @Test fun `numeros anulados no se reutilizan`() = runBlocking {
        issueNew()
        val second = issueNew()
        repo.void(second.id)
        val voided = repo.get(second.id)!!
        assertEquals(PaymentStatus.VOID, voided.status)
        assertEquals(2, voided.number) // conserva su número en el historial
        assertEquals(3, issueNew().number)
    }

    @Test fun `un comprobante emitido conserva los datos del vendedor`() = runBlocking {
        val issued = issueNew()
        assertEquals("Scooters Pro", repo.get(issued.id)!!.seller.name)
        assertFalse(repo.get(issued.id)!!.isDraft)
    }

    @Test(expected = IllegalStateException::class)
    fun `no se puede emitir dos veces`() = runBlocking {
        val issued = issueNew()
        repo.issue(issued.id, seller)
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun `no se puede editar un comprobante emitido`() = runBlocking {
        val issued = issueNew()
        repo.saveDraft(issued.copy(items = listOf(ReceiptItem("Otro", quantity = 1, unitPrice = 1))))
        Unit
    }

    @Test fun `pendiente se marca como pagado manualmente`() = runBlocking {
        val issued = issueNew(draft(status = PaymentStatus.PENDING))
        repo.markAsPaid(issued.id)
        assertEquals(PaymentStatus.PAID, repo.get(issued.id)!!.status)
    }

    @Test fun `duplicar crea un borrador nuevo sin numero`() = runBlocking {
        val issued = issueNew(draft(client = "Ana"))
        val copyId = repo.duplicateAsDraft(issued.id)
        val copy = repo.get(copyId)!!
        assertTrue(copy.isDraft)
        assertNull(copy.number)
        assertEquals("Ana", copy.client.name)
        assertEquals(issued.items, copy.items)
    }

    @Test fun `borrar y rehacer recupera el mismo numero`() = runBlocking {
        issueNew()
        val second = issueNew(draft(client = "Ana", product = "Casco"))
        issueNew()
        val redoId = repo.deleteAndRedo(second.id)
        assertNull(repo.get(second.id))
        val redo = repo.get(redoId)!!
        assertTrue(redo.isDraft)
        assertEquals(2, redo.reservedNumber)
        assertEquals("Ana", redo.client.name)
        assertEquals(second.dateTime, redo.dateTime)
        // Mientras tanto, un comprobante nuevo sigue la numeración normal.
        assertEquals(4, issueNew().number)
        // Al emitir el borrador recupera el N.º 0002 y el contador no retrocede.
        assertEquals(2, repo.issue(redoId, seller).number)
        assertEquals(5, repo.nextNumber())
    }

    @Test fun `rehacer el ultimo y descartar el borrador libera el numero`() = runBlocking {
        issueNew()
        val last = issueNew()
        val redoId = repo.deleteAndRedo(last.id)
        assertEquals(3, repo.nextNumber()) // el 2 está reservado por el borrador
        repo.deleteDraft(redoId)
        assertEquals(2, repo.nextNumber()) // era el último: se reutiliza
        assertEquals(2, issueNew().number)
    }

    @Test fun `descartar un rehacer intermedio deja el numero sin usar`() = runBlocking {
        val first = issueNew()
        issueNew()
        repo.deleteDraft(repo.deleteAndRedo(first.id))
        assertEquals(3, issueNew().number)
    }

    @Test fun `pago dividido y rut se guardan y se filtran por cualquier medio`() = runBlocking {
        val split = draft(client = "Ana").copy(
            client = ClientInfo(name = "Ana", rut = "12.345.678-5"),
            payments = listOf(PaymentPart(PaymentMethod.TRANSFER, 150_000, "OP-1"), PaymentPart(PaymentMethod.CASH, 65_000)),
        )
        val issued = issueNew(split)
        val stored = repo.get(issued.id)!!
        assertEquals(split.payments, stored.payments)
        assertEquals("12.345.678-5", stored.client.rut)
        assertEquals(listOf(1), repo.observe(ReceiptFilter(method = PaymentMethod.CASH)).first().map { it.number })
        assertEquals(listOf(1), repo.observe(ReceiptFilter(method = PaymentMethod.TRANSFER)).first().map { it.number })
    }

    @Test(expected = IllegalStateException::class)
    fun `no se emite un pago dividido que no cuadra con el total`() = runBlocking {
        issueNew(draft().copy(payments = listOf(PaymentPart(PaymentMethod.TRANSFER, 100_000), PaymentPart(PaymentMethod.CASH, 10_000))))
        Unit
    }

    @Test fun `buscar por numero, cliente o producto y filtrar`() = runBlocking {
        issueNew(draft(product = "Scooter eléctrico", client = "Ana Pérez"))
        issueNew(draft(product = "Casco", client = "Luis", method = PaymentMethod.CASH))
        issueNew(draft(product = "Cargador", client = "", date = LocalDateTime.of(2026, 10, 2, 9, 0)))

        suspend fun search(f: ReceiptFilter) = repo.observe(f).first().map { it.number }

        assertEquals(listOf(3, 2, 1), search(ReceiptFilter()))
        assertEquals(listOf(2), search(ReceiptFilter(query = "0002")))
        assertEquals(listOf(1), search(ReceiptFilter(query = "ana")))
        assertEquals(listOf(2), search(ReceiptFilter(query = "casco")))
        assertEquals(listOf(2), search(ReceiptFilter(method = PaymentMethod.CASH)))
        assertEquals(listOf(2, 1), search(ReceiptFilter(date = DateFilter.ThisMonth)))
        assertEquals(listOf(3), search(ReceiptFilter(date = DateFilter.LastMonth)))
    }
}
