package cl.recibos.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import cl.recibos.app.data.local.AppDatabase
import cl.recibos.app.data.repository.ReceiptRepository
import cl.recibos.app.data.repository.SellerRepository
import cl.recibos.app.export.LogoStorage
import cl.recibos.app.export.ReceiptExporter

private val Context.sellerDataStore by preferencesDataStore(name = "seller")

/** Contenedor de dependencias manual: simple, sin librerías extra y fácil de reemplazar por Hilt. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val receipts: ReceiptRepository by lazy { ReceiptRepository(database) }
    val seller: SellerRepository by lazy { SellerRepository(appContext.sellerDataStore) }
    val exporter: ReceiptExporter by lazy { ReceiptExporter(appContext) }
    val logos: LogoStorage by lazy { LogoStorage(appContext) }
}

class RecibosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
