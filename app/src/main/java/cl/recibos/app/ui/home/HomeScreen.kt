package cl.recibos.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.recibos.app.AppContainer
import cl.recibos.app.R
import cl.recibos.app.data.repository.DateFilter
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.DateFormats
import cl.recibos.app.domain.util.ReceiptNumber
import cl.recibos.app.ui.components.StatusBadge
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onNewReceipt: () -> Unit,
    onOpenReceipt: (Receipt) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(container.receipts, container.seller) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Recibos") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración del vendedor")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewReceipt,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo comprobante") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.sellerConfigured) {
                    item(key = "setup") { SetupBanner(onOpenSettings) }
                }
                item(key = "summary") { SummaryCard(state.summary, state.filter.date) }
                item(key = "search") {
                    SearchAndFilters(
                        state = state,
                        onQuery = vm::onQueryChange,
                        onDate = vm::onDateFilter,
                        onMethod = vm::onMethodFilter,
                    )
                }
                if (state.isEmpty) {
                    item(key = "empty") {
                        EmptyState(filtered = state.hasFilters, onClear = vm::clearFilters, onNew = onNewReceipt)
                    }
                }
                if (state.drafts.isNotEmpty()) {
                    item(key = "drafts-title") { SectionTitle("Borradores") }
                    items(state.drafts, key = { "d" + it.id }) { ReceiptRow(it) { onOpenReceipt(it) } }
                }
                if (state.issued.isNotEmpty()) {
                    item(key = "issued-title") { SectionTitle("Historial") }
                    items(state.issued, key = { "r" + it.id }) { ReceiptRow(it) { onOpenReceipt(it) } }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SetupBanner(onOpenSettings: () -> Unit) {
    Card(
        onClick = onOpenSettings,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Configura tus datos de vendedor", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tu nombre, logo y contacto aparecerán en cada comprobante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.Settings, contentDescription = null)
        }
    }
}

@Composable
private fun SummaryCard(summary: HomeSummary, date: DateFilter) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "RECIBIDO · " + date.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
            Text(
                Clp.format(summary.receivedAmount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                SummaryStat("Emitidos", summary.issuedCount.toString(), Modifier.weight(1f))
                SummaryStat(
                    "Pendiente" + if (summary.pendingCount > 0) " (${summary.pendingCount})" else "",
                    Clp.format(summary.pendingAmount),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilters(
    state: HomeUiState,
    onQuery: (String) -> Unit,
    onDate: (DateFilter) -> Unit,
    onMethod: (PaymentMethod?) -> Unit,
) {
    var dateMenu by remember { mutableStateOf(false) }
    var rangeDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQuery,
            placeholder = { Text("Buscar por número, cliente o producto") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                FilterChip(
                    selected = state.filter.date != DateFilter.All,
                    onClick = { dateMenu = true },
                    label = { Text(state.filter.date.label) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                )
                DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                    listOf(DateFilter.All, DateFilter.Today, DateFilter.Last7Days, DateFilter.ThisMonth, DateFilter.LastMonth)
                        .forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { onDate(option); dateMenu = false })
                        }
                    DropdownMenuItem(text = { Text("Rango personalizado…") }, onClick = { dateMenu = false; rangeDialog = true })
                }
            }
            FilterChip(selected = state.filter.method == null, onClick = { onMethod(null) }, label = { Text("Todos") })
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = state.filter.method == method,
                    onClick = { onMethod(if (state.filter.method == method) null else method) },
                    label = { Text(method.label) },
                )
            }
        }
    }

    if (rangeDialog) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { rangeDialog = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val start = rangeState.selectedStartDateMillis!!.toLocalDateUtc()
                        val end = (rangeState.selectedEndDateMillis ?: rangeState.selectedStartDateMillis!!).toLocalDateUtc()
                        onDate(DateFilter.Custom(start, end))
                        rangeDialog = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { rangeDialog = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(
                state = rangeState,
                title = { Text("Selecciona el rango", Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun Long.toLocalDateUtc() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    receipt.number?.let { "N.º " + ReceiptNumber.format(it) } ?: "Sin número",
                    style = MaterialTheme.typography.titleSmall,
                )
                val products = receipt.items.firstOrNull()?.name?.ifBlank { null }?.let { first ->
                    if (receipt.items.size > 1) "$first +${receipt.items.size - 1}" else first
                } ?: "Sin productos"
                val headline = receipt.client.name.ifBlank { null }?.let { "$it · $products" } ?: products
                Text(headline, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    DateFormats.dateTime(receipt.dateTime) + " · " + receipt.paymentMethod.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Clp.format(receipt.totals.total), style = MaterialTheme.typography.titleMedium)
                StatusBadge(receipt.status, receipt.isDraft)
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onClear: () -> Unit, onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_receipt),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        if (filtered) {
            Text("Sin resultados", style = MaterialTheme.typography.titleMedium)
            Text(
                "No hay comprobantes que coincidan con la búsqueda o los filtros.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear) { Text("Quitar filtros") }
        } else {
            Text("Aún no hay comprobantes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Registra una venta y comparte su recibo en menos de un minuto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surface)
            TextButton(onClick = onNew) { Text("Crear el primero") }
        }
    }
}
