package cl.iglesia.gestion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

// Ministerios -----------------------------------------------------------------------

@Composable
fun MinistriesScreen(
    member: Member,
    ministries: List<Ministry>,
    members: List<Member>,
    submitting: Boolean,
    onCreateMinistry: () -> Unit,
    onAddMember: (ministryId: String, userId: String, leader: Boolean) -> Unit,
    onSetLeader: (ministryId: String, userId: String, leader: Boolean) -> Unit,
    onRemoveMember: (ministryId: String, userId: String) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = ministries.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        MinistryDetail(
            ministry = selected,
            member = member,
            members = members,
            submitting = submitting,
            onBack = { selectedId = null },
            onAddMember = onAddMember,
            onSetLeader = onSetLeader,
            onRemoveMember = onRemoveMember,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Comunidad", "Ministerios", "Equipos que sirven y crecen juntos.") }
            if (ministries.isEmpty()) {
                item { EmptyState("Sin ministerios", "Los ministerios activos aparecerán aquí.", Icons.Outlined.Groups) }
            }
            items(ministries, key = { it.id }) { ministry ->
                val count = members.count { ministry.id in it.ministryIds }
                val leaders = ministry.leaderIds.mapNotNull { id -> members.find { it.id == id }?.name }
                AppCard(onClick = { selectedId = ministry.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SymbolBadge(ministry.icon, ministry.color.toColor(), size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ministry.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(if (count == 1) "1 integrante" else "$count integrantes", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                    if (ministry.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(ministry.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (leaders.isEmpty()) "Sin líder asignado" else "Líder: ${leaders.joinToString()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ministry.color.toColor(),
                    )
                }
            }
        }
        if (member.isPastor) {
            ExtendedFloatingActionButton(
                onClick = onCreateMinistry,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nuevo ministerio") },
                containerColor = Brand,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun MinistryDetail(
    ministry: Ministry,
    member: Member,
    members: List<Member>,
    submitting: Boolean,
    onBack: () -> Unit,
    onAddMember: (String, String, Boolean) -> Unit,
    onSetLeader: (String, String, Boolean) -> Unit,
    onRemoveMember: (String, String) -> Unit,
) {
    val ministryMembers = members.filter { ministry.id in it.ministryIds }
    val candidates = members.filter { it.active && ministry.id !in it.ministryIds }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Member?>(null) }
    var newMemberId by remember { mutableStateOf<String?>(null) }
    var newMemberLeader by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBadge(ministry.icon, ministry.color.toColor(), size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(ministry.name, style = MaterialTheme.typography.headlineSmall, color = Brand)
                    Text("${ministryMembers.size} integrantes", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
        if (ministry.description.isNotBlank()) {
            item { Text(ministry.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
        item {
            SectionHeader(
                "Integrantes",
                actionLabel = if (member.isPastor && candidates.isNotEmpty()) "Agregar" else null,
                onAction = {
                    newMemberId = null
                    newMemberLeader = false
                    adding = true
                },
            )
        }
        if (ministryMembers.isEmpty()) {
            item { EmptyState("Sin integrantes visibles", "Los integrantes aparecen según tus permisos.", Icons.Outlined.Groups) }
        }
        items(ministryMembers, key = { it.id }) { person ->
            val isLeader = person.id in ministry.leaderIds
            AppCard(contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.initials, ministry.color.toColor())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall)
                        Text(person.role, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (isLeader) Pill("Líder", GoldText, GoldSurface)
                    if (member.isPastor) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = !submitting) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones para ${person.name}")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isLeader) "Quitar liderazgo" else "Marcar como líder") },
                                    onClick = {
                                        menu = false
                                        onSetLeader(ministry.id, person.id, !isLeader)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Retirar del ministerio", color = Danger) },
                                    onClick = {
                                        menu = false
                                        removing = person
                                    },
                                )
                            }
                        }
                    }
                }
                if (isLeader && !person.isMinistryLeader && !person.isPastor) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Para gestionar el ministerio, su rol debe ser \"Líder de ministerio\" (Más › Personal).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning,
                    )
                }
            }
        }
    }

    if (adding) {
        FullScreenForm(
            title = "Agregar a ${ministry.shortName}",
            confirmLabel = "Agregar integrante",
            canConfirm = newMemberId != null,
            submitting = submitting,
            error = null,
            onDismiss = { adding = false },
            onConfirm = {
                newMemberId?.let { onAddMember(ministry.id, it, newMemberLeader) }
                adding = false
            },
        ) {
            FieldLabel("Persona", required = true)
            MemberPicker(
                members = candidates,
                selectedIds = setOfNotNull(newMemberId),
                multiSelect = false,
                onChange = { newMemberId = it.firstOrNull() },
                emptyMessage = "Todas las personas activas ya pertenecen a este ministerio.",
            )
            SwitchRow(
                label = "Líder del ministerio",
                checked = newMemberLeader,
                onCheckedChange = { newMemberLeader = it },
                supporting = "Podrá crear actividades y asignar tareas si su rol es Líder de ministerio.",
            )
        }
    }
    removing?.let { person ->
        ConfirmDialog(
            title = "¿Retirar a ${person.firstName}?",
            message = "${person.name} dejará de pertenecer a ${ministry.name} y perderá acceso a sus actividades.",
            confirmLabel = "Retirar",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                onRemoveMember(ministry.id, person.id)
            },
        )
    }
}

@Composable
fun BackLink(onBack: () -> Unit) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(start = 0.dp, end = 12.dp)) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Volver")
    }
}

