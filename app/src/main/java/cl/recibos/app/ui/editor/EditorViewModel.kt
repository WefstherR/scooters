package cl.recibos.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.recibos.app.data.repository.ReceiptRepository
import cl.recibos.app.data.repository.SellerRepository
import cl.recibos.app.domain.model.ClientInfo
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentPart
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.model.ReceiptItem
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.ReceiptValidator
import cl.recibos.app.domain.util.Rut
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Fila editable del formulario. Los montos se guardan como texto de dígitos. */
data class ItemForm(
    val key: Long,
    val name: String = "",
    val description: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
) {
    fun toItem() = ReceiptItem(
        name = name,
        description = description,
        quantity = quantity.toIntOrNull() ?: 0,
        unitPrice = Clp.parse(unitPrice) ?: 0,
    )
}

/** Un medio dentro de la forma de pago. El monto solo se usa con pago dividido. */
data class PaymentForm(
    val key: Long,
    val method: PaymentMethod,
    val amount: String = "",
    val reference: String = "",
) {
    fun toPart() = PaymentPart(
        method = method,
        amount = Clp.parse(amount) ?: 0,
        reference = if (method == PaymentMethod.CASH) "" else reference,
    )
}

data class EditorUiState(
    val loading: Boolean = true,
    val draftId: Long = 0,
    val nextNumber: Int = 1,
    val dateTime: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    val status: PaymentStatus = PaymentStatus.PAID,
    val items: List<ItemForm> = listOf(ItemForm(key = 1)),
    val discount: String = "",
    val payments: List<PaymentForm> = listOf(PaymentForm(key = 1, method = PaymentMethod.TRANSFER)),
    val reservedNumber: Int? = null,
    val clientName: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientRut: String = "",
    val showErrors: Boolean = false,
    val busy: Boolean = false,
    val sellerConfigured: Boolean = true,
) {
    fun toReceipt(): Receipt = Receipt(
        id = draftId,
        dateTime = dateTime,
        status = status,
        client = ClientInfo(clientName, clientPhone, clientEmail, Rut.format(clientRut)),
        items = items.map { it.toItem() },
        discount = Clp.parse(discount) ?: 0,
        payments = payments.map { it.toPart() },
        reservedNumber = reservedNumber,
    )

    val isSplit: Boolean get() = payments.size > 1

    /** Número que tendrá al emitirse. */
    val displayNumber: Int get() = reservedNumber ?: nextNumber

    /** Monto aún sin asignar en un pago dividido (negativo = exceso). */
    val unassigned: Long
        get() = runCatching { toReceipt().totals.total }.getOrDefault(0) -
            payments.sumOf { Clp.parse(it.amount) ?: 0 }

    val validation: ReceiptValidator.Result get() = ReceiptValidator.validate(toReceipt())

    /** Contenido comparable para saber si hay cambios sin guardar. */
    fun contentKey(): Any = listOf(
        dateTime, status, items.map { listOf(it.name, it.description, it.quantity, it.unitPrice) },
        discount, payments.map { listOf(it.method, it.amount, it.reference) }, clientName, clientPhone, clientEmail, clientRut,
    )
}

sealed interface EditorEvent {
    data class OpenPreview(val id: Long) : EditorEvent
    data object Close : EditorEvent
    data class Message(val text: String) : EditorEvent
}

