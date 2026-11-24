package cl.recibos.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.recibos.app.data.local.AppDatabase
import cl.recibos.app.data.local.Migrations
import cl.recibos.app.data.local.toDomain
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentPart
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifica que actualizar la app conserve los comprobantes ya emitidos: crea una base de datos
 * con el esquema exacto de la versión 1 (app/schemas/.../1.json), la abre con la versión actual
 * y Room valida que el esquema migrado coincida con el esperado.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `version 1 a 2 conserva comprobantes y crea su medio de pago`() = runBlocking {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createVersion1(file)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val receipt = db.receiptDao().getById(1)!!.toDomain()
            assertEquals(1, receipt.number)
            assertEquals("Ana", receipt.client.name)
            assertEquals("", receipt.client.rut)
            assertEquals(null, receipt.reservedNumber)
            assertEquals(listOf(PaymentPart(PaymentMethod.TRANSFER, 215_000, "OP-1")), receipt.payments)
        } finally {
            db.close()
        }
    }

    private fun createVersion1(file: File) {
        val schema = JSONObject(File("schemas/cl.recibos.app.data.local.AppDatabase/1.json").readText())
            .getJSONObject("database")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL(
            "INSERT INTO receipts (id, number, is_draft, date_time, status, client_name, client_phone, client_email, " +
                "discount, subtotal, total, payment_method, payment_reference, seller_name, seller_phone, seller_email, " +
                "seller_thanks, seller_logo_path, accent, created_at, updated_at, issued_at, status_changed_at) " +
                "VALUES (1, 1, 0, 0, 'PAID', 'Ana', '', '', 0, 215000, 215000, 'TRANSFER', 'OP-1', 'Tienda', '', '', '', NULL, " +
                "'GRAPHITE', 0, 0, 0, 0)",
        )
        sqlite.execSQL("INSERT INTO receipt_items (receipt_id, position, name, description, quantity, unit_price) VALUES (1, 0, 'Scooter', '', 1, 215000)")
        sqlite.version = 1
        sqlite.close()
    }
}