// Proyectos ------------------------------------------------------------------------

private val ProjectFilters = listOf<String?>(null) + WORK_STATUSES

@Composable
fun ProjectsScreen(
    projects: List<ChurchProject>,
    activities: List<ChurchActivity>,
    members: List<Member>,
    canManage: Boolean,
    onCreate: () -> Unit,
    onEdit: (ChurchProject) -> Unit,
    onOpenActivity: (ChurchActivity) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = projects.filter { filter == null || it.status == filter }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Planificación", "Proyectos", "Proyectos institucionales de toda la iglesia.") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProjectFilters) { status ->
                        val count = if (status == null) projects.size else projects.count { it.status == status }
                        FilterChip(
                            selected = filter == status,
                            onClick = { filter = status },
                            label = { Text("${status?.let { statusStyle(it, feminine = false).label } ?: "Todos"} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandContainer, selectedLabelColor = Brand),
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        if (projects.isEmpty()) "Sin proyectos" else "Sin proyectos con este estado",
                        if (canManage) "Crea el primer proyecto con el botón \"Nuevo proyecto\"." else "Los proyectos visibles para ti aparecerán aquí.",
                        Icons.Outlined.FolderOpen,
                    )
                }
            }
            items(visible, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    leader = members.find { it.id == project.leaderId },
                    activities = activities.filter { it.projectId == project.id && it.kind == ItemKind.Activity },
                    expanded = expandedId == project.id,
                    canManage = canManage,
                    onToggle = { expandedId = if (expandedId == project.id) null else project.id },
                    onEdit = { onEdit(project) },
                    onOpenActivity = onOpenActivity,
                )
            }
        }
        if (canManage) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Nuevo proyecto") },
                containerColor = Brand,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ChurchProject,
    leader: Member?,
    activities: List<ChurchActivity>,
    expanded: Boolean,
    canManage: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenActivity: (ChurchActivity) -> Unit,
) {
    val today = LocalDate.now()
    val finished = project.status == "completed" || project.status == "cancelled"
    val overdue = !finished && project.dueDate.isBefore(today)
    AppCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(project.status, feminine = false)
            Spacer(Modifier.weight(1f))
            Text(
                if (finished) "Fin: ${project.dueDate.fieldLabel()}" else dueLabel(project.dueDate, today),
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) Danger else TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(project.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (project.objective.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                project.objective,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Avance", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("${project.progress}%", style = MaterialTheme.typography.labelLarge, color = Brand)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { project.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Avance ${project.progress} por ciento" },
            color = if (project.progress == 100) Success else BrandLight,
            trackColor = MutedSurface,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (activities.isEmpty()) "Sin actividades asociadas: avance 0 %" else "Calculado de ${activities.size} actividad(es)",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(leader?.initials ?: "?", size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(leader?.name ?: "Responsable sin asignar", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(if (expanded) "Ocultar" else "Ver detalle", style = MaterialTheme.typography.labelMedium, color = BrandLight)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (project.description.isNotBlank()) LabeledValue("Descripción", project.description)
                Row {
                    LabeledValue("Inicio", project.startDate.fieldLabel(), Modifier.weight(1f))
                    LabeledValue("Fin estimado", project.dueDate.fieldLabel(), Modifier.weight(1f))
                }
                if (project.budgetEstimated != null || project.budgetExecuted != null) {
                    Row {
                        LabeledValue("Presupuesto estimado", project.budgetEstimated?.toCLPCurrency() ?: "—", Modifier.weight(1f))
                        LabeledValue("Ejecutado", project.budgetExecuted?.toCLPCurrency() ?: "—", Modifier.weight(1f))
                    }
                    val estimated = project.budgetEstimated
                    val executed = project.budgetExecuted
                    if (estimated != null && estimated > 0 && executed != null) {
                        val used = (executed * 100 / estimated).toInt()
                        Text(
                            "Se ha ejecutado el $used % del presupuesto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (used > 100) Danger else TextSecondary,
                        )
                    }
                }
                Text("Actividades", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                if (activities.isEmpty()) {
                    Text("Asocia actividades a este proyecto desde la Agenda.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                activities.forEach { activity ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenActivity(activity) }, modifier = Modifier.weight(1f)) {
                            Text(
                                "${activity.date.shortLabel()} · ${activity.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                color = TextPrimary,
                            )
                        }
                        StatusPill(activity.status)
                    }
                }
                if (canManage) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Editar proyecto")
                    }
                }
            }
        }
    }
}
