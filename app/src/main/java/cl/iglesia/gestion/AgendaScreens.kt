package cl.iglesia.gestion

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Inicio: "Mis pendientes" ------------------------------------------------------

@Composable
fun HomeScreen(
    member: Member,
    activities: List<ChurchActivity>,
    ministries: List<Ministry>,
    notifications: List<ChurchNotification>,
    onOpenItem: (ChurchActivity) -> Unit,
    onOpenAgenda: (personal: Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val today = LocalDate.now()
    val mine = activities.filter { it.involves(member.id) }
    // Trabajo propio con fecha y sin terminar desde hoy, más las tareas vencidas.
    val pending = mine
        .filter { !it.isDone && (!it.date.isBefore(today) || it.isTask) }
        .sortedWith(compareBy(ChurchActivity::date, ChurchActivity::time))
    val pendingTasks = pending.count { it.isTask }
    val thisWeek = mine.count { !it.isDone && !it.date.isBefore(today) && it.date.isBefore(today.plusDays(7)) }
    val unread = notifications.filter { !it.read }
    val upcomingChurch = activities
        .filter { !it.isTask && !it.isDone && !it.date.isBefore(today) && !it.involves(member.id) }
        .take(3)
    val myMinistries = ministries.filter { it.id in member.ministryIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = today.longLabel(),
                title = "Hola, ${member.firstName}",
                subtitle = buildString {
                    append(member.role)
                    if (myMinistries.isNotEmpty()) append(" · ").append(myMinistries.joinToString { it.shortName })
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("Tareas pendientes", pendingTasks, Icons.Outlined.TaskAlt, Modifier.weight(1f)) { onOpenAgenda(true) }
                SummaryTile("Próximos 7 días", thisWeek, Icons.Outlined.CalendarMonth, Modifier.weight(1f)) { onOpenAgenda(true) }
                SummaryTile("Avisos sin leer", unread.size, Icons.Outlined.NotificationsNone, Modifier.weight(1f), onOpenNotifications)
            }
        }
        item { SectionHeader("Mis pendientes", actionLabel = "Ver agenda", onAction = { onOpenAgenda(true) }) }
        if (pending.isEmpty()) {
            item {
                EmptyState(
                    "Estás al día",
                    "No tienes tareas ni actividades pendientes con fecha.",
                    Icons.Outlined.EventAvailable,
                )
            }
        } else {
            items(pending.take(6), key = { "p-" + it.kind.name + it.id }) { item ->
                ActivityListCard(item, ministries, onClick = { onOpenItem(item) }, showDate = true)
            }
            if (pending.size > 6) {
                item {
                    TextButton(onClick = { onOpenAgenda(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver ${pending.size - 6} pendientes más")
                    }
                }
            }
        }
        if (unread.isNotEmpty()) {
            item { SectionHeader("Avisos sin leer", actionLabel = "Ver todos", onAction = onOpenNotifications) }
            items(unread.take(3), key = { "n-" + it.id }) { notification ->
                AppCard(onClick = onOpenNotifications, containerColor = GoldSurface, contentPadding = 14.dp) {
                    Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    notification.createdAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.relativeLabel(), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
        if (upcomingChurch.isNotEmpty()) {
            item { SectionHeader("Próximo en la iglesia", actionLabel = "Agenda general", onAction = { onOpenAgenda(false) }) }
            items(upcomingChurch, key = { "u-" + it.kind.name + it.id }) { item ->
                ActivityListCard(item, ministries, onClick = { onOpenItem(item) }, showDate = true)
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        onClick = onClick,
        contentPadding = 12.dp,
    ) {
        Icon(icon, contentDescription = null, tint = BrandLight, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = Brand)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, minLines = 2)
    }
}

// Tarjeta de elemento de agenda --------------------------------------------------

@Composable
fun ActivityListCard(
    item: ChurchActivity,
    ministries: List<Ministry>,
    onClick: () -> Unit,
    showDate: Boolean = false,
) {
    val ministry = ministries.find { it.id == item.ministryId }
    val accent = ministry?.color?.toColor() ?: Brand
    val overdue = item.isTask && item.isOverdue()
    AppCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            SymbolBadge(item.typeSymbol(ministry), accent, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val whenText = buildString {
                    if (showDate) append(item.date.relativeLabel()).append(" · ")
                    append(item.time)
                    if (!item.isTask && item.endTime.isNotBlank()) append("–").append(item.endTime)
                    if (item.location.isNotBlank()) append(" · ").append(item.location)
                }
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.parentActivityTitle?.let {
                    Text("En: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(item.typeLabel, Brand, BrandSurface)
                    if (!item.isMeeting) StatusPill(item.status)
                    if (overdue) Pill("Vencida", Danger, DangerSurface)
                }
            }
        }
    }
}

private fun ChurchActivity.typeSymbol(ministry: Ministry?): String = when {
    isTask -> "✓"
    isMeeting -> "◎"
    category == ActivityTypes.CULTO -> "✝"
    category == ActivityTypes.ENSAYO -> "♫"
    category == ActivityTypes.ASEO -> "✦"
    category == ActivityTypes.LEADERS_MEETING -> "◎"
    else -> ministry?.icon ?: "▣"
}

// Agenda -------------------------------------------------------------------------

private val TypeFilters = listOf("Todos") + ActivityTypes.all + listOf("Tarea", "Reunión")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    member: Member,
    permissions: Permissions,
    activities: List<ChurchActivity>,
    ministries: List<Ministry>,
    personal: Boolean,
    onPersonalChange: (Boolean) -> Unit,
    onOpenItem: (ChurchActivity) -> Unit,
    onCreate: (FormRequest) -> Unit,
) {
    val today = LocalDate.now()
    var selectedDate by rememberSaveable { mutableStateOf(today) }
    var typeFilter by rememberSaveable { mutableStateOf("Todos") }
    var createMenu by remember { mutableStateOf(false) }
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = (0L..6L).map(weekStart::plusDays)

    val visible = activities.filter { item ->
        (!personal || item.involves(member.id)) &&
            (typeFilter == "Todos" || item.typeLabel == typeFilter)
    }
    val datesWithItems = visible.mapTo(mutableSetOf(), ChurchActivity::date)
    val dayItems = visible.filter { it.date == selectedDate }.sortedBy(ChurchActivity::time)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ScreenHeader(
                        "Calendario",
                        "Agenda",
                        if (personal) "Lo que tienes asignado o donde participas." else "Toda la actividad visible para ti.",
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false to "General", true to "Personal").forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = personal == value,
                                onClick = { onPersonalChange(value) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = BrandContainer, activeContentColor = Brand),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedDate = selectedDate.minusWeeks(1) }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Semana anterior")
                        }
                        Text(
                            weekStart.monthLabel(),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedDate != today) {
                            TextButton(onClick = { selectedDate = today }) { Text("Hoy") }
                        }
                        IconButton(onClick = { selectedDate = selectedDate.plusWeeks(1) }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Semana siguiente")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                selected = date == selectedDate,
                                isToday = date == today,
                                hasItems = date in datesWithItems,
                                onClick = { selectedDate = date },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TypeFilters) { filter ->
                        FilterChip(
                            selected = typeFilter == filter,
                            onClick = { typeFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandContainer,
                                selectedLabelColor = Brand,
                            ),
                        )
                    }
                }
            }
            item {
                Text(
                    selectedDate.longLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (dayItems.isEmpty()) {
                item {
                    EmptyState(
                        "Sin elementos este día",
                        if (personal) "No tienes asignaciones para esta fecha." else "Elige otra fecha o cambia el filtro.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(dayItems, key = { it.kind.name + it.id }) { item ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        ActivityListCard(item, ministries, onClick = { onOpenItem(item) })
                    }
                }
            }
        }

        if (permissions.canManageAnyMinistry) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { createMenu = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Nuevo") },
                    containerColor = Brand,
                    contentColor = Color.White,
                )
                DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Actividad") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        onClick = {
                            createMenu = false
                            onCreate(FormRequest.Activity)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Tarea de una actividad") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null) },
                        onClick = {
                            createMenu = false
                            onCreate(FormRequest.Task(null))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Convocatoria de reunión") },
                        leadingIcon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                        onClick = {
                            createMenu = false
                            onCreate(FormRequest.Meeting)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    hasItems: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        selected -> Brand
        isToday -> BrandSurface
        else -> Color.White
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = date.longLabel() + if (hasItems) ", con actividades" else ""
            }
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.weekdayInitials(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White.copy(alpha = 0.85f) else TextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) Color.White else TextPrimary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (hasItems) (if (selected) Gold else BrandLight) else Color.Transparent),
        )
    }
}

