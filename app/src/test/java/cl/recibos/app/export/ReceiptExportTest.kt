package cl.recibos.app.export

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptAccent
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.model.SellerSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
/**
 * Pruebas de exportación en el computador. La generación de PDF usa el motor nativo de Android,
 * que Robolectric no implementa; esas pruebas están en androidTest/ReceiptPdfInstrumentedTest.
 */
class ReceiptExportTest {

    private val exporter = ReceiptExporter(ApplicationProvider.getApplicationContext())

    private fun sample(status: PaymentStatus = PaymentStatus.PAID, items: Int = 1, discount: Long = 0) = Receipt(
        number = 1,
        isDraft = false,
        dateTime = LocalDateTime.of(2026, 11, 23, 15, 42),
        status = status,
        client = ClientInfo(name = "Camila Rojas"),
        items = List(items) { i ->
            if (i == 0) ReceiptItem("Scooter eléctrico", "Modelo E9", 1, 215_000)
            else ReceiptItem("Accesorio $i", "Casco · talla M", 2, 12_990)
        },
        discount = discount,
        paymentMethod = PaymentMethod.TRANSFER,
        paymentReference = "OP-48213",
        seller = SellerSnapshot(
            name = "Movilidad Sur",
            phone = "+56 9 8765 4321",
            email = "ventas@movilidadsur.cl",
            thanksMessage = "¡Gracias por tu compra!",
            accent = ReceiptAccent.NAVY,
        ),
    )

    @Test fun `nombre de archivo segun numero y fecha`() {
        assertEquals("RECIBO-0001-20261123.pdf", exporter.fileName(sample(), ExportFormat.PDF))
        assertEquals("RECIBO-0001-20261123.png", exporter.fileName(sample(), ExportFormat.PNG))
    }

    @Test fun `genera PNG de alta resolucion`() {
        val out = ByteArrayOutputStream()
        exporter.write(sample(), ExportFormat.PNG, out)
        val bytes = out.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals(ReceiptRenderer.PNG_WIDTH_PX, bounds.outWidth)
        assertTrue("alto adaptado al contenido", bounds.outHeight > 1000)
        saveSample("muestra-pagado.png", bytes)
    }

    @Test fun `la imagen crece con la cantidad de productos`() {
        fun height(r: Receipt): Int {
            val out = ByteArrayOutputStream()
            exporter.write(r, ExportFormat.PNG, out)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
            return opts.outHeight
        }
        assertTrue(height(sample(items = 6)) > height(sample(items = 1)))
    }

    @Test fun `PDF con muchos productos se divide en paginas sin cortar filas`() {
        val renderer = ReceiptRenderer()
        val layout = renderer.layout(sample(items = 30))
        val pages = renderer.paginate(layout)
        assertTrue(pages.size > 1)
        assertEquals(layout.blocks.size, pages.sumOf { it.size })
        // Ninguna página excede el alto útil de A5.
        val usable = ReceiptRenderer.PAGE_HEIGHT - 2 * ReceiptRenderer.MARGIN - ReceiptRenderer.PAGE_FOOTER
        pages.forEach { page -> assertTrue(page.sumOf { it.height.toDouble() } <= usable) }
    }

    @Test fun `muestras de pendiente y anulado`() {
        listOf(PaymentStatus.PENDING to "muestra-pendiente.png", PaymentStatus.VOID to "muestra-anulado.png").forEach { (status, name) ->
            val out = ByteArrayOutputStream()
            exporter.write(sample(status = status, items = 3, discount = 10_000), ExportFormat.PNG, out)
            assertTrue(out.size() > 0)
            saveSample(name, out.toByteArray())
        }
    }

    @Test fun `exportar a cache para compartir no depende del registro`() = runBlocking {
        val file = exporter.exportToCache(sample(), ExportFormat.PNG)
        assertTrue(file.exists() && file.length() > 0)
        assertEquals("RECIBO-0001-20261123.png", file.name)
        // Exportar de nuevo reemplaza el archivo, no crea duplicados.
        val again = exporter.exportToCache(sample(), ExportFormat.PNG)
        assertEquals(file.absolutePath, again.absolutePath)
        assertEquals(1, file.parentFile!!.listFiles()!!.count { it.name.startsWith("RECIBO-0001") && it.extension == "png" })
    }

    /** Guarda muestras visuales en app/build/receipt-samples para revisarlas a mano. */
    private fun saveSample(name: String, bytes: ByteArray) {
        val dir = File("build/receipt-samples").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
    }
}
