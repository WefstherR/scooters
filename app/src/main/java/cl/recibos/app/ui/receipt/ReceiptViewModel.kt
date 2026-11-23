package cl.recibos.app.ui.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.recibos.app.data.repository.ReceiptRepository
import cl.recibos.app.data.repository.SellerRepository
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.SellerProfile
import cl.recibos.app.export.ExportFormat
import cl.recibos.app.export.ReceiptExporter
import cl.recibos.app.export.ShareIntents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShareTarget { WHATSAPP, EMAIL, OTHER }

data class ReceiptUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    /** Comprobante tal como se verá: en borradores usa los datos actuales del vendedor. */
    val receipt: Receipt? = null,
    val nextNumber: Int = 1,
    val preview: Bitmap? = null,
    val shareFormat: ExportFormat = ExportFormat.PDF,
    val busy: Boolean = false,
    val seller: SellerProfile = SellerProfile(),
)

sealed interface ReceiptEvent {
    data class Message(val text: String, val openUri: Uri? = null, val mimeType: String? = null) : ReceiptEvent
    data class Launch(val intent: Intent) : ReceiptEvent
    data object Issued : ReceiptEvent
    data class OpenDraft(val id: Long) : ReceiptEvent
    data object Close : ReceiptEvent
}

class ReceiptViewModel(
    private val receiptId: Long,
    private val receipts: ReceiptRepository,
    private val seller: SellerRepository,
    private val exporter: ReceiptExporter,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state.asStateFlow()

    private val _events = Channel<ReceiptEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(receipts.observeById(receiptId), seller.profile) { stored, profile ->
                // Un comprobante emitido conserva sus datos de vendedor; un borrador muestra los actuales.
                stored?.let { if (it.isDraft) it.copy(seller = profile.toSnapshot()) else it } to profile
            }.distinctUntilChanged().collect { (receipt, profile) ->
                if (receipt == null) {
                    _state.update { it.copy(loading = false, missing = true, receipt = null) }
                    return@collect
                }
                val next = receipts.nextNumber()
                _state.update { it.copy(loading = false, receipt = receipt, nextNumber = next, seller = profile) }
                val bitmap = runCatching { exporter.preview(receipt, PREVIEW_WIDTH, next) }.getOrNull()
                _state.update { it.copy(preview = bitmap) }
            }
        }
    }

    fun setShareFormat(format: ExportFormat) = _state.update { it.copy(shareFormat = format) }

    /** Emite el borrador después de la confirmación manual del vendedor. */
    fun issue() = runBusy {
        val profile = seller.current()
        val issued = receipts.issue(receiptId, profile.toSnapshot())
        seller.rememberPaymentMethod(issued.paymentMethod)
        _events.send(ReceiptEvent.Issued)
    }

    fun markAsPaid() = runBusy {
        receipts.markAsPaid(receiptId)
        _events.send(ReceiptEvent.Message("Comprobante marcado como pagado"))
    }

    fun void() = runBusy {
        receipts.void(receiptId)
        _events.send(ReceiptEvent.Message("Comprobante anulado. Se conserva en el historial."))
    }

    fun duplicate() = runBusy {
        val id = receipts.duplicateAsDraft(receiptId)
        _events.send(ReceiptEvent.OpenDraft(id))
    }

    fun deleteDraft() = runBusy {
        receipts.deleteDraft(receiptId)
        _events.send(ReceiptEvent.Close)
    }

    fun download(format: ExportFormat) = runBusy {
        val receipt = requireIssued()
        val uri = exporter.saveToDownloads(receipt, format)
        _events.send(
            ReceiptEvent.Message(
                "Guardado en Descargas/${ReceiptExporter.DOWNLOAD_SUBDIR}: ${exporter.fileName(receipt, format)}",
                openUri = uri,
                mimeType = format.mimeType,
            ),
        )
    }

    fun share(target: ShareTarget) = runBusy {
        val receipt = requireIssued()
        val format = _state.value.shareFormat
        val file = exporter.exportToCache(receipt, format)
        val uri = ShareIntents.uriFor(appContext, file)
        val intent = when (target) {
            ShareTarget.WHATSAPP -> ShareIntents.whatsApp(appContext, uri, format.mimeType, receipt)
                ?: run {
                    _events.send(ReceiptEvent.Message("WhatsApp no está instalado. Elige otra aplicación."))
                    ShareIntents.chooser(uri, format.mimeType, receipt)
                }
            ShareTarget.EMAIL -> ShareIntents.email(uri, format.mimeType, receipt)
            ShareTarget.OTHER -> ShareIntents.chooser(uri, format.mimeType, receipt)
        }
        _events.send(ReceiptEvent.Launch(intent))
    }

    private fun requireIssued(): Receipt {
        val receipt = _state.value.receipt ?: error("El comprobante ya no existe.")
        check(!receipt.isDraft) { "Primero debes emitir el comprobante." }
        return receipt
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                _events.send(ReceiptEvent.Message(e.message ?: "Ocurrió un error inesperado."))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        const val PREVIEW_WIDTH = 1080
    }
}
