package cl.iglesia.gestion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val PICKER_TYPES = ALLOWED_FILE_TYPES.keys.toTypedArray()

/** Lee y valida un archivo elegido: solo PDF, JPG o PNG de hasta 5 MB. */
fun readPickedFile(context: Context, uri: Uri): PickedFile {
    val resolver = context.contentResolver
    val type = resolver.getType(uri)
    if (type == null || type !in ALLOWED_FILE_TYPES) {
        throw UserFacingException("Formato no permitido. Usa archivos PDF, JPG o PNG.")
    }
    var name = "archivo.${ALLOWED_FILE_TYPES.getValue(type)}"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            if (!cursor.isNull(0)) name = cursor.getString(0)
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    if (size > MAX_FILE_BYTES) throw UserFacingException("\"$name\" supera el máximo de 5 MB.")
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw UserFacingException("No se pudo leer \"$name\".")
    if (bytes.size > MAX_FILE_BYTES) throw UserFacingException("\"$name\" supera el máximo de 5 MB.")
    if (bytes.isEmpty()) throw UserFacingException("\"$name\" está vacío.")
    return PickedFile(name, type, bytes)
}

/** Selector de documentos del sistema con validación; informa errores al llamador. */
@Composable
fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val picked = mutableListOf<PickedFile>()
        val errors = mutableListOf<String>()
        uris.forEach { uri ->
            try {
                picked += readPickedFile(context, uri)
            } catch (error: UserFacingException) {
                errors += error.message.orEmpty()
            } catch (_: Exception) {
                errors += "No se pudo leer uno de los archivos."
            }
        }
        if (picked.isNotEmpty()) onPicked(picked)
        if (errors.isNotEmpty()) onError(errors.joinToString(" "))
    }
    return { launcher.launch(PICKER_TYPES) }
}

fun Long.fileSizeLabel(): String = when {
    this >= 1024 * 1024 -> String.format(ChileLocale, "%.1f MB", this / (1024.0 * 1024.0))
    this >= 1024 -> "${this / 1024} KB"
    else -> "$this B"
}

@Composable
private fun FileRow(name: String, contentType: String, size: Long, onOpen: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (contentType == "application/pdf") Icons.Outlined.Description else Icons.Outlined.Image,
            contentDescription = null,
            tint = BrandLight,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(size.fileSizeLabel(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (onOpen != null) TextButton(onClick = onOpen) { Text("Abrir") }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Quitar $name", tint = Danger)
            }
        }
    }
}

// Lista de registros -------------------------------------------------------------------

