package cl.iglesia.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private fun String.digitsOnly() = filter(Char::isDigit).take(13)

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supporting: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

// Proyecto -------------------------------------------------------------------------------

@Composable
fun ProjectForm(
    existing: ChurchProject?,
    members: List<Member>,
    currentMember: Member,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ProjectDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var objective by rememberSaveable { mutableStateOf(existing?.objective.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var leaderId by rememberSaveable { mutableStateOf(existing?.leaderId ?: currentMember.id) }
    var startDate by rememberSaveable { mutableStateOf(existing?.startDate ?: LocalDate.now()) }
    var dueDate by rememberSaveable { mutableStateOf(existing?.dueDate ?: LocalDate.now().plusDays(30)) }
    var status by rememberSaveable { mutableStateOf(existing?.status?.takeIf { it in WORK_STATUSES } ?: "pending") }
    var budgetEstimated by rememberSaveable { mutableStateOf(existing?.budgetEstimated?.toString().orEmpty()) }
    var budgetExecuted by rememberSaveable { mutableStateOf(existing?.budgetExecuted?.toString().orEmpty()) }
    val leaderOptions = members.filter { it.active || it.id == existing?.leaderId }
    val datesValid = !dueDate.isBefore(startDate)
    val canSubmit = name.isNotBlank() && objective.isNotBlank() && datesValid && leaderOptions.any { it.id == leaderId }

    FullScreenForm(
        title = if (existing == null) "Nuevo proyecto" else "Editar proyecto",
        confirmLabel = if (existing == null) "Crear proyecto" else "Guardar cambios",
        canConfirm = canSubmit,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            onSubmit(
                ProjectDraft(
                    id = existing?.id,
                    name = name,
                    objective = objective,
                    description = description,
                    leaderId = leaderId,
                    startDate = startDate,
                    dueDate = dueDate,
                    status = status,
                    budgetEstimated = budgetEstimated.toLongOrNull(),
                    budgetExecuted = budgetExecuted.toLongOrNull(),
                ),
            )
        },
    ) {
        MessageBanner("Los proyectos son institucionales: pertenecen a toda la iglesia, no a un ministerio.", BannerTone.Info)
        TextInput(name, { name = it }, "Nombre", required = true)
        TextInput(objective, { objective = it }, "Objetivo", required = true, singleLine = false, minLines = 2)
        TextInput(description, { description = it }, "Descripción", singleLine = false, minLines = 3)
        FieldLabel("Responsable del proyecto", required = true)
        MemberPicker(leaderOptions, setOf(leaderId), multiSelect = false, onChange = { leaderId = it.first() }, emptyMessage = "No hay personas activas visibles.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField("Inicio *", startDate, { it?.let { startDate = it } }, Modifier.weight(1f))
            DateField("Fin estimado *", dueDate, { it?.let { dueDate = it } }, Modifier.weight(1f))
        }
        if (!datesValid) FieldError("La fecha de término no puede ser anterior a la de inicio.")
        FieldLabel("Estado", required = true)
        ChoiceChips(WORK_STATUSES, status, { statusStyle(it, feminine = false).label }, { status = it })
        Text(
            "El avance se calcula automáticamente con las actividades del proyecto.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        TextInput(
            budgetEstimated,
            { budgetEstimated = it.digitsOnly() },
            "Presupuesto estimado (CLP)",
            keyboardType = KeyboardType.Number,
            supportingText = budgetEstimated.toLongOrNull()?.toCLPCurrency() ?: "Opcional",
        )
        TextInput(
            budgetExecuted,
            { budgetExecuted = it.digitsOnly() },
            "Presupuesto ejecutado (CLP)",
            keyboardType = KeyboardType.Number,
            supportingText = budgetExecuted.toLongOrNull()?.toCLPCurrency() ?: "Opcional",
        )
    }
}

// Actividad -------------------------------------------------------------------------------

@Composable
fun ActivityForm(
    ministries: List<Ministry>,
    members: List<Member>,
    projects: List<ChurchProject>,
    currentMember: Member,
    permissions: Permissions,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ActivityDraft) -> Unit,
) {
    val availableMinistries = if (currentMember.isPastor) ministries else ministries.filter { it.id in currentMember.ledMinistryIds }
    // La creación de actividades individuales sigue pendiente de validación: se
    // mantiene restringida al Pastor, igual que la política RLS actual.
    val classifications = if (currentMember.isPastor) {
        listOf(Classification.Ministry, Classification.Project, Classification.General, Classification.Individual)
    } else {
        listOf(Classification.Ministry)
    }
    val openProjects = projects.filter { it.status != "completed" && it.status != "cancelled" }
    val types = ActivityTypes.all.filter { it != ActivityTypes.ENSAYO || permissions.canManageWorship }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ActivityTypes.LIBRE) }
    var classification by rememberSaveable { mutableStateOf(classifications.first()) }
    var ministryId by rememberSaveable { mutableStateOf(availableMinistries.singleOrNull()?.id) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var startTime by rememberSaveable { mutableStateOf(LocalTime.of(19, 0)) }
    var endTime by rememberSaveable { mutableStateOf(LocalTime.of(21, 0)) }
    var location by rememberSaveable { mutableStateOf("") }
    var preacherId by rememberSaveable { mutableStateOf<String?>(null) }
    var worshipLeaderId by rememberSaveable { mutableStateOf<String?>(null) }
    var assignees by remember { mutableStateOf(setOf<String>()) }

    fun selectCategory(value: String) {
        category = value
        // Los ensayos pertenecen al ministerio de Alabanza.
        if (value == ActivityTypes.ENSAYO && permissions.worshipMinistryId != null) {
            classification = Classification.Ministry
            ministryId = permissions.worshipMinistryId
        }
    }

    val eligible = members.filter { candidate ->
        candidate.active && (
            currentMember.isPastor ||
                (ministryId != null && ministryId in candidate.ministryIds)
            )
    }
    val activeMembers = members.filter(Member::active)
    val selectedAssignees = assignees.filter { id -> eligible.any { it.id == id } }.toSet()
    val isCulto = category == ActivityTypes.CULTO
    val timesValid = startTime.isBefore(endTime)
    val targetValid = when (classification) {
        Classification.Ministry -> ministryId != null && availableMinistries.any { it.id == ministryId }
        Classification.Project -> openProjects.any { it.id == projectId }
        else -> currentMember.isPastor
    }
    val cultoValid = !isCulto || (preacherId != null && worshipLeaderId != null)
    val canSubmit = title.isNotBlank() && timesValid && targetValid && cultoValid && selectedAssignees.isNotEmpty()

    FullScreenForm(
        title = "Nueva actividad",
        confirmLabel = "Crear actividad",
        canConfirm = canSubmit,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            onSubmit(
                ActivityDraft(
                    title = title,
                    description = description,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    classification = classification,
                    ministryId = ministryId,
                    projectId = projectId,
                    location = location,
                    category = category,
                    worshipLeaderId = worshipLeaderId,
                    preacherId = preacherId,
                    assigneeIds = selectedAssignees.toList(),
                ),
            )
        },
    ) {
        TextInput(title, { title = it }, "Título", required = true)
        FieldLabel("Tipo", required = true)
        ChoiceChips(types, category, { it }, ::selectCategory)

        FieldLabel("Clasificación", required = true)
        ChoiceChips(
            classifications,
            classification,
            { it.label },
            { classification = it },
            enabled = { category != ActivityTypes.ENSAYO || it == Classification.Ministry },
        )
        when (classification) {
            Classification.Ministry -> {
                if (availableMinistries.isEmpty()) {
                    MessageBanner("No lideras ningún ministerio, por lo que no puedes crear actividades.", BannerTone.Error)
                } else if (availableMinistries.size > 1 || currentMember.isPastor) {
                    FieldLabel("Ministerio", required = true)
                    ChoiceChips(
                        availableMinistries,
                        availableMinistries.firstOrNull { it.id == ministryId },
                        { it.shortName },
                        { ministryId = it.id },
                        enabled = { category != ActivityTypes.ENSAYO || it.id == permissions.worshipMinistryId },
                    )
                } else {
                    Text("Ministerio: ${availableMinistries.first().name}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Classification.Project -> {
                FieldLabel("Proyecto", required = true)
                if (openProjects.isEmpty()) {
                    Text("No hay proyectos abiertos. Créalo primero en Proyectos.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                ChoiceChips(openProjects, openProjects.firstOrNull { it.id == projectId }, { it.name }, { projectId = it.id })
            }
            Classification.Individual -> Text(
                "Solo la verán el Pastor y las personas responsables.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Classification.General -> Text(
                "Visible para toda la iglesia.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        // En un Ensayo, las personas elegidas son las invitadas: lo verán en su agenda personal.
        FieldLabel(if (category == ActivityTypes.ENSAYO) "Invitados al ensayo" else "Responsables", required = true)
        if (classification == Classification.Ministry && ministryId == null) {
            Text("Elige primero el ministerio.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            MemberPicker(
                eligible,
                selectedAssignees,
                multiSelect = true,
                onChange = { assignees = it },
                emptyMessage = "No hay integrantes activos en este ministerio para asignar.",
            )
        }

        DateField("Fecha *", date, { it?.let { date = it } })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Inicio", startTime, { startTime = it }, Modifier.weight(1f))
            TimeField("Término", endTime, { endTime = it }, Modifier.weight(1f))
        }
        if (!timesValid) FieldError("La hora de término debe ser posterior a la de inicio.")
        TextInput(location, { location = it }, "Lugar", placeholder = "Ej.: Santuario principal")
        TextInput(description, { description = it }, "Descripción", singleLine = false, minLines = 3)

        if (isCulto) {
            FieldLabel("Predicador", required = true)
            MemberPicker(activeMembers, setOfNotNull(preacherId), multiSelect = false, onChange = { preacherId = it.firstOrNull() }, emptyMessage = "No hay personas visibles.")
            FieldLabel("Director de alabanza", required = true)
            MemberPicker(activeMembers, setOfNotNull(worshipLeaderId), multiSelect = false, onChange = { worshipLeaderId = it.firstOrNull() }, emptyMessage = "No hay personas visibles.")
        }
    }
}

// Reunión ---------------------------------------------------------------------------------

@Composable
fun MeetingForm(
    ministries: List<Ministry>,
    members: List<Member>,
    currentMember: Member,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MeetingDraft) -> Unit,
) {
    val availableMinistries = if (currentMember.isPastor) ministries else ministries.filter { it.id in currentMember.ledMinistryIds }
    var title by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("") }
    var ministryId by rememberSaveable { mutableStateOf(if (currentMember.isPastor) null else availableMinistries.firstOrNull()?.id) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var startTime by rememberSaveable { mutableStateOf(LocalTime.of(19, 0)) }
    var endTime by rememberSaveable { mutableStateOf(LocalTime.of(20, 0)) }
    var location by rememberSaveable { mutableStateOf("") }
    var participants by remember { mutableStateOf(setOf<String>()) }
    val eligible = members.filter {
        it.active && it.id != currentMember.id &&
            (currentMember.isPastor || (ministryId != null && ministryId in it.ministryIds))
    }
    val timesValid = startTime.isBefore(endTime)
    val canSubmit = title.isNotBlank() && timesValid && (currentMember.isPastor || ministryId != null)

    FullScreenForm(
        title = "Convocar reunión",
        confirmLabel = "Enviar convocatoria",
        canConfirm = canSubmit,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            onSubmit(
                MeetingDraft(
                    title = title,
                    purpose = purpose,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    ministryId = ministryId,
                    location = location,
                    participantIds = participants.filter { id -> eligible.any { it.id == id } },
                ),
            )
        },
    ) {
        MessageBanner("Las personas invitadas recibirán un aviso y verán la reunión en su agenda.", BannerTone.Info)
        TextInput(title, { title = it }, "Título", required = true)
        TextInput(purpose, { purpose = it }, "Propósito", singleLine = false, minLines = 2)
        if (availableMinistries.isNotEmpty()) {
            FieldLabel("Ministerio", required = !currentMember.isPastor)
            val options: List<Ministry?> = (if (currentMember.isPastor) listOf<Ministry?>(null) else emptyList()) + availableMinistries
            ChoiceChips(options, availableMinistries.firstOrNull { it.id == ministryId }, { it?.shortName ?: "General" }, { ministryId = it?.id })
        }
        DateField("Fecha *", date, { it?.let { date = it } })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Inicio", startTime, { startTime = it }, Modifier.weight(1f))
            TimeField("Término", endTime, { endTime = it }, Modifier.weight(1f))
        }
        if (!timesValid) FieldError("La hora de término debe ser posterior a la de inicio.")
        TextInput(location, { location = it }, "Lugar")
        FieldLabel("Invitados")
        MemberPicker(eligible, participants, multiSelect = true, onChange = { participants = it }, emptyMessage = "No hay personas disponibles para invitar.")
    }
}

// Tarea ------------------------------------------------------------------------------------

@Composable
fun TaskForm(
    initialActivityId: String?,
    activities: List<ChurchActivity>,
    members: List<Member>,
    currentMember: Member,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (TaskDraft) -> Unit,
) {
    val today = LocalDate.now()
    val parents = activities
        .filter { it.kind == ItemKind.Activity && !it.isLegacyTask && currentMember.canManageMinistry(it.ministryId) }
        .filter { it.id == initialActivityId || !it.date.isBefore(today.minusDays(7)) }
        .sortedBy(ChurchActivity::date)
    var activityId by rememberSaveable { mutableStateOf(initialActivityId ?: parents.firstOrNull()?.id) }
    var title by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var assigneeId by rememberSaveable { mutableStateOf<String?>(null) }
    var priority by rememberSaveable { mutableStateOf("medium") }
    var dueDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var dueTime by rememberSaveable { mutableStateOf(LocalTime.of(18, 0)) }
    var comments by rememberSaveable { mutableStateOf("") }
    val parent = parents.firstOrNull { it.id == activityId }
    val eligible = members.filter {
        it.active && (currentMember.isPastor || (parent?.ministryId != null && parent.ministryId in it.ministryIds))
    }
    val canSubmit = parent != null && title.isNotBlank() && instructions.isNotBlank() && eligible.any { it.id == assigneeId }

    FullScreenForm(
        title = "Nueva tarea",
        confirmLabel = "Asignar tarea",
        canConfirm = canSubmit,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            val parentId = activityId
            val responsible = assigneeId
            if (parentId != null && responsible != null) {
                onSubmit(
                    TaskDraft(
                        activityId = parentId,
                        title = title,
                        instructions = instructions,
                        assigneeId = responsible,
                        dueAt = dueDate?.let { LocalDateTime.of(it, dueTime).atZone(ZoneId.systemDefault()).toOffsetDateTime() },
                        priority = priority,
                        comments = comments,
                    ),
                )
            }
        },
    ) {
        FieldLabel("Actividad", required = true)
        if (parents.isEmpty()) {
            MessageBanner("Primero crea una actividad en un ministerio que gestiones.", BannerTone.Info)
        } else if (initialActivityId != null && parent != null) {
            Text("${parent.title} · ${parent.date.shortLabel()}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        } else {
            ChoiceChips(parents, parent, { "${it.date.shortLabel()} · ${it.title}" }, {
                activityId = it.id
                assigneeId = null
            })
        }
        TextInput(title, { title = it }, "Título", required = true)
        TextInput(instructions, { instructions = it }, "Instrucciones", required = true, singleLine = false, minLines = 3)
        FieldLabel("Responsable", required = true)
        if (parent == null) {
            Text("Elige primero la actividad.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            MemberPicker(eligible, setOfNotNull(assigneeId), multiSelect = false, onChange = { assigneeId = it.firstOrNull() }, emptyMessage = "No hay integrantes activos del ministerio para asignar.")
        }
        FieldLabel("Prioridad", required = true)
        ChoiceChips(listOf("high", "medium", "low"), priority, { it.toPriorityLabel() }, { priority = it })
        DateField("Fecha límite (opcional)", dueDate, { dueDate = it }, clearable = true)
        if (dueDate != null) {
            TimeField("Hora límite", dueTime, { dueTime = it })
            Text("La tarea aparecerá en el calendario personal del responsable.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        TextInput(comments, { comments = it }, "Comentarios (opcional)", singleLine = false, minLines = 2)
    }
}

// Canción, ministerio y perfil ----------------------------------------------------------------

private val MusicalKeys = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

@Composable
fun SongForm(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (WorshipSongDraft, List<PickedFile>) -> Unit,
    existing: WorshipSong? = null,
) {
    // La tonalidad se guarda como "A" o "Am": se separa la nota del modo menor.
    val existingMinor = existing?.defaultKey?.endsWith("m") == true
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var artist by rememberSaveable { mutableStateOf(existing?.artist.orEmpty()) }
    var key by rememberSaveable { mutableStateOf(existing?.defaultKey?.removeSuffix("m")?.ifBlank { null } ?: "C") }
    var minor by rememberSaveable { mutableStateOf(existingMinor) }
    var link by rememberSaveable { mutableStateOf(existing?.referenceUrl.orEmpty()) }
    var files by remember { mutableStateOf(listOf<PickedFile>()) }
    var pickError by remember { mutableStateOf<String?>(null) }
    val pickFiles = rememberFilePicker(
        onPicked = { picked ->
            pickError = null
            files = files + picked
        },
        onError = { pickError = it },
    )
    val linkValid = link.isBlank() || link.trim().startsWith("https://")
    FullScreenForm(
        title = if (existing == null) "Agregar canción" else "Editar canción",
        confirmLabel = if (existing == null) "Agregar al catálogo" else "Guardar cambios",
        canConfirm = title.isNotBlank() && linkValid,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(WorshipSongDraft(title, artist, if (minor) key + "m" else key, link), files) },
    ) {
        TextInput(title, { title = it }, "Título", required = true)
        TextInput(artist, { artist = it }, "Artista o autor")
        FieldLabel("Tonalidad por defecto", required = true)
        ChoiceChips(MusicalKeys, key, { it }, { key = it })
        SwitchRow("Tonalidad menor", minor, { minor = it })
        TextInput(
            link,
            { link = it.trim() },
            "Enlace a la versión de referencia",
            keyboardType = KeyboardType.Uri,
            isError = !linkValid,
            supportingText = if (linkValid) "Opcional. Por ejemplo, un enlace de YouTube." else "El enlace debe comenzar con https://",
        )
        FieldLabel(if (existing == null) "Archivos de la canción" else "Agregar más archivos")
        Text(
            if (existing == null || existing.files.isEmpty()) {
                "Partitura, letra o acordes. PDF, JPG o PNG, hasta 5 MB cada uno."
            } else {
                "Ya tiene ${existing.files.size} archivo(s); se abren o eliminan desde el catálogo. PDF, JPG o PNG, hasta 5 MB cada uno."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
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
        OutlinedButton(onClick = pickFiles, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Agregar archivos")
        }
    }
}

@Composable
fun MinistryForm(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MinistryDraft) -> Unit,
    existing: Ministry? = null,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    FullScreenForm(
        title = if (existing == null) "Nuevo ministerio" else "Editar ministerio",
        confirmLabel = if (existing == null) "Crear ministerio" else "Guardar cambios",
        canConfirm = name.isNotBlank(),
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(MinistryDraft(name, description)) },
    ) {
        MessageBanner("Aseo es un tipo de actividad y Pastor es un rol; no los crees como ministerios.", BannerTone.Info)
        TextInput(name, { name = it }, "Nombre", required = true, placeholder = "Ej.: Ministerio de Jóvenes")
        TextInput(description, { description = it }, "Descripción", singleLine = false, minLines = 3)
    }
}

/** Ministerios de una persona: integrante sí/no y, si lo es, líder sí/no. */
@Composable
private fun MembershipEditor(
    ministries: List<Ministry>,
    role: String,
    value: Map<String, Boolean>,
    onChange: (Map<String, Boolean>) -> Unit,
) {
    FieldLabel("Ministerios")
    if (ministries.isEmpty()) {
        Text("No hay ministerios creados.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        return
    }
    AppCard(contentPadding = 8.dp) {
        ministries.forEachIndexed { index, ministry ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = CardBorder)
            val isMember = ministry.id in value
            SwitchRow(
                label = ministry.name,
                checked = isMember,
                onCheckedChange = { checked -> onChange(if (checked) value + (ministry.id to false) else value - ministry.id) },
            )
            if (isMember) {
                Row(Modifier.padding(start = 16.dp)) {
                    SwitchRow(
                        label = "Líder de este ministerio",
                        checked = value[ministry.id] == true,
                        onCheckedChange = { onChange(value + (ministry.id to it)) },
                        supporting = if (role != Roles.MINISTRY_LEADER && value[ministry.id] == true) {
                            "Solo podrá gestionarlo si su rol es Líder de ministerio."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordInput(value: String, onValueChange: (String) -> Unit, label: String, showProblem: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    val problem = if (showProblem && value.isNotEmpty()) passwordProblem(value) else null
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$label *") },
        singleLine = true,
        isError = problem != null,
        supportingText = { Text(problem ?: "Mínimo 8 caracteres, con al menos una letra y un número.") },
        visualTransformation = if (visible) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                )
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Contraseña temporal legible (sin caracteres ambiguos) que cumple la política. */
private fun generateTemporaryPassword(): String {
    val letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"
    val digits = "23456789"
    val random = java.security.SecureRandom()
    val chars = MutableList(8) { letters[random.nextInt(letters.length)] } +
        List(2) { digits[random.nextInt(digits.length)] }
    return chars.shuffled(random).joinToString("")
}

@Composable
fun NewUserForm(
    ministries: List<Ministry>,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (NewUserDraft) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Roles.LEADER) }
    var password by rememberSaveable { mutableStateOf(generateTemporaryPassword()) }
    var memberships by remember { mutableStateOf(mapOf<String, Boolean>()) }
    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    FullScreenForm(
        title = "Nueva cuenta",
        confirmLabel = "Crear cuenta",
        canConfirm = emailValid && name.isNotBlank() && passwordProblem(password) == null,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(NewUserDraft(email, name, phone, role, password, memberships)) },
    ) {
        MessageBanner(
            "La cuenta queda activa de inmediato. Entrega la contraseña temporal en persona y pide que la cambie en Más › Cambiar mi contraseña.",
            BannerTone.Info,
        )
        TextInput(name, { name = it }, "Nombre completo", required = true)
        TextInput(
            email,
            { email = it.trim() },
            "Correo electrónico",
            required = true,
            keyboardType = KeyboardType.Email,
            isError = email.isNotBlank() && !emailValid,
            supportingText = if (email.isNotBlank() && !emailValid) "Escribe un correo válido." else "Será su usuario para ingresar.",
        )
        TextInput(phone, { phone = it }, "Teléfono (opcional)", keyboardType = KeyboardType.Phone)
        FieldLabel("Rol", required = true)
        ChoiceChips(Roles.all, role, { it }, { role = it })
        PasswordInput(password, { password = it }, "Contraseña temporal")
        TextButton(onClick = { password = generateTemporaryPassword() }) { Text("Generar otra contraseña") }
        MembershipEditor(ministries, role, memberships) { memberships = it }
    }
}

/** Datos de acceso de la cuenta recién creada; se muestran hasta que el Pastor los cierra. */
@Composable
fun CreatedAccountDialog(email: String, password: String, onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Cuenta creada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Anota estos datos y entrégalos en persona. La contraseña no se volverá a mostrar.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Correo", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Text(email, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Contraseña temporal", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        password,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = Brand,
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString("Correo: $email\nContraseña: $password"))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(if (copied) "Copiado" else "Copiar correo y contraseña") }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Listo, ya los anoté")
            }
        },
    )
}

@Composable
fun ProfileForm(
    target: Member,
    ministries: List<Ministry>,
    isSelf: Boolean,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ProfileDraft) -> Unit,
    onSendReset: () -> Unit,
    onSetPassword: (String) -> Unit,
) {
    val initialMemberships = target.ministryIds.associateWith { id -> ministries.any { it.id == id && target.id in it.leaderIds } }
    var name by rememberSaveable { mutableStateOf(target.name) }
    var phone by rememberSaveable { mutableStateOf(target.phone) }
    var role by rememberSaveable { mutableStateOf(target.role) }
    var active by rememberSaveable { mutableStateOf(target.active) }
    var memberships by remember { mutableStateOf(initialMemberships) }
    var confirmDeactivate by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var settingPassword by remember { mutableStateOf(false) }
    val draft = ProfileDraft(name, phone, role, active, memberships)
    FullScreenForm(
        title = "Administrar cuenta",
        confirmLabel = "Guardar cambios",
        canConfirm = name.isNotBlank(),
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { if (target.active && !active) confirmDeactivate = true else onSubmit(draft) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(target.initials, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(target.email.ifBlank { "Correo no disponible" }, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    target.lastSignIn?.let { "Último acceso: ${it.relativeLabel()}" } ?: "Sin accesos registrados",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
        TextInput(name, { name = it }, "Nombre completo", required = true)
        TextInput(phone, { phone = it }, "Teléfono (opcional)", keyboardType = KeyboardType.Phone)
        FieldLabel("Rol", required = true)
        ChoiceChips(Roles.all, role, { it }, { role = it })
        if (isSelf && role != Roles.PASTOR) {
            FieldError("Vas a quitarte el rol de Pastor. Debe quedar al menos otro Pastor activo.")
        }
        SwitchRow(
            "Cuenta activa",
            active,
            { active = it },
            supporting = "Una cuenta inactiva no puede usar la aplicación. Sus registros se conservan.",
        )
        MembershipEditor(ministries, role, memberships) { memberships = it }

        FieldLabel("Acceso")
        OutlinedButton(
            onClick = { confirmReset = true },
            enabled = target.email.isNotBlank() && !submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Enviar correo para restablecer contraseña") }
        OutlinedButton(
            onClick = { settingPassword = true },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Asignar contraseña temporal") }
    }
    if (confirmDeactivate) {
        ConfirmDialog(
            title = "¿Desactivar la cuenta?",
            message = "${target.name} no podrá ingresar a la aplicación hasta que se reactive.",
            confirmLabel = "Desactivar",
            destructive = true,
            onDismiss = { confirmDeactivate = false },
            onConfirm = {
                confirmDeactivate = false
                onSubmit(draft)
            },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "¿Enviar restablecimiento?",
            message = "Se enviará a ${target.email} un enlace para crear una nueva contraseña.",
            confirmLabel = "Enviar",
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                onSendReset()
            },
        )
    }
    if (settingPassword) {
        var temporary by remember { mutableStateOf(generateTemporaryPassword()) }
        AlertDialog(
            onDismissRequest = { settingPassword = false },
            title = { Text("Contraseña temporal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reemplaza la contraseña de ${target.firstName}. Entrégala en persona.", style = MaterialTheme.typography.bodyMedium)
                    PasswordInput(temporary, { temporary = it }, "Nueva contraseña")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passwordProblem(temporary) == null,
                    onClick = {
                        settingPassword = false
                        onSetPassword(temporary)
                    },
                ) { Text("Asignar") }
            },
            dismissButton = { TextButton(onClick = { settingPassword = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun ChangePasswordForm(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val matches = password == confirmation
    FullScreenForm(
        title = "Cambiar mi contraseña",
        confirmLabel = "Cambiar contraseña",
        canConfirm = passwordProblem(password) == null && matches,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(password) },
    ) {
        PasswordInput(password, { password = it }, "Nueva contraseña")
        PasswordInput(confirmation, { confirmation = it }, "Repite la contraseña", showProblem = false)
        if (confirmation.isNotEmpty() && !matches) FieldError("Las contraseñas no coinciden.")
    }
}

@Composable
fun AddMemberForm(
    ministry: Ministry,
    candidates: List<Member>,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (userId: String, isLeader: Boolean) -> Unit,
) {
    var memberId by remember { mutableStateOf<String?>(null) }
    var leader by remember { mutableStateOf(false) }
    FullScreenForm(
        title = "Agregar a ${ministry.shortName}",
        confirmLabel = "Agregar integrante",
        canConfirm = memberId != null,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { memberId?.let { onSubmit(it, leader) } },
    ) {
        FieldLabel("Persona", required = true)
        MemberPicker(
            members = candidates,
            selectedIds = setOfNotNull(memberId),
            multiSelect = false,
            onChange = { memberId = it.firstOrNull() },
            emptyMessage = "Todas las personas activas ya pertenecen a este ministerio.",
        )
        SwitchRow(
            label = "Líder del ministerio",
            checked = leader,
            onCheckedChange = { leader = it },
            supporting = "Podrá crear actividades y asignar tareas si su rol es Líder de ministerio.",
        )
    }
}

/** Cambia los responsables de una actividad existente. */
@Composable
fun AssigneesForm(
    activity: ChurchActivity,
    eligible: List<Member>,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(activity.assigneeIds.toSet()) }
    val options = eligible
    FullScreenForm(
        title = "Responsables",
        confirmLabel = "Guardar responsables",
        canConfirm = selected.isNotEmpty() && selected != activity.assigneeIds.toSet(),
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(selected) },
    ) {
        Text("${activity.title} · ${activity.date.shortLabel()}", style = MaterialTheme.typography.titleSmall)
        Text(
            "Las personas agregadas recibirán un aviso de asignación.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        FieldLabel("Responsables", required = true)
        MemberPicker(options, selected, multiSelect = true, onChange = { selected = it }, emptyMessage = "No hay personas elegibles para esta actividad.")
    }
}

/** Reasigna una tarea a otra persona responsable. */
@Composable
fun ReassignTaskForm(
    task: ChurchActivity,
    eligible: List<Member>,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val currentId = task.assigneeIds.firstOrNull()
    var selected by remember { mutableStateOf(currentId) }
    FullScreenForm(
        title = "Reasignar tarea",
        confirmLabel = "Reasignar",
        canConfirm = selected != null && selected != currentId,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { selected?.let(onSubmit) },
    ) {
        Text(task.title, style = MaterialTheme.typography.titleSmall)
        task.parentActivityTitle?.let { Text("Actividad: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        Text("La nueva persona responsable recibirá un aviso.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        FieldLabel("Responsable", required = true)
        MemberPicker(eligible, setOfNotNull(selected), multiSelect = false, onChange = { selected = it.firstOrNull() }, emptyMessage = "No hay personas elegibles.")
    }
}

// Repertorio --------------------------------------------------------------------------------

/** Edita el repertorio único y reutilizable del ministerio de Alabanza. */
@Composable
fun RepertoireForm(
    songs: List<WorshipSong>,
    members: List<Member>,
    existing: WorshipSet?,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (List<WorshipSetSong>) -> Unit,
) {
    var entries by remember { mutableStateOf(existing?.songs.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val activeMembers = members.filter(Member::active)

    FullScreenForm(
        title = "Repertorio de Alabanza",
        confirmLabel = "Guardar y notificar",
        canConfirm = true,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(entries.mapIndexed { index, entry -> entry.copy(position = index + 1) }) },
    ) {
        MessageBanner(
            "Es el repertorio vigente del ministerio y se usa en todos los cultos y ensayos. " +
                "Al guardar se enviará un único aviso a Alabanza, al Pastor y a predicadores y directores de los próximos cultos.",
            BannerTone.Info,
        )
        if (entries.isEmpty()) {
            Text("Aún no hay canciones. Agrégalas desde el catálogo.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        entries.forEachIndexed { index, entry ->
            val song = songs.find { it.id == entry.songId }
            AppCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", style = MaterialTheme.typography.titleSmall, color = BrandLight, modifier = Modifier.width(28.dp))
                    Text(song?.title ?: "Canción", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { entries = entries.swap(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Subir ${song?.title}")
                    }
                    IconButton(onClick = { entries = entries.swap(index, index + 1) }, enabled = index < entries.lastIndex) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Bajar ${song?.title}")
                    }
                    IconButton(onClick = { entries = entries.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Quitar ${song?.title}", tint = Danger)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(
                        entry.key,
                        { value -> entries = entries.mapIndexed { i, e -> if (i == index) e.copy(key = value.take(4)) else e } },
                        "Tono",
                        modifier = Modifier.width(96.dp),
                    )
                    var leadMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { leadMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(
                                members.find { it.id == entry.leadId }?.name?.let { "Dirige: $it" } ?: "Quién dirige (opcional)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(expanded = leadMenu, onDismissRequest = { leadMenu = false }) {
                            DropdownMenuItem(text = { Text("Sin asignar") }, onClick = {
                                leadMenu = false
                                entries = entries.mapIndexed { i, e -> if (i == index) e.copy(leadId = null) else e }
                            })
                            activeMembers.forEach { person ->
                                DropdownMenuItem(text = { Text(person.name) }, onClick = {
                                    leadMenu = false
                                    entries = entries.mapIndexed { i, e -> if (i == index) e.copy(leadId = person.id) else e }
                                })
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { picking = true }, enabled = songs.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (songs.isEmpty()) "El catálogo está vacío" else "Agregar canción del catálogo")
        }
        Text("Ser quien dirige una canción es informativo; no crea una tarea.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }

    if (picking) {
        var query by remember { mutableStateOf("") }
        val available = songs.filter { song ->
            entries.none { it.songId == song.id } &&
                (song.title.contains(query.trim(), true) || song.artist.contains(query.trim(), true))
        }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Elegir canción") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextInput(query, { query = it }, "Buscar")
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        if (available.isEmpty()) {
                            Text("No hay canciones disponibles.", color = TextSecondary, modifier = Modifier.padding(8.dp))
                        }
                        available.forEach { song ->
                            TextButton(
                                onClick = {
                                    entries = entries + WorshipSetSong(song.id, entries.size + 1, song.defaultKey, null)
                                    picking = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    song.title + if (song.artist.isNotBlank()) " — ${song.artist}" else "",
                                    modifier = Modifier.fillMaxWidth(),
                                    color = TextPrimary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        )
    }
}

private fun <T> List<T>.swap(from: Int, to: Int): List<T> {
    if (to !in indices) return this
    return toMutableList().also {
        val item = it[from]
        it[from] = it[to]
        it[to] = item
    }
}
