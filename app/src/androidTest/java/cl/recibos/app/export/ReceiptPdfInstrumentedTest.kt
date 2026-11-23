package cl.recibos.app.export

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.model.SellerSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

/** Genera PDFs reales en un dispositivo/emulador y los vuelve a abrir con PdfRenderer. */
@RunWith(AndroidJUnit4::class)
class ReceiptPdfInstrumentedTest {

    private val exporter = ReceiptExporter(ApplicationProvider.getApplicationContext())

    private fun sample(items: Int = 1) = Receipt(
        number = 1,
        isDraft = false,
        dateTime = LocalDateTime.of(2026, 11, 23, 15, 42),
        status = PaymentStatus.PAID,
        client = ClientInfo(name = "Camila Rojas"),
        items = List(items) { i -> ReceiptItem(if (i == 0) "Scooter eléctrico" else "Accesorio $i", "Modelo E9", 1, 215_000) },
        paymentMethod = PaymentMethod.TRANSFER,
        seller = SellerSnapshot(name = "Movilidad Sur"),
    )

    private fun pages(file: File): List<Pair<Int, Int>> =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            (0 until pdf.pageCount).map { i -> pdf.openPage(i).use { it.width to it.height } }
        }

    @Test fun pdfA5DeUnaPagina() = runBlocking {
        val file = exporter.exportToCache(sample(), ExportFormat.PDF)
        assertEquals("RECIBO-0001-20261123.pdf", file.name)
        assertTrue(file.readBytes().copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        assertEquals(listOf(420 to 595), pages(file))
    }

    @Test fun pdfConMuchosProductosTieneVariasPaginasA5() = runBlocking {
        val file = exporter.exportToCache(sample(items = 30), ExportFormat.PDF)
        val pages = pages(file)
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it == 420 to 595 })
    }
}
