package cl.recibos.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Copia el logo elegido a almacenamiento privado, reducido a un tamaño razonable.
 * Cada logo nuevo usa un archivo distinto, así los comprobantes ya emitidos conservan
 * el logo que tenían al emitirse.
 */
class LogoStorage(private val context: Context) {

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = max(info.size.width, info.size.height)
            if (largest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / largest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        val dir = File(context.filesDir, "logos").apply { mkdirs() }
        val file = File(dir, "logo_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file.absolutePath
    }

    private companion object {
        const val MAX_SIDE = 512
    }
}