class EditorViewModel(
    private val receipts: ReceiptRepository,
    private val seller: SellerRepository,
    private val receiptId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var savedKey: Any? = null
    private var nextKey = 2L

    init {
        viewModelScope.launch {
            val next = receipts.nextNumber()
            val configured = seller.current().isConfigured
            val draft = receiptId.takeIf { it != 0L }?.let { receipts.get(it) }?.takeIf { it.isDraft }
            val initial = if (draft != null) {
                EditorUiState(
                    loading = false,
                    draftId = draft.id,
                    nextNumber = next,
                    dateTime = draft.dateTime,
                    status = if (draft.status == PaymentStatus.VOID) PaymentStatus.PAID else draft.status,
                    items = draft.items.map { item ->
                        ItemForm(
                            key = nextKey++,
                            name = item.name,
                            description = item.description,
                            quantity = item.quantity.toString(),
                            unitPrice = item.unitPrice.takeIf { it > 0 }?.toString().orEmpty(),
                        )
                    }.ifEmpty { listOf(ItemForm(key = nextKey++)) },
                    discount = draft.discount.takeIf { it > 0 }?.toString().orEmpty(),
                    payments = draft.payments.map { part ->
                        PaymentForm(
                            key = nextKey++,
                            method = part.method,
                            amount = if (draft.payments.size > 1) part.amount.takeIf { it > 0 }?.toString().orEmpty() else "",
                            reference = part.reference,
                        )
                    }.ifEmpty { listOf(PaymentForm(key = nextKey++, method = PaymentMethod.TRANSFER)) },
                    reservedNumber = draft.reservedNumber,
                    clientName = draft.client.name,
                    clientPhone = draft.client.phone,
                    clientEmail = draft.client.email,
                    clientRut = draft.client.rut,
                    sellerConfigured = configured,
                ).also { savedKey = it.contentKey() }
            } else {
                EditorUiState(
                    loading = false,
                    nextNumber = next,
                    payments = listOf(PaymentForm(key = nextKey++, method = seller.lastPaymentMethod.first())),
                    sellerConfigured = configured,
                ).also { savedKey = it.contentKey() }
            }
            _state.value = initial
        }
    }

    val hasUnsavedChanges: Boolean
        get() = !_state.value.loading && _state.value.contentKey() != savedKey

    val isExistingDraft: Boolean get() = _state.value.draftId != 0L

    fun updateItem(key: Long, transform: (ItemForm) -> ItemForm) = _state.update { s ->
        s.copy(items = s.items.map { if (it.key == key) transform(it) else it })
    }

    fun addItem() = _state.update { it.copy(items = it.items + ItemForm(key = nextKey++)) }

    fun removeItem(key: Long) = _state.update { s ->
        val remaining = s.items.filterNot { it.key == key }
        s.copy(items = remaining.ifEmpty { listOf(ItemForm(key = nextKey++)) })
    }

    fun setDate(date: LocalDate) = _state.update { it.copy(dateTime = LocalDateTime.of(date, it.dateTime.toLocalTime())) }
    fun setTime(time: LocalTime) = _state.update { it.copy(dateTime = LocalDateTime.of(it.dateTime.toLocalDate(), time)) }
    fun setStatus(status: PaymentStatus) = _state.update { it.copy(status = status) }
    fun setDiscount(value: String) = _state.update { it.copy(discount = value) }
    private fun updatePayment(key: Long, transform: (PaymentForm) -> PaymentForm) = _state.update { s ->
        s.copy(payments = s.payments.map { if (it.key == key) transform(it) else it })
    }

    /** Cambia el medio de una parte. Si otra parte ya lo usaba, intercambian medios. */
    fun setPaymentMethod(key: Long, method: PaymentMethod) = _state.update { s ->
        val current = s.payments.firstOrNull { it.key == key }?.method ?: return@update s
        s.copy(
            payments = s.payments.map {
                when {
                    it.key == key -> it.copy(method = method)
                    it.method == method -> it.copy(method = current)
                    else -> it
                }
            },
        )
    }

    fun setPaymentAmount(key: Long, value: String) = updatePayment(key) { it.copy(amount = value) }
    fun setPaymentReference(key: Long, value: String) = updatePayment(key) { it.copy(reference = value.take(60)) }

    /** Asigna a esta parte lo que falta para completar el total. */
    fun fillRemaining(key: Long) = _state.update { s ->
        val missing = s.unassigned
        if (missing == 0L) return@update s
        s.copy(
            payments = s.payments.map {
                if (it.key != key) it
                else it.copy(amount = ((Clp.parse(it.amount) ?: 0) + missing).coerceAtLeast(0).takeIf { v -> v > 0 }?.toString().orEmpty())
            },
        )
    }

    /** Agrega otro medio de pago (máximo 3, sin repetir). */
    fun addPaymentPart() = _state.update { s ->
        if (s.payments.size >= ReceiptValidator.MAX_PAYMENT_PARTS) return@update s
        val unused = PaymentMethod.entries.firstOrNull { m -> s.payments.none { it.method == m } } ?: return@update s
        val total = runCatching { s.toReceipt().totals.total }.getOrDefault(0)
        // Al pasar de 1 a 2 medios, el primero conserva el total para que el vendedor lo reparta.
        val base = if (s.payments.size == 1) {
            s.payments.map { it.copy(amount = total.takeIf { t -> t > 0 }?.toString().orEmpty()) }
        } else {
            s.payments
        }
        s.copy(payments = base + PaymentForm(key = nextKey++, method = unused))
    }

    fun removePaymentPart(key: Long) = _state.update { s ->
        val remaining = s.payments.filterNot { it.key == key }
        if (remaining.isEmpty()) return@update s
        s.copy(payments = if (remaining.size == 1) remaining.map { it.copy(amount = "") } else remaining)
    }
    fun setClientName(value: String) = _state.update { it.copy(clientName = value.take(80)) }
    fun setClientPhone(value: String) = _state.update { it.copy(clientPhone = value.filter { c -> c.isDigit() || c in "+ " }.take(20)) }
    fun setClientEmail(value: String) = _state.update { it.copy(clientEmail = value.trim().take(80)) }
    fun setClientRut(value: String) = _state.update {
        it.copy(clientRut = value.uppercase().filter { c -> c.isDigit() || c == 'K' || c == '.' || c == '-' }.take(12))
    }

    /** Valida, guarda el borrador y abre la vista previa. */
    fun preview() {
        val current = _state.value
        if (current.busy) return
        if (!current.validation.isValid) {
            _state.update { it.copy(showErrors = true) }
            viewModelScope.launch {
                _events.send(EditorEvent.Message(current.validation.firstMessage ?: "Revisa los datos marcados."))
            }
            return
        }
        viewModelScope.launch {
            persist()?.let { _events.send(EditorEvent.OpenPreview(it)) }
        }
    }

    fun saveDraftAndClose() {
        viewModelScope.launch {
            if (persist() != null) {
                _events.send(EditorEvent.Message("Borrador guardado"))
                _events.send(EditorEvent.Close)
            }
        }
    }

    private suspend fun persist(): Long? {
        _state.update { it.copy(busy = true) }
        return try {
            val snapshot = _state.value
            val id = receipts.saveDraft(snapshot.toReceipt())
            seller.rememberPaymentMethod(snapshot.payments.first().method)
            _state.update { it.copy(draftId = id, busy = false) }
            savedKey = snapshot.contentKey()
            id
        } catch (e: Exception) {
            _state.update { it.copy(busy = false) }
            _events.send(EditorEvent.Message(e.message ?: "No se pudo guardar el borrador."))
            null
        }
    }

    fun deleteDraft() {
        val id = _state.value.draftId
        viewModelScope.launch {
            if (id != 0L) receipts.deleteDraft(id)
            _events.send(EditorEvent.Message("Borrador eliminado"))
            _events.send(EditorEvent.Close)
        }
    }
}
