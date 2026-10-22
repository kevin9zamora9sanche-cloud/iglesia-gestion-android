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
import androidx.compose.material.icons.outlined.Delete
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
        FieldLabel("Responsable", required = true)
        MemberPicker(leaderOptions, setOf(leaderId), multiSelect = false, onChange = { leaderId = it.first() }, emptyMessage = "No hay personas activas visibles.")
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

        FieldLabel("Responsables", required = true)
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
        FieldLabel("Prioridad", required = true)
        ChoiceChips(listOf("high", "medium", "low"), priority, { it.toPriorityLabel() }, { priority = it })
        DateField("Fecha límite (opcional)", dueDate, { dueDate = it }, clearable = true)
        if (dueDate != null) {
            TimeField("Hora límite", dueTime, { dueTime = it })
            Text("La tarea aparecerá en el calendario personal del responsable.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        TextInput(comments, { comments = it }, "Comentarios (opcional)", singleLine = false, minLines = 2)
        FieldLabel("Responsable", required = true)
        if (parent == null) {
            Text("Elige primero la actividad.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            MemberPicker(eligible, setOfNotNull(assigneeId), multiSelect = false, onChange = { assigneeId = it.firstOrNull() }, emptyMessage = "No hay integrantes activos del ministerio para asignar.")
        }
    }
}

// Canción, ministerio y perfil ----------------------------------------------------------------

private val MusicalKeys = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

@Composable
fun SongForm(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (WorshipSongDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("C") }
    var minor by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf("") }
    val linkValid = link.isBlank() || link.trim().startsWith("https://")
    FullScreenForm(
        title = "Agregar canción",
        confirmLabel = "Agregar al catálogo",
        canConfirm = title.isNotBlank() && linkValid,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(WorshipSongDraft(title, artist, if (minor) key + "m" else key, link)) },
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
    }
}

@Composable
fun MinistryForm(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MinistryDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    FullScreenForm(
        title = "Nuevo ministerio",
        confirmLabel = "Crear ministerio",
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

@Composable
fun ProfileForm(
    target: Member,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, role: String, active: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(target.name) }
    var phone by rememberSaveable { mutableStateOf(target.phone) }
    var role by rememberSaveable { mutableStateOf(target.role) }
    var active by rememberSaveable { mutableStateOf(target.active) }
    var confirmDeactivate by remember { mutableStateOf(false) }
    FullScreenForm(
        title = "Editar perfil",
        confirmLabel = "Guardar cambios",
        canConfirm = name.isNotBlank(),
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = {
            if (target.active && !active) confirmDeactivate = true else onSubmit(name, phone, role, active)
        },
    ) {
        if (target.email.isNotBlank()) Text(target.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        TextInput(name, { name = it }, "Nombre completo", required = true)
        TextInput(phone, { phone = it }, "Teléfono", keyboardType = KeyboardType.Phone)
        FieldLabel("Rol", required = true)
        ChoiceChips(Roles.all, role, { it }, { role = it })
        SwitchRow(
            "Cuenta activa",
            active,
            { active = it },
            supporting = "Una cuenta inactiva no puede iniciar sesión ni usar los módulos.",
        )
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
                onSubmit(name, phone, role, active)
            },
        )
    }
}

// Repertorio --------------------------------------------------------------------------------

@Composable
fun RepertoireForm(
    culto: ChurchActivity,
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
        title = "Repertorio",
        confirmLabel = "Guardar y notificar",
        canConfirm = culto.worshipLeaderId != null,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onConfirm = { onSubmit(entries.mapIndexed { index, entry -> entry.copy(position = index + 1) }) },
    ) {
        Text("${culto.title} · ${culto.date.longLabel()}", style = MaterialTheme.typography.titleSmall)
        if (culto.worshipLeaderId == null) {
            MessageBanner("Este Culto no tiene director de alabanza; asígnalo antes de preparar el repertorio.", BannerTone.Error)
        }
        MessageBanner(
            "Al guardar se enviará un único aviso al ministerio de Alabanza, al Pastor, al predicador y al director.",
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
                        OutlinedButton(onClick = { leadMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
        OutlinedButton(onClick = { picking = true }, enabled = songs.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
