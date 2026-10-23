package cl.iglesia.gestion

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun MoreScreen(
    member: Member,
    ministries: List<Ministry>,
    unreadNotifications: Int,
    onWorship: () -> Unit,
    onRecords: () -> Unit,
    onPersonnel: () -> Unit,
    onNotifications: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Herramientas", "Más opciones") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(member.initials, size = 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.titleMedium)
                        Text(member.role, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        if (member.email.isNotBlank()) Text(member.email, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                val myMinistries = ministries.filter { it.id in member.ministryIds }
                if (myMinistries.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Ministerios: " + myMinistries.joinToString { it.shortName + if (it.id in member.ledMinistryIds) " (líder)" else "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
        item { MoreOption(Icons.Outlined.MusicNote, "Alabanza", "Repertorios de cultos y catálogo de canciones", onClick = onWorship) }
        item { MoreOption(Icons.Outlined.Description, "Registros de reuniones", "Resúmenes, archivos y presentaciones", onClick = onRecords) }
        item {
            MoreOption(
                Icons.Outlined.People,
                if (member.isPastor) "Usuarios" else "Personal",
                if (member.isPastor) "Crear cuentas, roles, ministerios y acceso" else "Directorio de líderes y colaboradores",
                onClick = onPersonnel,
            )
        }
        item { MoreOption(Icons.Outlined.Lock, "Cambiar mi contraseña", "Actualiza la contraseña de tu cuenta", onClick = onChangePassword) }
        item {
            MoreOption(
                Icons.Outlined.NotificationsNone,
                "Avisos",
                if (unreadNotifications == 0) "No tienes avisos sin leer" else "$unreadNotifications avisos sin leer",
                badge = unreadNotifications,
                onClick = onNotifications,
            )
        }
        item { MoreOption(Icons.AutoMirrored.Outlined.Logout, "Cerrar sesión", "Salir de tu cuenta en este dispositivo", tint = Danger, onClick = onLogout) }
    }
}

@Composable
private fun MoreOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: Int = 0,
    tint: Color = BrandLight,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, container = if (tint == Danger) DangerSurface else BrandSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == Danger) Danger else TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (badge > 0) {
                Badge(containerColor = Danger) { Text(badge.toString()) }
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

// Alabanza ---------------------------------------------------------------------------

@Composable
fun WorshipScreen(
    activities: List<ChurchActivity>,
    members: List<Member>,
    songs: List<WorshipSong>,
    worshipSets: List<WorshipSet>,
    canManage: Boolean,
    onBack: () -> Unit,
    onCreateSong: () -> Unit,
    onEditRepertoire: (ChurchActivity) -> Unit,
    submitting: Boolean = false,
    onOpenFile: (RecordFile) -> Unit = {},
    onAddSongFiles: (WorshipSong, List<PickedFile>) -> Unit = { _, _ -> },
    onDeleteSongFile: (RecordFile) -> Unit = {},
    onError: (String) -> Unit = {},
    onEditSong: (WorshipSong) -> Unit = {},
    onDeleteSong: (WorshipSong) -> Unit = {},
) {
    val today = LocalDate.now()
    var attachTo by androidx.compose.runtime.remember { mutableStateOf<WorshipSong?>(null) }
    var deletingSong by androidx.compose.runtime.remember { mutableStateOf<WorshipSong?>(null) }
    var deletingFile by androidx.compose.runtime.remember { mutableStateOf<RecordFile?>(null) }
    val pickFiles = rememberFilePicker(
        onPicked = { files -> attachTo?.let { onAddSongFiles(it, files) } },
        onError = onError,
    )
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val cultos = activities.filter {
        it.kind == ItemKind.Activity && it.category == ActivityTypes.CULTO && !it.date.isBefore(today)
    }
    var selectedCultoId by rememberSaveable { mutableStateOf<String?>(null) }
    val culto = cultos.firstOrNull { it.id == selectedCultoId } ?: cultos.firstOrNull()
    val uriHandler = LocalUriHandler.current
    val filteredSongs = songs.filter {
        it.title.contains(query.trim(), ignoreCase = true) || it.artist.contains(query.trim(), ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BackLink(onBack) }
            item { ScreenHeader("Ministerio de Alabanza", "Alabanza") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Repertorio", true to "Catálogo").forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = showCatalog == value,
                            onClick = { showCatalog = value },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = BrandContainer, activeContentColor = Brand),
                        ) { Text(label) }
                    }
                }
            }
            if (!showCatalog) {
                if (cultos.isEmpty()) {
                    item { EmptyState("Sin cultos programados", "Cuando se programe un Culto podrás preparar su repertorio.", Icons.Outlined.Event) }
                } else {
                    if (cultos.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(cultos, key = { it.id }) { option ->
                                    FilterChip(
                                        selected = option.id == culto?.id,
                                        onClick = { selectedCultoId = option.id },
                                        label = { Text(option.date.relativeLabel()) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandContainer, selectedLabelColor = Brand),
                                    )
                                }
                            }
                        }
                    }
                    culto?.let { current ->
                        item {
                            AppCard(containerColor = Brand) {
                                Text("CULTO", style = MaterialTheme.typography.labelSmall, color = Gold)
                                Spacer(Modifier.height(4.dp))
                                Text(current.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                Text(
                                    "${current.date.longLabel()} · ${current.time}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Predica: ${members.find { it.id == current.preacherId }?.name ?: "Sin asignar"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Text(
                                    "Dirige alabanza: ${members.find { it.id == current.worshipLeaderId }?.name ?: "Sin asignar"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                        item {
                            SectionHeader(
                                "Canciones del repertorio",
                                actionLabel = if (canManage) "Editar" else null,
                                onAction = { onEditRepertoire(current) },
                            )
                        }
                        item {
                            AppCard {
                                RepertoireList(worshipSets.firstOrNull { it.activityId == current.id }, songs, members, onOpenFile)
                            }
                        }
                    }
                }
            } else {
                item { TextInput(value = query, onValueChange = { query = it }, label = "Buscar canción o artista") }
                if (filteredSongs.isEmpty()) {
                    item {
                        EmptyState(
                            if (songs.isEmpty()) "Catálogo vacío" else "Sin coincidencias",
                            if (songs.isEmpty()) "Las canciones del ministerio aparecerán aquí." else "Prueba con otra búsqueda.",
                            Icons.Outlined.LibraryMusic,
                        )
                    }
                }
                items(filteredSongs, key = { it.id }) { song ->
                    AppCard(contentPadding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.MusicNote, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (song.artist.isNotBlank()) Text(song.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Pill("Tono ${song.defaultKey}", Brand, BrandSurface)
                        }
                        if (song.referenceUrl.startsWith("https://")) {
                            TextButton(onClick = { runCatching { uriHandler.openUri(song.referenceUrl) } }) { Text("Escuchar versión de referencia") }
                        }
                        song.files.forEach { file ->
                            FileRow(
                                name = file.name,
                                contentType = file.contentType,
                                size = file.sizeBytes,
                                onOpen = { onOpenFile(file) },
                                onRemove = if (canManage && !submitting) ({ deletingFile = file }) else null,
                            )
                        }
                        if (canManage) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    attachTo = song
                                    pickFiles()
                                },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Adjuntar partitura o letra")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { onEditSong(song) },
                                    enabled = !submitting,
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                ) { Text("Editar") }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { deletingSong = song },
                                    enabled = !submitting,
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                ) { Text("Eliminar", color = Danger) }
                            }
                        }
                    }
                }
            }
            // Espacio para que el botón flotante no tape la última tarjeta.
            item { Spacer(Modifier.height(88.dp)) }
        }
        if (canManage && showCatalog) {
            ExtendedFloatingActionButton(
                onClick = onCreateSong,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Agregar canción") },
                containerColor = Brand,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    deletingSong?.let { song ->
        ConfirmDialog(
            title = "¿Eliminar \"${song.title}\"?",
            message = "Se quitará del catálogo" +
                (if (song.files.isEmpty()) "" else " junto con sus ${song.files.size} archivo(s)") +
                ". Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            destructive = true,
            onDismiss = { deletingSong = null },
            onConfirm = {
                deletingSong = null
                onDeleteSong(song)
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
                onDeleteSongFile(file)
            },
        )
    }
}

// Personal -----------------------------------------------------------------------------

@Composable
fun PersonnelScreen(
    currentMember: Member,
    members: List<Member>,
    ministries: List<Ministry>,
    onBack: () -> Unit,
    onEdit: (Member) -> Unit,
    onCreate: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showInactive by rememberSaveable { mutableStateOf(true) }
    val filtered = members.filter {
        (it.name.contains(query.trim(), ignoreCase = true) || it.email.contains(query.trim(), ignoreCase = true)) &&
            (roleFilter == null || it.role == roleFilter) &&
            (showInactive || it.active)
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackLink(onBack) }
        item {
            ScreenHeader(
                if (currentMember.isPastor) "Administración" else "Directorio",
                if (currentMember.isPastor) "Usuarios" else "Personal",
                if (currentMember.isPastor) "Crea cuentas y toca a una persona para gestionar su rol, ministerios y acceso." else "Personas visibles según tus permisos.",
            )
        }
        if (currentMember.isPastor) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Activas" to members.count { it.active },
                        "Inactivas" to members.count { !it.active },
                        "Pastores" to members.count { it.isPastor && it.active },
                    ).forEach { (label, value) ->
                        AppCard(Modifier.weight(1f), contentPadding = 12.dp) {
                            Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = Brand)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
        item { TextInput(value = query, onValueChange = { query = it }, label = if (currentMember.isPastor) "Buscar por nombre o correo" else "Buscar persona") }
        if (currentMember.isPastor) {
            item { SwitchRow("Mostrar cuentas inactivas", showInactive, { showInactive = it }) }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<String?>(null) + Roles.all) { role ->
                    FilterChip(
                        selected = roleFilter == role,
                        onClick = { roleFilter = role },
                        label = { Text(role ?: "Todos") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandContainer, selectedLabelColor = Brand),
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item { EmptyState("Sin resultados", "No hay personas que coincidan con la búsqueda.", Icons.Outlined.PersonAddAlt) }
        }
        items(filtered, key = { it.id }) { person ->
            val personMinistries = ministries.filter { it.id in person.ministryIds }
            val accent = personMinistries.firstOrNull()?.color?.toColor() ?: BrandLight
            AppCard(onClick = if (currentMember.isPastor) ({ onEdit(person) }) else null, contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(person.initials, accent, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall)
                        Text(person.role, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            personMinistries.joinToString { it.shortName }.ifBlank { "Sin ministerio" },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentMember.isPastor && person.email.isNotBlank()) {
                            Text(
                                person.email + (person.lastSignIn?.let { " · ${it.relativeLabel()}" } ?: " · sin accesos"),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!person.active) Pill("Inactiva", Danger, DangerSurface)
                    if (currentMember.isPastor) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }
    if (currentMember.isPastor) {
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Outlined.PersonAddAlt, contentDescription = null) },
            text = { Text("Nueva cuenta") },
            containerColor = Brand,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    }
}

// Avisos ---------------------------------------------------------------------------------

@Composable
fun NotificationsScreen(
    notifications: List<ChurchNotification>,
    submitting: Boolean,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val unread = notifications.filter { !it.read }
    val read = notifications.filter { it.read }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackLink(onBack) }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenHeader("Notificaciones", "Avisos", modifier = Modifier.weight(1f))
                if (unread.isNotEmpty()) {
                    TextButton(onClick = onMarkAllRead, enabled = !submitting) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar todo")
                    }
                }
            }
        }
        if (notifications.isEmpty()) {
            item { EmptyState("Sin avisos", "Las notificaciones dirigidas a tu cuenta aparecerán aquí.", Icons.Outlined.NotificationsNone) }
        }
        if (unread.isNotEmpty()) {
            item { SectionHeader("Sin leer (${unread.size})") }
            items(unread, key = { it.id }) { NotificationCard(it, submitting, onMarkRead) }
        }
        if (read.isNotEmpty()) {
            item { SectionHeader("Anteriores") }
            items(read, key = { it.id }) { NotificationCard(it, submitting, onMarkRead) }
        }
    }
}

@Composable
private fun NotificationCard(notification: ChurchNotification, submitting: Boolean, onMarkRead: (String) -> Unit) {
    val icon = when (notification.type) {
        "assignment" -> Icons.Outlined.PersonAddAlt
        "schedule" -> Icons.Outlined.Event
        else -> Icons.Outlined.Campaign
    }
    AppCard(
        onClick = if (notification.read || submitting) null else ({ onMarkRead(notification.id) }),
        containerColor = if (notification.read) Color.White else GoldSurface,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon, tint = if (notification.read) TextMuted else GoldText, container = if (notification.read) MutedSurface else Color.White, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).heightIn(min = 36.dp)) {
                Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(notification.type.toNotificationLabel(), notification.createdAt?.relativeLabel()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                if (!notification.read) {
                    Text("Toca para marcar como leído", style = MaterialTheme.typography.bodySmall, color = GoldText)
                }
            }
        }
    }
}

private fun String.toNotificationLabel(): String = when (this) {
    "assignment" -> "Asignación"
    "schedule" -> "Agenda"
    else -> "Sistema"
}
