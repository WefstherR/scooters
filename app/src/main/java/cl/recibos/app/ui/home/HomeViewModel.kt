package cl.recibos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.recibos.app.data.repository.DateFilter
import cl.recibos.app.data.repository.ReceiptFilter
import cl.recibos.app.data.repository.ReceiptRepository
import cl.recibos.app.data.repository.SellerRepository
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeSummary(
    val issuedCount: Int = 0,
    val receivedAmount: Long = 0,
    val pendingAmount: Long = 0,
    val pendingCount: Int = 0,
) {
    companion object {
        fun of(receipts: List<Receipt>): HomeSummary {
            val issued = receipts.filter { !it.isDraft && it.status != PaymentStatus.VOID }
            val pending = issued.filter { it.status == PaymentStatus.PENDING }
            return HomeSummary(
                issuedCount = issued.size,
                receivedAmount = issued.filter { it.status == PaymentStatus.PAID }.sumOf { it.totals.total },
                pendingAmount = pending.sumOf { it.totals.total },
                pendingCount = pending.size,
            )
        }
    }
}

data class HomeUiState(
    val filter: ReceiptFilter = ReceiptFilter(),
    val drafts: List<Receipt> = emptyList(),
    val issued: List<Receipt> = emptyList(),
    val summary: HomeSummary = HomeSummary(),
    val sellerConfigured: Boolean = true,
) {
    val hasFilters: Boolean get() = filter.query.isNotBlank() || filter.date != DateFilter.All || filter.method != null
    val isEmpty: Boolean get() = drafts.isEmpty() && issued.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(receipts: ReceiptRepository, seller: SellerRepository) : ViewModel() {

    private val filter = MutableStateFlow(ReceiptFilter())

    private val results = filter
        .debounce { if (it.query.isEmpty()) 0 else 200 }
        .flatMapLatest { f -> receipts.observe(f).map { f to it } }

    val state: StateFlow<HomeUiState> = combine(filter, results, seller.profile) { current, (_, list), profile ->
        HomeUiState(
            filter = current,
            drafts = list.filter { it.isDraft },
            issued = list.filter { !it.isDraft },
            summary = HomeSummary.of(list),
            sellerConfigured = profile.isConfigured,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }
    fun onDateFilter(date: DateFilter) = filter.update { it.copy(date = date) }
    fun onMethodFilter(method: PaymentMethod?) = filter.update { it.copy(method = method) }
    fun clearFilters() = filter.update { ReceiptFilter() }
}
