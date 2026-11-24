package cl.recibos.app.ui.receipt

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.recibos.app.AppContainer
import cl.recibos.app.R
import cl.recibos.app.domain.model.PaymentStatus
import cl.recibos.app.domain.model.Receipt
import cl.recibos.app.domain.util.Clp
import cl.recibos.app.domain.util.ReceiptNumber
import cl.recibos.app.export.ExportFormat
import cl.recibos.app.export.ShareIntents
import cl.recibos.app.ui.components.ConfirmDialog
import cl.recibos.app.ui.components.SectionCard
import cl.recibos.app.ui.components.StatusBadge

private enum class PendingConfirm { ISSUE, VOID, MARK_PAID, DELETE, REDO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    container: AppContainer,
    receiptId: Long,
    justIssued: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onIssued: () -> Unit,
    onOpenDraft: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: ReceiptViewModel = viewModel(
        key = "receipt-$receiptId",
        factory = viewModelFactory {
            initializer {
                ReceiptViewModel(receiptId, container.receipts, container.seller, container.exporter, container.appContext)
            }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (justIssued) snackbar.showSnackbar("Comprobante emitido. Ya puedes descargarlo o compartirlo.")
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ReceiptEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    snackbar.showSnackbar("No hay una aplicación disponible para esta acción.")
                }
                is ReceiptEvent.Message -> {
                    val result = snackbar.showSnackbar(
                        event.text,
                        actionLabel = if (event.openUri != null) "Abrir" else null,
                        withDismissAction = event.openUri != null,
                    )
                    if (result == SnackbarResult.ActionPerformed && event.openUri != null) {
                        try {
                            context.startActivity(ShareIntents.view(event.openUri, event.mimeType ?: "*/*"))
                        } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar("No hay una aplicación para abrir este archivo.")
                        }
                    }
                }
                ReceiptEvent.Issued -> onIssued()
                is ReceiptEvent.OpenDraft -> onOpenDraft(event.id)
                ReceiptEvent.Close -> onBack()
            }
        }
    }

    val receipt = state.receipt

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            receipt == null -> ""
                            receipt.isDraft -> "Vista previa"
                            else -> "Comprobante N.º " + ReceiptNumber.format(receipt.number ?: 0)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (receipt != null) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (receipt.isDraft) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar borrador") },
                                    onClick = { menu = false; confirm = PendingConfirm.DELETE },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Duplicar como nuevo") },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null) },
                                    onClick = { menu = false; vm.duplicate() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Borrar y rehacer N.º ${ReceiptNumber.format(receipt.number ?: 0)}") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; confirm = PendingConfirm.REDO },
                                )
                                if (receipt.status != PaymentStatus.VOID) {
                                    DropdownMenuItem(
                                        text = { Text("Anular comprobante", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_block), null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; confirm = PendingConfirm.VOID },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (receipt != null && receipt.isDraft) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Text("  Editar")
                        }
                        Button(
                            onClick = { confirm = PendingConfirm.ISSUE },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1.4f).height(52.dp),
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Text("  Confirmar y emitir")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(48.dp))
                state.missing || receipt == null -> Text(
                    "Este comprobante ya no existe.",
                    modifier = Modifier.padding(48.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (receipt.isDraft && !state.seller.isConfigured) {
                        Surface(
                            onClick = onOpenSettings,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                "El comprobante no mostrará vendedor. Toca aquí para configurar tus datos antes de emitir.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    if (!receipt.isDraft) StatusHeader(receipt, onMarkPaid = { confirm = PendingConfirm.MARK_PAID })
                    ReceiptPreview(state)
                    if (!receipt.isDraft) {
                        ExportActions(
                            format = state.shareFormat,
                            enabled = !state.busy,
                            onFormat = vm::setShareFormat,
                            onDownload = vm::download,
                            onShare = vm::share,
                        )
                    }
                }
            }
        }
    }

    val r = receipt
    when (confirm) {
        PendingConfirm.ISSUE -> if (r != null) {
            val amount = Clp.format(r.totals.total)
            val number = ReceiptNumber.format(r.reservedNumber ?: state.nextNumber)
            ConfirmDialog(
                title = if (r.status == PaymentStatus.PAID) "¿Confirmas que recibiste el pago?" else "Emitir como pendiente",
                message = if (r.status == PaymentStatus.PAID) {
                    "Declaras haber recibido $amount mediante ${paymentPhrase(r)}. " +
                        "Se asignará el N.º $number y el comprobante ya no podrá editarse."
                } else {
                    "Se emitirá el N.º $number por $amount con pago pendiente. El comprobante no indicará que recibiste dinero."
                },
                confirmText = if (r.status == PaymentStatus.PAID) "Sí, confirmar y emitir" else "Emitir",
                onConfirm = { confirm = null; vm.issue() },
                onDismiss = { confirm = null },
            )
        }
        PendingConfirm.VOID -> ConfirmDialog(
            title = "¿Anular comprobante?",
            message = "Quedará marcado como ANULADO y se conservará en el historial. Su número no se reutilizará. " +
                "Esta acción no se puede deshacer.",
            confirmText = "Anular",
            destructive = true,
            onConfirm = { confirm = null; vm.void() },
            onDismiss = { confirm = null },
        )
        PendingConfirm.REDO -> if (r != null) {
            val number = ReceiptNumber.format(r.number ?: 0)
            ConfirmDialog(
                title = "¿Borrar y rehacer el N.º $number?",
                message = "El comprobante se eliminará del historial y se abrirá un borrador con sus mismos datos para " +
                    "corregirlo. Al emitirlo recuperará el N.º $number.\n\nSi ya lo compartiste, el cliente conservará " +
                    "la versión anterior. Si solo quieres dejarlo sin efecto, usa «Anular».",
                confirmText = "Borrar y rehacer",
                destructive = true,
                onConfirm = { confirm = null; vm.deleteAndRedo() },
                onDismiss = { confirm = null },
            )
        }
        PendingConfirm.MARK_PAID -> ConfirmDialog(
            title = "¿Confirmas que recibiste el pago?",
            message = "El comprobante pasará a estado PAGADO y su total se mostrará como recibido.",
            confirmText = "Sí, marcar como pagado",
            onConfirm = { confirm = null; vm.markAsPaid() },
            onDismiss = { confirm = null },
        )
        PendingConfirm.DELETE -> ConfirmDialog(
            title = "¿Eliminar borrador?",
            message = if (r?.reservedNumber != null) {
                "El borrador se eliminará y el N.º ${ReceiptNumber.format(r.reservedNumber)} quedará sin usar " +
                    "(si era el último, lo tomará el próximo comprobante)."
            } else {
                "El borrador se eliminará. No afecta la numeración de los comprobantes."
            },
            confirmText = "Eliminar",
            destructive = true,
            onConfirm = { confirm = null; vm.deleteDraft() },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun StatusHeader(receipt: Receipt, onMarkPaid: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Clp.format(receipt.totals.total), style = MaterialTheme.typography.headlineSmall)
            Text(
                receipt.client.name.ifBlank { "Sin cliente" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(receipt.status)
    }
    if (receipt.status == PaymentStatus.PENDING) {
        FilledTonalButton(onClick = onMarkPaid, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Text("  Marcar como pagado")
        }
    }
}

@Composable
private fun ReceiptPreview(state: ReceiptUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val bitmap = state.preview
        if (bitmap == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "Vista previa del comprobante",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportActions(
    format: ExportFormat,
    enabled: Boolean,
    onFormat: (ExportFormat) -> Unit,
    onDownload: (ExportFormat) -> Unit,
    onShare: (ShareTarget) -> Unit,
) {
    SectionCard(title = "Descargar") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("PDF (A5)", painterResource(R.drawable.ic_pdf), enabled, Modifier.weight(1f)) { onDownload(ExportFormat.PDF) }
            ActionButton("Imagen PNG", painterResource(R.drawable.ic_image), enabled, Modifier.weight(1f)) { onDownload(ExportFormat.PNG) }
        }
    }
    SectionCard(title = "Compartir") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ExportFormat.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == format,
                    onClick = { onFormat(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExportFormat.entries.size),
                    label = { Text(if (option == ExportFormat.PDF) "Enviar PDF" else "Enviar imagen") },
                )
            }
        }
        Button(onClick = { onShare(ShareTarget.WHATSAPP) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(18.dp))
            Text("  Compartir por WhatsApp")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Correo", painterResource(R.drawable.ic_mail), enabled, Modifier.weight(1f)) { onShare(ShareTarget.EMAIL) }
            OutlinedButton(onClick = { onShare(ShareTarget.OTHER) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Text("  Otras apps")
            }
        }
        Text(
            "Nada se envía automáticamente: siempre eliges la aplicación y confirmas el envío.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: Painter, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text("  $label", maxLines = 1)
    }
}

/** "transferencia bancaria" o "transferencia bancaria ($150.000) y efectivo ($65.000)". */
private fun paymentPhrase(r: Receipt): String {
    val parts = r.resolvedPayments
    if (parts.size == 1) return parts.first().method.label.lowercase()
    val described = parts.map { "${it.method.label.lowercase()} (${Clp.format(it.amount)})" }
    return described.dropLast(1).joinToString(", ") + " y " + described.last()
}