// Detalle --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailSheet(
    item: ChurchActivity,
    member: Member,
    permissions: Permissions,
    activities: List<ChurchActivity>,
    ministries: List<Ministry>,
    members: List<Member>,
    projects: List<ChurchProject>,
    worshipSets: List<WorshipSet>,
    songs: List<WorshipSong>,
    meetingRecords: List<MeetingRecord>,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenItem: (ChurchActivity) -> Unit,
    onAddTask: () -> Unit,
    onEditRepertoire: () -> Unit,
    onChangeStatus: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ministry = ministries.find { it.id == item.ministryId }
    val project = projects.find { it.id == item.projectId }
    fun nameOf(id: String?) = members.find { it.id == id }?.name
    val canManage = permissions.canManage(item)
    val canChangeStatus = when {
        item.isMeeting -> false
        item.isTask -> item.isAssignedToCurrentUser || canManage
        else -> canManage
    }
    val childTasks = if (item.kind == ItemKind.Activity) activities.filter { it.parentActivityId == item.id } else emptyList()
    val isCulto = item.kind == ItemKind.Activity && item.category == ActivityTypes.CULTO
    val worshipSet = worshipSets.firstOrNull { it.activityId == item.id }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(item.typeLabel, Brand, BrandSurface)
                if (!item.isMeeting) StatusPill(item.status)
                if (item.isTask && item.isOverdue()) Pill("Vencida", Danger, DangerSurface)
            }
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Column {
                DetailRow(
                    Icons.Outlined.Schedule,
                    when {
                        !item.isTask -> "${item.date.longLabel()} · ${item.time}–${item.endTime}"
                        item.dueAt != null -> "Fecha límite: ${item.date.longLabel()} · ${item.time}"
                        else -> "Sin fecha límite (actividad: ${item.date.shortLabel()})"
                    },
                )
                if (item.location.isNotBlank()) DetailRow(Icons.Outlined.Place, item.location)
                val classification = when (item.classification) {
                    Classification.Ministry -> "Ministerio: ${ministry?.name ?: "—"}"
                    Classification.Project -> "Proyecto: ${project?.name ?: "—"}"
                    Classification.Individual -> "Actividad individual"
                    Classification.General -> if (item.isMeeting) "Reunión general" else "General (toda la iglesia)"
                }
                DetailRow(
                    if (item.classification == Classification.Project) Icons.Outlined.FolderOpen else Icons.Outlined.Groups,
                    classification,
                )
            }
            if (item.description.isNotBlank()) {
                LabeledValue(if (item.isTask) "Instrucciones" else "Descripción", item.description)
            }
            if (item.isTask) {
                item.parentActivityTitle?.let { LabeledValue("Actividad", it) }
                item.priority?.let { LabeledValue("Prioridad", it.toPriorityLabel()) }
                if (item.comments.isNotBlank()) LabeledValue("Comentarios", item.comments)
            }
            if (isCulto) {
                LabeledValue("Predicador", nameOf(item.preacherId) ?: "Sin asignar")
                LabeledValue("Director de alabanza", nameOf(item.worshipLeaderId) ?: "Sin asignar")
            }
            val people = item.assigneeIds.mapNotNull { id -> members.find { it.id == id } }
            if (item.assigneeIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            item.isMeeting -> "Invitados"
                            item.isTask -> "Responsable"
                            else -> "Responsables"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    people.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.initials, size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(person.name + if (person.id == member.id) " (tú)" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val hidden = item.assigneeIds.size - people.size
                    if (hidden > 0) {
                        Text("y $hidden persona(s) más", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }

            if (item.isMeeting) {
                RecordLinks(meetingRecords.filter { it.meetingId == item.id }, onOpenRecords)
            }

            if (canChangeStatus) {
                HorizontalDivider(color = CardBorder)
                FieldLabel("Cambiar estado")
                ChoiceChips(
                    options = WORK_STATUSES,
                    selected = item.status,
                    label = { statusStyle(it).label },
                    onSelect = { if (it != item.status && !submitting) onChangeStatus(it) },
                    enabled = { !submitting },
                )
                if (item.isTask && !canManage) {
                    Text("Como responsable, solo puedes cambiar el estado de tu tarea.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            if (isCulto) {
                HorizontalDivider(color = CardBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LibraryMusic, contentDescription = null, tint = BrandLight)
                    Spacer(Modifier.width(8.dp))
                    Text("Repertorio", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (permissions.canManageWorship) {
                        TextButton(onClick = onEditRepertoire) { Text(if (worshipSet == null) "Crear" else "Editar") }
                    }
                }
                RepertoireList(worshipSet, songs, members)
            }

            if (item.kind == ItemKind.Activity && !item.isLegacyTask) {
                HorizontalDivider(color = CardBorder)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = BrandLight)
                    Spacer(Modifier.width(8.dp))
                    Text("Tareas (${childTasks.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (canManage) {
                        TextButton(onClick = onAddTask) { Text("Agregar") }
                    }
                }
                if (childTasks.isEmpty()) {
                    Text(
                        if (item.status == "completed") "Sin tareas: la actividad cuenta como 100 % de avance." else "Sin tareas visibles para ti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                } else {
                    val done = childTasks.count { it.status == "completed" }
                    Text("$done de ${childTasks.size} completadas", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    childTasks.forEach { task ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onOpenItem(task) }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(nameOf(task.assigneeIds.firstOrNull()) ?: "Responsable", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            StatusPill(task.status)
                        }
                    }
                }
            }
            if (item.isTask && item.parentActivityId != null) {
                activities.firstOrNull { it.id == item.parentActivityId }?.let { parent ->
                    OutlinedButton(onClick = { onOpenItem(parent) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver actividad: ${parent.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun RepertoireList(worshipSet: WorshipSet?, songs: List<WorshipSong>, members: List<Member>) {
    val uriHandler = LocalUriHandler.current
    if (worshipSet == null || worshipSet.songs.isEmpty()) {
        Text("Aún no hay canciones en el repertorio.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        worshipSet.songs.forEach { entry ->
            val song = songs.find { it.id == entry.songId }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.position}.", style = MaterialTheme.typography.labelLarge, color = BrandLight, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(song?.title ?: "Canción", style = MaterialTheme.typography.bodyMedium)
                    val lead = members.find { it.id == entry.leadId }?.name
                    val details = listOfNotNull(song?.artist?.takeIf(String::isNotBlank), lead?.let { "Dirige: $it" })
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Pill("Tono ${entry.key}", Brand, BrandSurface)
                if (song?.referenceUrl?.startsWith("https://") == true) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(song.referenceUrl) } }) { Text("Versión") }
                }
            }
        }
        worshipSet.updatedAt?.let {
            Text("Última modificación: ${it.relativeLabel()}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
