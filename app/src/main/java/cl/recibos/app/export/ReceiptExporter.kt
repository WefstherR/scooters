package cl.recibos.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.util.ExportFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    PNG("png", "image/png", "Imagen"),
}

/**
 * Genera archivos PDF/PNG a partir de un comprobante guardado. Los archivos se crean desde
 * los datos almacenados, por lo que exportar de nuevo nunca duplica el registro.
 */
class ReceiptExporter(
    private val context: Context,
    private val renderer: ReceiptRenderer = ReceiptRenderer(),
) {

    fun fileName(receipt: Receipt, format: ExportFormat): String {
        val number = requireNotNull(receipt.number) { "Solo se exportan comprobantes emitidos." }
        return ExportFileName.of(number, receipt.dateTime.toLocalDate(), format.extension)
    }

    /** Escribe el comprobante en el formato indicado. */
    fun write(receipt: Receipt, format: ExportFormat, out: OutputStream) {
        val layout = renderer.layout(receipt)
        when (format) {
            ExportFormat.PDF -> writePdf(layout, out)
            ExportFormat.PNG -> {
                val bitmap = renderer.renderBitmap(layout)
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "No se pudo crear la imagen." }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun writePdf(layout: ReceiptRenderer.ReceiptLayout, out: OutputStream) {
        val document = PdfDocument()
        try {
            val pages = renderer.paginate(layout)
            pages.forEachIndexed { index, blocks ->
                val info = PdfDocument.PageInfo.Builder(
                    ReceiptRenderer.PAGE_WIDTH.toInt(),
                    ReceiptRenderer.PAGE_HEIGHT.toInt(),
                    index + 1,
                ).create()
                val page = document.startPage(info)
                renderer.drawPdfPage(page.canvas, layout, blocks, index, pages.size)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Vista previa en pantalla, generada con el mismo diseño que los archivos. */
    suspend fun preview(receipt: Receipt, widthPx: Int, previewNumber: Int?): Bitmap = withContext(Dispatchers.Default) {
        renderer.renderBitmap(renderer.layout(receipt, previewNumber), widthPx)
    }

    /** Archivo temporal (caché privada) para compartir mediante FileProvider. */
    suspend fun exportToCache(receipt: Receipt, format: ExportFormat): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        // Limpia exportaciones antiguas para no acumular archivos en caché.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > CACHE_TTL_MS }?.forEach { it.delete() }
        val file = File(dir, fileName(receipt, format))
        file.outputStream().use { write(receipt, format, it) }
        file
    }

    /**
     * Guarda en Descargas/Recibos usando MediaStore (Android 10+): no requiere permisos de
     * almacenamiento. Si el archivo ya existe se reemplaza en lugar de duplicarse.
     */
    suspend fun saveToDownloads(receipt: Receipt, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = fileName(receipt, format)
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_SUBDIR + "/"

        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, relativePath),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) Uri.withAppendedPath(collection, cursor.getLong(0).toString()) else null }

        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("No se pudo crear el archivo en Descargas.")

        try {
            resolver.openOutputStream(uri, "wt")?.use { write(receipt, format, it) }
                ?: error("No se pudo escribir el archivo.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            if (existing == null) resolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    companion object {
        const val EXPORT_DIR = "exports"
        const val DOWNLOAD_SUBDIR = "Recibos"
        private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}
