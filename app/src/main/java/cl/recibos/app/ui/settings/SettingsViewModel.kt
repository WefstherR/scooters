package cl.recibos.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.recibos.app.data.repository.SellerRepository
import cl.recibos.app.domain.model.ReceiptAccent
import cl.recibos.app.domain.model.SellerProfile
import cl.recibos.app.domain.util.ReceiptValidator
import cl.recibos.app.export.LogoStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val profile: SellerProfile = SellerProfile(),
    val saved: SellerProfile = SellerProfile(),
    val busy: Boolean = false,
    val showErrors: Boolean = false,
) {
    val emailError: String?
        get() = profile.email.takeIf { it.isNotBlank() && !ReceiptValidator.isValidEmail(it) }?.let { "El correo no parece válido." }
    val nameError: String? get() = if (profile.name.isBlank()) "Escribe tu nombre o el de tu negocio." else null
    val hasChanges: Boolean get() = profile != saved
}

class SettingsViewModel(
    private val seller: SellerRepository,
    private val logos: LogoStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = seller.current()
            _state.value = SettingsUiState(loading = false, profile = current, saved = current)
        }
    }

    private fun edit(transform: (SellerProfile) -> SellerProfile) = _state.update { it.copy(profile = transform(it.profile)) }

    fun setName(v: String) = edit { it.copy(name = v.take(80)) }
    fun setPhone(v: String) = edit { it.copy(phone = v.filter { c -> c.isDigit() || c in "+ " }.take(20)) }
    fun setEmail(v: String) = edit { it.copy(email = v.trim().take(80)) }
    fun setThanks(v: String) = edit { it.copy(thanksMessage = v.take(160)) }
    fun setShowLogo(v: Boolean) = edit { it.copy(showLogo = v) }
    fun setAccent(v: ReceiptAccent) = edit { it.copy(accent = v) }
    fun removeLogo() = edit { it.copy(logoPath = null) }

    fun onLogoPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val path = logos.import(uri)
                edit { it.copy(logoPath = path, showLogo = true) }
            } catch (e: Exception) {
                _messages.send("No se pudo cargar la imagen. Prueba con otra.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.nameError != null || s.emailError != null) {
            _state.update { it.copy(showErrors = true) }
            viewModelScope.launch { _messages.send(s.nameError ?: s.emailError ?: "") }
            return
        }
        viewModelScope.launch {
            seller.save(s.profile)
            _state.update { it.copy(saved = it.profile, showErrors = false) }
            _messages.send("Datos guardados. Se usarán en los próximos comprobantes.")
            _saved.send(Unit)
        }
    }
}
