package cl.recibos.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migraciones de la base de datos: conservan todos los comprobantes existentes. */
object Migrations {

    /**
     * v1 → v2: RUT del cliente, número reservado (borrar y rehacer) y tabla de medios de pago.
     * Cada comprobante existente pasa a tener un único medio de pago por el total.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `client_rut` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `receipts` ADD COLUMN `reserved_number` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `receipt_payments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receipt_id` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `method` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
                    "`reference` TEXT NOT NULL, FOREIGN KEY(`receipt_id`) REFERENCES `receipts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_payments_receipt_id` ON `receipt_payments` (`receipt_id`)")
            db.execSQL(
                "INSERT INTO `receipt_payments` (`receipt_id`, `position`, `method`, `amount`, `reference`) " +
                    "SELECT `id`, 0, `payment_method`, `total`, `payment_reference` FROM `receipts`",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
