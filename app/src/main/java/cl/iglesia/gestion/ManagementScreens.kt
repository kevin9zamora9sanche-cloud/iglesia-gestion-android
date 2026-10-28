package cl.iglesia.gestion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
    onAddMember: (Ministry) -> Unit,
    onSetLeader: (ministryId: String, userId: String, leader: Boolean) -> Unit,
    onRemoveMember: (ministryId: String, userId: String) -> Unit,
    inactiveMinistries: List<Ministry> = emptyList(),
    onEditMinistry: (Ministry) -> Unit = {},
    onSetMinistryActive: (Ministry, Boolean) -> Unit = { _, _ -> },
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var reactivating by remember { mutableStateOf<Ministry?>(null) }
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
            onEdit = { onEditMinistry(selected) },
            onDeactivate = {
                selectedId = null
                onSetMinistryActive(selected, false)
            },
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
            if (member.isPastor && inactiveMinistries.isNotEmpty()) {
                item {
                    SectionHeader("Ministerios desactivados (${inactiveMinistries.size})")
                    Text(
                        "No aparecen para el resto de los usuarios. Sus integrantes y registros se conservan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                items(inactiveMinistries, key = { "inactive-${it.id}" }) { ministry ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolBadge(ministry.icon, TextMuted, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ministry.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Pill("Desactivado", TextSecondary, MutedSurface)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(
                            onClick = { reactivating = ministry },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("Reactivar ministerio") }
                    }
                }
            }
            // Espacio para que el botón flotante no tape la última tarjeta.
            item { Spacer(Modifier.height(88.dp)) }
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

    reactivating?.let { ministry ->
        ConfirmDialog(
            title = "¿Reactivar ${ministry.name}?",
            message = "El ministerio volverá a aparecer para todos, con los integrantes que tenía.",
            confirmLabel = "Reactivar",
            onDismiss = { reactivating = null },
            onConfirm = {
                reactivating = null
                onSetMinistryActive(ministry, true)
            },
        )
    }
}

@Composable
private fun MinistryDetail(
    ministry: Ministry,
    member: Member,
    members: List<Member>,
    submitting: Boolean,
    onBack: () -> Unit,
    onAddMember: (Ministry) -> Unit,
    onSetLeader: (String, String, Boolean) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val ministryMembers = members.filter { ministry.id in it.ministryIds }
    val candidates = members.filter { it.active && ministry.id !in it.ministryIds }
    var removing by remember { mutableStateOf<Member?>(null) }
    var confirmDeactivate by remember { mutableStateOf(false) }

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
                onAction = { onAddMember(ministry) },
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
        if (member.isPastor) {
            item {
                SectionHeader("Administrar ministerio")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Editar nombre y descripción")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmDeactivate = true },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Desactivar ministerio", color = Danger) }
            }
        }
    }

    if (confirmDeactivate) {
        ConfirmDialog(
            title = "¿Desactivar ${ministry.name}?",
            message = "Dejará de aparecer para los usuarios. Sus integrantes y registros se conservan y podrás reactivarlo cuando quieras desde la lista de ministerios.",
            confirmLabel = "Desactivar",
            destructive = true,
            onDismiss = { confirmDeactivate = false },
            onConfirm = {
                confirmDeactivate = false
                onDeactivate()
            },
        )
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
                    tasks = activities.filter { it.isTask && it.projectId == project.id },
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
    tasks: List<ChurchActivity>,
    expanded: Boolean,
    canManage: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenActivity: (ChurchActivity) -> Unit,
) {
    val today = LocalDate.now()
    // Misma regla que el avance del proyecto: sin tareas vale 100 % solo si está completada.
    fun activityProgress(activity: ChurchActivity): Int {
        val childTasks = tasks.filter { it.parentActivityId == activity.id }
        return if (childTasks.isEmpty()) {
            if (activity.status == "completed") 100 else 0
        } else {
            childTasks.count { it.status == "completed" } * 100 / childTasks.size
        }
    }
    val completedActivities = activities.count { activityProgress(it) == 100 }
    val finished = project.status == "completed"
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Avance del proyecto", style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(
                "${project.progress}%",
                style = MaterialTheme.typography.headlineSmall,
                color = if (project.progress == 100) Success else Brand,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { project.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Avance del proyecto: ${project.progress} por ciento" },
            color = if (project.progress == 100) Success else BrandLight,
            trackColor = MutedSurface,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (activities.isEmpty()) {
                "Sin actividades asociadas todavía: avance 0 %."
            } else {
                "$completedActivities de ${activities.size} actividad(es) completas · se actualiza al completar actividades y tareas."
            },
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
                    val value = activityProgress(activity)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenActivity(activity) }
                            .heightIn(min = 56.dp)
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${activity.date.shortLabel()} · ${activity.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = TextPrimary,
                            )
                            Text("$value%", style = MaterialTheme.typography.labelLarge, color = BrandLight)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { value / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = if (value == 100) Success else BrandLight,
                            trackColor = MutedSurface,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                if (canManage) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Editar proyecto")
                    }
                }
            }
        }
    }
}
