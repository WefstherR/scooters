package cl.recibos.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.recibos.app.AppContainer
import cl.recibos.app.R
import cl.recibos.app.domain.model.PaymentMethod
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.DateFormats
import cl.recibos.app.domain.util.ReceiptNumber
import cl.recibos.app.domain.util.ReceiptValidator
import cl.recibos.app.domain.util.Rut
import cl.recibos.app.ui.theme.StatusColors
import cl.recibos.app.ui.components.MoneyField
import cl.recibos.app.ui.components.SectionCard
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    container: AppContainer,
    receiptId: Long,
    onBack: () -> Unit,
    onPreview: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: EditorViewModel = viewModel(
        key = "editor-$receiptId",
        factory = viewModelFactory { initializer { EditorViewModel(container.receipts, container.seller, receiptId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var exitDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.OpenPreview -> onPreview(event.id)
                EditorEvent.Close -> onBack()
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val requestBack = { if (vm.hasUnsavedChanges) exitDialog = true else onBack() }
    BackHandler(enabled = true) { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.draftId != 0L) "Editar borrador" else "Nuevo comprobante")
                        Text(
                            state.reservedNumber?.let { "N.º ${ReceiptNumber.format(it)} · rehaciendo comprobante borrado" }
                                ?: "N.º ${ReceiptNumber.format(state.nextNumber)} · se asigna al emitir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Guardar borrador") }, onClick = { menu = false; vm.saveDraftAndClose() })
                        if (state.draftId != 0L) {
                            DropdownMenuItem(text = { Text("Eliminar borrador") }, onClick = { menu = false; deleteDialog = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { TotalBar(state, onPreview = vm::preview) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.sellerConfigured) {
                    Surface(
                        onClick = onOpenSettings,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "Aún no configuras tus datos de vendedor. Toca aquí para agregarlos.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                ProductsSection(state, vm)
                PaymentSection(state, vm)
                ReceiptDataSection(state, vm)
                ClientSection(state, vm)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("¿Guardar como borrador?") },
            text = { Text("Puedes guardar los datos y terminar el comprobante más tarde.") },
            confirmButton = { TextButton(onClick = { exitDialog = false; vm.saveDraftAndClose() }) { Text("Guardar borrador") } },
            dismissButton = {
                TextButton(onClick = { exitDialog = false; onBack() }) {
                    Text("Descartar cambios", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("¿Eliminar borrador?") },
            text = {
                Text(
                    state.reservedNumber?.let {
                        "El borrador se eliminará y el N.º ${ReceiptNumber.format(it)} quedará sin usar " +
                            "(si era el último, lo tomará el próximo comprobante)."
                    } ?: "El borrador se eliminará. No afecta la numeración de los comprobantes.",
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteDialog = false; vm.deleteDraft() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TotalBar(state: EditorUiState, onPreview: () -> Unit) {
    val totals = runCatching { state.toReceipt().totals }.getOrNull()
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.status == PaymentStatus.PENDING) "Total pendiente" else "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    totals?.let { Clp.format(it.total) } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(onClick = onPreview, enabled = !state.busy, modifier = Modifier.height(52.dp)) {
                Text("Vista previa")
            }
        }
    }
}

@Composable
private fun ProductsSection(state: EditorUiState, vm: EditorViewModel) {
    val errors = if (state.showErrors) state.validation.itemErrors else emptyMap()
    val totals = runCatching { state.toReceipt().totals }.getOrNull()
    SectionCard(title = "Detalle de venta") {
        state.items.forEachIndexed { index, item ->
            key(item.key) {
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ItemEditor(
                    item = item,
                    index = index,
                    canRemove = state.items.size > 1,
                    error = errors[index],
                    autoFocus = index > 0 && index == state.items.lastIndex && item.name.isEmpty(),
                    onChange = { transform -> vm.updateItem(item.key, transform) },
                    onRemove = { vm.removeItem(item.key) },
                )
            }
        }
        TextButton(onClick = vm::addItem) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar producto")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val discountError = if (state.showErrors) state.validation.discountError else null
        MoneyField(
            value = state.discount,
            onValueChange = vm::setDiscount,
            label = "Descuento (opcional)",
            isError = discountError != null,
            supportingText = discountError,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth(),
        )
        if (totals != null) {
            AmountLine("Subtotal", Clp.format(totals.subtotal))
            if (totals.discount > 0) AmountLine("Descuento", "−" + Clp.format(totals.discount))
            AmountLine("Total", Clp.format(totals.total), strong = true)
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ItemEditor(
    item: ItemForm,
    index: Int,
    canRemove: Boolean,
    error: String?,
    autoFocus: Boolean,
    onChange: ((ItemForm) -> ItemForm) -> Unit,
    onRemove: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Producto ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Quitar producto ${index + 1}") }
            }
        }
        OutlinedTextField(
            value = item.name,
            onValueChange = { v -> onChange { it.copy(name = v.take(80)) } },
            label = { Text("Nombre del producto") },
            singleLine = true,
            isError = error != null && item.name.isBlank(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        OutlinedTextField(
            value = item.description,
            onValueChange = { v -> onChange { it.copy(description = v.take(120)) } },
            label = { Text("Modelo o descripción (opcional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            QuantityField(
                value = item.quantity,
                onValueChange = { v -> onChange { it.copy(quantity = v) } },
                modifier = Modifier.weight(0.42f),
            )
            MoneyField(
                value = item.unitPrice,
                onValueChange = { v -> onChange { it.copy(unitPrice = v) } },
                label = "Precio unitario",
                isError = error != null && (Clp.parse(item.unitPrice) ?: 0) <= 0,
                modifier = Modifier.weight(0.58f),
            )
        }
        val line = item.toItem().let { runCatching { it.lineTotal }.getOrDefault(0) }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (line > 0 && (item.quantity.toIntOrNull() ?: 0) > 1) {
            Text(
                "Subtotal: ${Clp.format(line)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuantityField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val qty = value.toIntOrNull() ?: 0
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).trimStart('0').take(4)) },
        label = { Text("Cantidad") },
        singleLine = true,
        isError = qty < 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        trailingIcon = {
            Column {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "Aumentar cantidad",
                    modifier = Modifier.size(22.dp).clickableStep { onValueChange((qty + 1).coerceAtMost(9999).toString()) },
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Disminuir cantidad",
                    modifier = Modifier.size(22.dp).clickableStep { onValueChange((qty - 1).coerceAtLeast(1).toString()) },
                )
            }
        },
        modifier = modifier,
    )
}

private fun Modifier.clickableStep(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSection(state: EditorUiState, vm: EditorViewModel) {
    val paymentError = if (state.showErrors) state.validation.paymentError else null
    SectionCard(title = if (state.isSplit) "Forma de pago · ${state.payments.size} medios" else "Método de pago") {
        if (!state.isSplit) {
            val part = state.payments.first()
            MethodSelector(selected = part.method, onSelect = { vm.setPaymentMethod(part.key, it) })
            AnimatedVisibility(visible = part.method != PaymentMethod.CASH) {
                OutlinedTextField(
                    value = part.reference,
                    onValueChange = { vm.setPaymentReference(part.key, it) },
                    label = { Text("N.º de operación o referencia (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = vm::addPaymentPart) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dividir el pago en 2 o 3 medios")
            }
        } else {
            state.payments.forEachIndexed { index, part ->
                key(part.key) {
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Medio ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removePaymentPart(part.key) }) {
                                Icon(Icons.Default.Close, "Quitar medio ${index + 1}")
                            }
                        }
                        MethodSelector(selected = part.method, onSelect = { vm.setPaymentMethod(part.key, it) })
                        MoneyField(
                            value = part.amount,
                            onValueChange = { vm.setPaymentAmount(part.key, it) },
                            label = "Monto en ${part.method.shortLabel.lowercase()}",
                            isError = paymentError != null && (Clp.parse(part.amount) ?: 0) <= 0,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.unassigned > 0) {
                            TextButton(onClick = { vm.fillRemaining(part.key) }) {
                                Text("Asignar saldo de ${Clp.format(state.unassigned)} aquí")
                            }
                        }
                        AnimatedVisibility(visible = part.method != PaymentMethod.CASH) {
                            OutlinedTextField(
                                value = part.reference,
                                onValueChange = { vm.setPaymentReference(part.key, it) },
                                label = { Text("N.º de operación (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            val unassigned = state.unassigned
            Text(
                when {
                    unassigned > 0 -> "Falta asignar ${Clp.format(unassigned)}"
                    unassigned < 0 -> "Asignaste ${Clp.format(-unassigned)} de más"
                    else -> "Montos completos ✓"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (unassigned == 0L) StatusColors.paid else MaterialTheme.colorScheme.error,
            )
            if (state.payments.size < ReceiptValidator.MAX_PAYMENT_PARTS) {
                TextButton(onClick = vm::addPaymentPart) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar otro medio")
                }
            }
        }
        if (paymentError != null) {
            Text(paymentError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Solo se registra lo declarado. La app no procesa ni verifica pagos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodSelector(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PaymentMethod.entries.forEachIndexed { index, method ->
            SegmentedButton(
                selected = selected == method,
                onClick = { onSelect(method) },
                shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                icon = {},
                label = { Text(if (method == PaymentMethod.TRANSFER) "Transferencia" else method.shortLabel, maxLines = 1) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptDataSection(state: EditorUiState, vm: EditorViewModel) {
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }

    SectionCard(title = "Datos del comprobante") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { dateDialog = true },
                label = { Text(DateFormats.date(state.dateTime)) },
                leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { timeDialog = true },
                label = { Text(state.dateTime.format(DateFormats.time)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_schedule), null, Modifier.size(18.dp)) },
            )
        }
        Text("Estado", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PaymentStatus.PAID, PaymentStatus.PENDING).forEach { status ->
                FilterChip(
                    selected = state.status == status,
                    onClick = { vm.setStatus(status) },
                    label = { Text(status.label) },
                )
            }
        }
        Text(
            if (state.status == PaymentStatus.PAID) {
                "Al emitir deberás confirmar que recibiste el pago."
            } else {
                "El comprobante indicará que el pago está pendiente. Podrás marcarlo como pagado después."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (dateDialog) {
        val initial = state.dateTime.toLocalDate().toEpochDay() * 86_400_000L
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    dateDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Cancelar") } },
        ) { DatePicker(state = picker, showModeToggle = false) }
    }
    if (timeDialog) {
        val picker = rememberTimePickerState(state.dateTime.hour, state.dateTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = {
                TextButton(onClick = { vm.setTime(LocalTime.of(picker.hour, picker.minute)); timeDialog = false }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancelar") } },
            text = { TimePicker(state = picker) },
        )
    }
}

@Composable
private fun ClientSection(state: EditorUiState, vm: EditorViewModel) {
    var expanded by rememberSaveable { mutableStateOf(!state.toReceipt().client.isEmpty) }
    val emailError = if (state.showErrors) state.validation.emailError else null
    val hasRutError = state.showErrors && state.validation.rutError != null
    LaunchedEffect(emailError, hasRutError) { if (emailError != null || hasRutError) expanded = true }
    SectionCard(
        title = "Cliente (opcional)",
        trailing = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Ocultar datos del cliente" else "Mostrar datos del cliente",
                )
            }
        },
    ) {
        if (!expanded) {
            Text(
                listOf(state.clientName.trim(), Rut.format(state.clientRut))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "Sin datos del cliente" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.clientName,
                    onValueChange = vm::setClientName,
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                val rutError = if (state.showErrors) state.validation.rutError else null
                OutlinedTextField(
                    value = state.clientRut,
                    onValueChange = vm::setClientRut,
                    label = { Text("RUT (opcional)") },
                    placeholder = { Text("12.345.678-5") },
                    singleLine = true,
                    isError = rutError != null,
                    supportingText = when {
                        rutError != null -> { { Text(rutError) } }
                        state.clientRut.length >= 2 && Rut.isValid(state.clientRut) -> { { Text("RUT válido · ${Rut.format(state.clientRut)}") } }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.clientPhone,
                    onValueChange = vm::setClientPhone,
                    label = { Text("Teléfono / WhatsApp") },
                    placeholder = { Text("+56 9 1234 5678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.clientEmail,
                    onValueChange = vm::setClientEmail,
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
