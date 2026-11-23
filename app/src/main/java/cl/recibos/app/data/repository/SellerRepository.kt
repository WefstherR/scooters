package cl.recibos.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.ReceiptAccent
import cl.recibos.app.domain.model.SellerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Datos del vendedor y preferencias, guardados con DataStore. */
class SellerRepository(private val store: DataStore<Preferences>) {

    val profile: Flow<SellerProfile> = store.data.map { p ->
        SellerProfile(
            name = p[NAME].orEmpty(),
            phone = p[PHONE].orEmpty(),
            email = p[EMAIL].orEmpty(),
            thanksMessage = p[THANKS] ?: SellerProfile().thanksMessage,
            logoPath = p[LOGO],
            showLogo = p[SHOW_LOGO] ?: true,
            accent = p[ACCENT]?.let { runCatching { ReceiptAccent.valueOf(it) }.getOrNull() } ?: ReceiptAccent.GRAPHITE,
        )
    }

    val lastPaymentMethod: Flow<PaymentMethod> = store.data.map { p ->
        p[LAST_METHOD]?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() } ?: PaymentMethod.TRANSFER
    }

    suspend fun current(): SellerProfile = profile.first()

    suspend fun save(profile: SellerProfile) {
        store.edit { p ->
            p[NAME] = profile.name.trim()
            p[PHONE] = profile.phone.trim()
            p[EMAIL] = profile.email.trim()
            p[THANKS] = profile.thanksMessage.trim()
            if (profile.logoPath != null) p[LOGO] = profile.logoPath else p.remove(LOGO)
            p[SHOW_LOGO] = profile.showLogo
            p[ACCENT] = profile.accent.name
        }
    }

    suspend fun rememberPaymentMethod(method: PaymentMethod) {
        store.edit { it[LAST_METHOD] = method.name }
    }

    private companion object {
        val NAME = stringPreferencesKey("seller_name")
        val PHONE = stringPreferencesKey("seller_phone")
        val EMAIL = stringPreferencesKey("seller_email")
        val THANKS = stringPreferencesKey("seller_thanks")
        val LOGO = stringPreferencesKey("seller_logo_path")
        val SHOW_LOGO = booleanPreferencesKey("seller_show_logo")
        val ACCENT = stringPreferencesKey("receipt_accent")
        val LAST_METHOD = stringPreferencesKey("last_payment_method")
    }
}
