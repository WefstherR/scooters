package cl.recibos.app.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import cl.recibos.app.domain.model.ReceiptAccent
import cl.recibos.app.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.seller, container.logos) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var exitDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::onLogoPicked)

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val requestBack = { if (state.hasChanges) exitDialog = true else onBack() }
    BackHandler { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datos del vendedor") },
                navigationIcon = { IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    TextButton(onClick = vm::save, enabled = state.hasChanges && !state.busy) { Text("Guardar") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        val p = state.profile
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Estos datos se incorporan a cada comprobante nuevo. Los comprobantes ya emitidos conservan los datos originales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionCard("Identificación") {
                    OutlinedTextField(
                        value = p.name,
                        onValueChange = vm::setName,
                        label = { Text("Nombre o nombre comercial") },
                        singleLine = true,
                        isError = state.showErrors && state.nameError != null,
                        supportingText = if (state.showErrors) state.nameError?.let { { Text(it) } } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = p.phone,
                        onValueChange = vm::setPhone,
                        label = { Text("Teléfono de contacto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = p.email,
                        onValueChange = vm::setEmail,
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        isError = state.emailError != null,
                        supportingText = state.emailError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = p.thanksMessage,
                        onValueChange = vm::setThanks,
                        label = { Text("Mensaje de agradecimiento") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard("Logo") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LogoThumb(p.logoPath)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !state.busy,
                            ) { Text(if (p.logoPath == null) "Elegir imagen" else "Cambiar imagen") }
                            if (p.logoPath != null) {
                                TextButton(onClick = vm::removeLogo) { Text("Quitar logo", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mostrar logo en el comprobante", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = p.showLogo, onCheckedChange = vm::setShowLogo, enabled = p.logoPath != null)
                    }
                }

                SectionCard("Apariencia del comprobante") {
                    Text("Color de acento", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReceiptAccent.entries.forEach { accent ->
                            AccentSwatch(accent, selected = p.accent == accent) { vm.setAccent(accent) }
                        }
                    }
                    Text(
                        p.accent.label + " · se usa en el encabezado. El resto del diseño se mantiene sobrio y legible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("¿Guardar cambios?") },
            text = { Text("Modificaste los datos del vendedor.") },
            confirmButton = { TextButton(onClick = { exitDialog = false; vm.save() }) { Text("Guardar") } },
            dismissButton = {
                TextButton(onClick = { exitDialog = false; onBack() }) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun LogoThumb(path: String?) {
    val bitmap = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    Box(
        Modifier
            .size(72.dp)
            .background(Color.White, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = "Logo actual",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(6.dp).fillMaxSize(),
            )
        } else {
            Icon(painterResource(R.drawable.ic_image), null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AccentSwatch(accent: ReceiptAccent, selected: Boolean, onClick: () -> Unit) {
    val color = Color(accent.argb)
    Box(
        Modifier
            .size(40.dp)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                CircleShape,
            )
            .padding(5.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = accent.label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