@Composable
fun RecordsScreen(
    member: Member,
    records: List<MeetingRecord>,
    available: Boolean,
    ministries: List<Ministry>,
    members: List<Member>,
    submitting: Boolean,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenFile: (RecordFile) -> Unit,
    onAddFiles: (MeetingRecord, List<PickedFile>) -> Unit,
    onDeleteFile: (RecordFile) -> Unit,
    onDeleteRecord: (MeetingRecord) -> Unit,
    onError: (String) -> Unit,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var attachTo by remember { mutableStateOf<MeetingRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<MeetingRecord?>(null) }
    var deletingFile by remember { mutableStateOf<RecordFile?>(null) }
    val pickFiles = rememberFilePicker(
        onPicked = { files -> attachTo?.let { onAddFiles(it, files) } },
        onError = onError,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackLink(onBack) }
            item {
                ScreenHeader(
                    "Después de cada reunión",
                    "Registros de reuniones",
                    "Resúmenes y archivos visibles solo para quienes tienen permiso.",
                )
            }
            if (!available) {
                item {
                    MessageBanner(
                        "Los registros aún no están habilitados en el servidor. Pide aplicar la migración de registros de reuniones.",
                        BannerTone.Error,
                    )
                }
            } else if (records.isEmpty()) {
                item {
                    EmptyState(
                        "Sin registros",
                        if (member.isPastor) "Registra el resumen de una reunión con \"Nuevo registro\"." else "Los registros compartidos contigo aparecerán aquí.",
                        Icons.Outlined.Description,
                    )
                }
            }
            items(records, key = { it.id }) { record ->
                val expanded = expandedId == record.id
                AppCard(onClick = { expandedId = if (expanded) null else record.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(record.visibilityLabel(ministries), Brand, BrandSurface)
                        Spacer(Modifier.weight(1f))
                        Text(record.date.fieldLabel(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(record.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    if (record.summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            record.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (record.files.size) {
                                0 -> "Sin archivos"
                                1 -> "1 archivo"
                                else -> "${record.files.size} archivos"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (expanded) "Ocultar" else "Ver detalle", style = MaterialTheme.typography.labelMedium, color = BrandLight)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(Modifier.height(6.dp))
                        if (record.visibility == RecordVisibility.Selected && member.isPastor) {
                            val names = record.viewerIds.mapNotNull { id -> members.find { it.id == id }?.name }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Visible para: " + names.joinToString().ifBlank { "solo el Pastor" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                        record.files.forEach { file ->
                            FileRow(
                                name = file.name,
                                contentType = file.contentType,
                                size = file.sizeBytes,
                                onOpen = { onOpenFile(file) },
                                onRemove = if (member.isPastor && !submitting) ({ deletingFile = file }) else null,
                            )
                        }
                        if (member.isPastor) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        attachTo = record
                                        pickFiles()
                                    },
                                    enabled = !submitting,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Adjuntar")
                                }
                                OutlinedButton(
                                    onClick = { deletingRecord = record },
                                    enabled = !submitting,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Eliminar", color = Danger)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (member.isPastor && available) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nuevo registro") },
                containerColor = Brand,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    deletingRecord?.let { record ->
        ConfirmDialog(
            title = "¿Eliminar el registro?",
            message = "Se eliminarán \"${record.title}\" y sus ${record.files.size} archivo(s). Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            destructive = true,
            onDismiss = { deletingRecord = null },
            onConfirm = {
                deletingRecord = null
                onDeleteRecord(record)
            },
        )
    }
    deletingFile?.let { file ->
        ConfirmDialog(
            title = "¿Eliminar el archivo?",
            message = "\"${file.name}\" dejará de estar disponible para todos.",
            confirmLabel = "Eliminar",
            destructive = true,
            onDismiss = { deletingFile = null },
            onConfirm = {
                deletingFile = null
                onDeleteFile(file)
            },
        )
    }
}

fun MeetingRecord.visibilityLabel(ministries: List<Ministry>): String = when (visibility) {
    RecordVisibility.Everyone -> "Toda la iglesia"
    RecordVisibility.Ministry -> ministries.find { it.id == ministryId }?.shortName ?: "Ministerio"
    RecordVisibility.Selected -> if (viewerIds.size == 1) "1 persona" else "${viewerIds.size} personas"
}

// Formulario ---------------------------------------------------------------------------

@Composable
fun RecordForm(
    meetings: List<ChurchActivity>,
    ministries: List<Ministry>,
    members: List<Member>,
    currentMember: Member,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MeetingRecordDraft, List<PickedFile>) -> Unit,
) {
    val today = LocalDate.now()
    val pastMeetings = meetings
        .filter { it.isMeeting && !it.date.isAfter(today) }
        .sortedByDescending(ChurchActivity::date)
        .take(10)
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today) }
    var meetingId by rememberSaveable { mutableStateOf<String?>(null) }
    var summary by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(RecordVisibility.Everyone) }
    var ministryId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewers by remember { mutableStateOf(setOf<String>()) }
    var files by remember { mutableStateOf(listOf<PickedFile>()) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val pickFiles = rememberFilePicker(
        onPicked = { picked ->
            pickError = null
            files = files + picked
        },
        onError = { pickError = it },
    )
    val candidates = members.filter { it.active && it.id != currentMember.id }
    val visibilityValid = when (visibility) {
        RecordVisibility.Everyone -> true
        RecordVisibility.Ministry -> ministryId != null
        RecordVisibility.Selected -> viewers.isNotEmpty()
    }
    val canSubmit = title.isNotBlank() && visibilityValid

    FullScreenForm(
        title = "Nuevo registro de reunión",
        confirmLabel = if (files.isEmpty()) "Guardar registro" else "Guardar y subir ${files.size} archivo(s)",
        canConfirm = canSubmit,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            onSubmit(
                MeetingRecordDraft(
                    meetingId = meetingId,
                    title = title,
                    date = date,
                    summary = summary,
                    visibility = visibility,
                    ministryId = ministryId,
                    viewerIds = viewers.toList(),
                ),
                files,
            )
        },
    ) {
        MessageBanner(
            "El registro es distinto de la convocatoria: resume lo tratado y solo lo verán las personas que elijas.",
            BannerTone.Info,
        )
        if (pastMeetings.isNotEmpty()) {
            FieldLabel("Reunión convocada (opcional)")
            ChoiceChips(
                listOf<ChurchActivity?>(null) + pastMeetings,
                pastMeetings.firstOrNull { it.id == meetingId },
                { it?.let { meeting -> "${meeting.date.shortLabel()} · ${meeting.title}" } ?: "Ninguna" },
                { meeting ->
                    meetingId = meeting?.id
                    if (meeting != null) {
                        if (title.isBlank()) title = meeting.title
                        date = meeting.date
                    }
                },
            )
        }
        TextInput(title, { title = it }, "Nombre de la reunión", required = true)
        DateField("Fecha *", date, { it?.let { date = it } })
        TextInput(summary, { summary = it }, "Resumen", singleLine = false, minLines = 5)

        FieldLabel("¿Quién puede verlo?", required = true)
        ChoiceChips(RecordVisibility.entries, visibility, { it.label }, { visibility = it })
        when (visibility) {
            RecordVisibility.Everyone -> Text(
                "Todas las personas activas lo verán y recibirán un aviso.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            RecordVisibility.Ministry -> {
                ChoiceChips(ministries, ministries.firstOrNull { it.id == ministryId }, { it.shortName }, { ministryId = it.id })
                Text("Solo los integrantes del ministerio lo verán.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            RecordVisibility.Selected -> MemberPicker(
                candidates,
                viewers,
                multiSelect = true,
                onChange = { viewers = it },
                emptyMessage = "No hay personas activas para seleccionar.",
            )
        }

        FieldLabel("Archivos y presentaciones")
        Text("PDF, JPG o PNG, hasta 5 MB cada uno.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        pickError?.let { FieldError(it) }
        files.forEachIndexed { index, file ->
            FileRow(
                name = file.name,
                contentType = file.contentType,
                size = file.bytes.size.toLong(),
                onOpen = null,
                onRemove = { files = files.filterIndexed { i, _ -> i != index } },
            )
        }
        OutlinedButton(onClick = pickFiles, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Agregar archivos")
        }
    }
}

/** Enlace desde la ficha de una convocatoria a sus registros posteriores. */
@Composable
fun RecordLinks(records: List<MeetingRecord>, onOpenRecords: () -> Unit) {
    if (records.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Registros de esta reunión", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        records.forEach { record ->
            Text("• ${record.title} (${record.files.size} archivo(s))", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onOpenRecords) { Text("Ver registros") }
    }
}
