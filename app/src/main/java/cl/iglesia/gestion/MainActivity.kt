package cl.iglesia.gestion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Outlined.Home),
    Calendar("Agenda", Icons.Outlined.CalendarMonth),
    Ministries("Ministerios", Icons.Outlined.Groups),
    Projects("Proyectos", Icons.Outlined.FolderOpen),
    More("Más", Icons.Outlined.Menu),
}

enum class MorePage { Menu, Worship, Records, Personnel, Notifications, Privacy }

/** Formularios que pueden abrirse sobre cualquier pantalla. */
sealed interface FormRequest {
    data class Project(val existing: ChurchProject?) : FormRequest
    data object Activity : FormRequest
    data object Meeting : FormRequest
    data class Task(val activityId: String?) : FormRequest
    data object Song : FormRequest
    data class EditSong(val songId: String) : FormRequest
    data object NewMinistry : FormRequest
    data class EditMinistry(val ministry: Ministry) : FormRequest
    data class Profile(val member: Member) : FormRequest
    data object Repertoire : FormRequest
    data object Record : FormRequest
    data object NewUser : FormRequest
    data object ChangePassword : FormRequest
    data class EditAssignees(val itemId: String) : FormRequest
    data class AddMember(val ministryId: String) : FormRequest
}

/** Permisos derivados del rol; replican RLS para ocultar acciones no permitidas. */
data class Permissions(
    val member: Member,
    val worshipMinistryId: String?,
) {
    val canManageAnyMinistry = member.isPastor || member.ledMinistryIds.isNotEmpty()
    val canManageProjects = member.isPastor
    val canManageWorship = worshipMinistryId != null && member.canManageMinistry(worshipMinistryId)
    fun canManage(item: ChurchActivity) = member.canManageMinistry(item.ministryId)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(30, 58, 138)),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        setContent {
            ChurchTheme {
                ChurchApp()
            }
        }
    }
}

private const val LOAD_ERROR =
    "No se pudieron cargar los datos. Revisa tu conexión y vuelve a intentarlo."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChurchApp() {
    var currentMember by remember { mutableStateOf<Member?>(null) }
    var churchSnapshot by remember { mutableStateOf<ChurchSnapshot?>(null) }
    var authLoading by remember { mutableStateOf(SupabaseBackend.isConfigured) }
    // Solo la restauración inicial usa pantalla de carga; el login conserva su formulario.
    var sessionChecked by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var modulesLoading by remember { mutableStateOf(false) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    var morePage by rememberSaveable { mutableStateOf(MorePage.Menu) }
    var calendarPersonal by rememberSaveable { mutableStateOf(false) }
    var openForm by remember { mutableStateOf<FormRequest?>(null) }
    var detailItemId by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    /** Correo y contraseña temporal de la última cuenta creada, para entregarlos en persona. */
    var createdAccount by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    /** La sesión sigue guardada, pero no se pudo cargar el perfil por falta de conexión. */
    var connectionError by remember { mutableStateOf(false) }
    /** Ya se envió el código de recuperación: la pantalla pide código y nueva contraseña. */
    var resetCodeSent by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    /** Usuario que aceptó la versión vigente de las políticas en este dispositivo. */
    var consentedUserId by remember { mutableStateOf<String?>(null) }

    suspend fun reload(member: Member) {
        modulesLoading = true
        try {
            churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
            moduleError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("ChurchApp", "Carga fallida", error)
            moduleError = LOAD_ERROR
        } finally {
            modulesLoading = false
        }
    }

    /**
     * Carga el perfil de la sesión guardada. Solo cierra la sesión si el servidor
     * rechaza la cuenta; un problema de red la conserva y ofrece reintentar, para no
     * obligar a escribir otra vez la contraseña por una conexión lenta.
     */
    suspend fun loadSessionMember() {
        authLoading = true
        connectionError = false
        try {
            val member = SupabaseBackend.loadCurrentMember()
            currentMember = member
            authMessage = null
            reload(member)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("ChurchApp", "No se pudo cargar el perfil", error)
            currentMember = null
            val rejected = error is io.github.jan.supabase.exceptions.RestException ||
                error.message?.startsWith("Esta cuenta") == true
            if (rejected) {
                authMessage = (
                    error.message?.takeIf { it.startsWith("Esta cuenta") }
                        ?: "No se pudo cargar tu perfil. Verifica la cuenta y sus permisos."
                    ) to true
                SupabaseBackend.client.auth.signOut()
            } else {
                connectionError = true
            }
        } finally {
            authLoading = false
            sessionChecked = true
        }
    }

    LaunchedEffect(SupabaseBackend.isConfigured) {
        if (!SupabaseBackend.isConfigured) return@LaunchedEffect

        SupabaseBackend.client.auth.sessionStatus.collect { status ->
            if (status !is SessionStatus.LoadingFromStorage && status !is SessionStatus.Authenticated) {
                sessionChecked = true
            }
            when (status) {
                is SessionStatus.LoadingFromStorage -> authLoading = true
                is SessionStatus.NotAuthenticated -> {
                    currentMember = null
                    churchSnapshot = null
                    modulesLoading = false
                    moduleError = null
                    authLoading = false
                    currentTab = AppTab.Home
                    morePage = MorePage.Menu
                }
                is SessionStatus.NetworkError -> {
                    authLoading = false
                    if (currentMember == null) {
                        authMessage = "No se pudo validar la sesión. Revisa tu conexión e inténtalo de nuevo." to true
                    }
                }
                is SessionStatus.Authenticated -> loadSessionMember()
            }
        }
    }

    val member = currentMember
    when {
        !SupabaseBackend.isConfigured -> SupabaseConfigurationScreen()
        !sessionChecked && member == null -> LoadingScreen()
        member == null && connectionError -> ConnectionErrorScreen(
            loading = authLoading,
            onRetry = { coroutineScope.launch { loadSessionMember() } },
            onSignOut = {
                coroutineScope.launch {
                    connectionError = false
                    runCatching { SupabaseBackend.client.auth.signOut() }
                }
            },
        )
        member == null -> LoginScreen(
            message = authMessage,
            loading = authLoading,
            onModeChange = {
                authMessage = null
                resetCodeSent = false
            },
            onLogin = { email, password ->
                coroutineScope.launch {
                    authLoading = true
                    authMessage = null
                    try {
                        SupabaseBackend.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        authMessage = "Correo o contraseña incorrectos, o no hay conexión. Inténtalo de nuevo." to true
                        authLoading = false
                    }
                }
            },
            onResetPassword = { email ->
                coroutineScope.launch {
                    authLoading = true
                    authMessage = null
                    try {
                        SupabaseBackend.client.auth.resetPasswordForEmail(email)
                        resetCodeSent = true
                        authMessage = "Si el correo está registrado, te llegará un código. Escríbelo abajo junto con tu nueva contraseña." to false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("ChurchApp", "No se pudo solicitar la recuperación", error)
                        authMessage = "No fue posible solicitar el restablecimiento. Revisa tu conexión e inténtalo de nuevo." to true
                    } finally {
                        authLoading = false
                    }
                }
            },
            resetCodeSent = resetCodeSent,
            onConfirmReset = { email, code, newPassword ->
                coroutineScope.launch {
                    authLoading = true
                    authMessage = null
                    try {
                        SupabaseBackend.completePasswordRecovery(email, code, newPassword)
                        resetCodeSent = false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("ChurchApp", "Recuperación rechazada", error)
                        authMessage = (
                            (error as? UserFacingException)?.message
                                ?: "El código no es válido o ya venció. Pide uno nuevo e inténtalo otra vez."
                            ) to true
                    } finally {
                        authLoading = false
                    }
                }
            },
        )
        consentedUserId != member.id && !hasCurrentConsent(context, member.id) -> ConsentScreen(
            onAccept = {
                saveConsent(context, member.id)
                consentedUserId = member.id
            },
            onDecline = {
                coroutineScope.launch { runCatching { SupabaseBackend.client.auth.signOut() } }
            },
        )
        else -> {
            val snapshot = churchSnapshot
            val activities = snapshot?.activities.orEmpty()
            val ministries = snapshot?.ministries.orEmpty()
            val members = snapshot?.members.orEmpty()
            val projects = snapshot?.projects.orEmpty()
            val notifications = snapshot?.notifications.orEmpty()
            val unread = notifications.count { !it.read }
            val permissions = Permissions(member, ministries.firstOrNull(Ministry::isWorship)?.id)

            /** Ejecuta una escritura, recarga los datos y solo informa éxito si persistió. */
            fun perform(successMessage: String, closeForm: Boolean = true, block: suspend () -> Unit) {
                if (submitting) return
                // El aviso se muestra aparte para no dejar los botones deshabilitados mientras está visible.
                fun notify(message: String) {
                    coroutineScope.launch { snackbar.showSnackbar(message) }
                }
                coroutineScope.launch {
                    submitting = true
                    formError = null
                    try {
                        block()
                        if (closeForm) openForm = null
                        reload(member)
                        notify(successMessage)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("ChurchApp", "Operación rechazada", error)
                        val detail = (error as? UserFacingException)?.message
                            ?: "No se pudo guardar. Verifica los datos, tu conexión y tus permisos."
                        if (error is PartialSaveException && closeForm) {
                            // Guardado parcial: se cierra el formulario y se recarga para mostrar lo persistido.
                            openForm = null
                            reload(member)
                            notify(detail)
                        } else if (openForm != null) {
                            formError = detail
                        } else {
                            notify(detail)
                        }
                    } finally {
                        submitting = false
                    }
                }
            }

            fun openFormRequest(request: FormRequest) {
                formError = null
                openForm = request
            }

            fun openFile(file: RecordFile) {
                coroutineScope.launch {
                    try {
                        uriHandler.openUri(SupabaseBackend.signedFileUrl(file))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("ChurchApp", "No se pudo abrir el archivo", error)
                        snackbar.showSnackbar("No se pudo abrir \"${file.name}\". Verifica tu conexión y tus permisos.")
                    }
                }
            }

            BackHandler(enabled = currentTab == AppTab.More && morePage != MorePage.Menu) {
                morePage = MorePage.Menu
            }
            BackHandler(enabled = currentTab != AppTab.Home && (currentTab != AppTab.More || morePage == MorePage.Menu)) {
                currentTab = AppTab.Home
            }

            Scaffold(
                containerColor = AppBackground,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    ChurchTopBar(
                        member = member,
                        unread = unread,
                        onNotifications = {
                            currentTab = AppTab.More
                            morePage = MorePage.Notifications
                        },
                        onLogout = { confirmLogout = true },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White, windowInsets = WindowInsets.navigationBars) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    if (tab == AppTab.More) morePage = MorePage.Menu
                                    currentTab = tab
                                },
                                icon = {
                                    if (tab == AppTab.More && unread > 0) {
                                        BadgedBox(badge = { Badge() }) { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
                                    } else {
                                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                                    }
                                },
                                // Cinco pestañas: la etiqueta usa un tamaño fijo que cabe completa en pantallas angostas.
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand,
                                    selectedTextColor = Brand,
                                    indicatorColor = BrandContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                PullToRefreshBox(
                    isRefreshing = modulesLoading && snapshot != null,
                    onRefresh = { coroutineScope.launch { reload(member) } },
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (modulesLoading && snapshot == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandLight, trackColor = BrandSurface)
                        }
                        moduleError?.let {
                            MessageBanner(
                                it,
                                BannerTone.Error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                actionLabel = if (modulesLoading) null else "Reintentar",
                                onAction = { coroutineScope.launch { reload(member) } },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            when (currentTab) {
                                AppTab.Home -> HomeScreen(
                                    member = member,
                                    activities = activities,
                                    ministries = ministries,
                                    notifications = notifications,
                                    onOpenItem = { detailItemId = it.id },
                                    onOpenAgenda = { personal ->
                                        calendarPersonal = personal
                                        currentTab = AppTab.Calendar
                                    },
                                    onOpenNotifications = {
                                        currentTab = AppTab.More
                                        morePage = MorePage.Notifications
                                    },
                                )
                                AppTab.Calendar -> CalendarScreen(
                                    member = member,
                                    permissions = permissions,
                                    activities = activities,
                                    ministries = ministries,
                                    personal = calendarPersonal,
                                    onPersonalChange = { calendarPersonal = it },
                                    onOpenItem = { detailItemId = it.id },
                                    onCreate = ::openFormRequest,
                                )
                                AppTab.Ministries -> MinistriesScreen(
                                    member = member,
                                    ministries = ministries,
                                    members = members,
                                    submitting = submitting,
                                    onCreateMinistry = { openFormRequest(FormRequest.NewMinistry) },
                                    onAddMember = { ministry -> openFormRequest(FormRequest.AddMember(ministry.id)) },
                                    onSetLeader = { ministryId, userId, leader ->
                                        perform(if (leader) "Marcado como líder" else "Liderazgo retirado", closeForm = false) {
                                            SupabaseBackend.setMembershipLeader(ministryId, userId, leader)
                                        }
                                    },
                                    onRemoveMember = { ministryId, userId ->
                                        perform("Integrante retirado", closeForm = false) {
                                            SupabaseBackend.removeMembership(ministryId, userId)
                                        }
                                    },
                                    inactiveMinistries = snapshot?.inactiveMinistries.orEmpty(),
                                    onEditMinistry = { openFormRequest(FormRequest.EditMinistry(it)) },
                                    onSetMinistryActive = { ministry, active ->
                                        perform(
                                            if (active) "${ministry.name} se reactivó" else "${ministry.name} se desactivó",
                                            closeForm = false,
                                        ) {
                                            SupabaseBackend.setMinistryActive(member, ministry.id, active)
                                        }
                                    },
                                )
                                AppTab.Projects -> ProjectsScreen(
                                    projects = projects,
                                    activities = activities,
                                    members = members,
                                    canManage = permissions.canManageProjects,
                                    onCreate = { openFormRequest(FormRequest.Project(null)) },
                                    onEdit = { openFormRequest(FormRequest.Project(it)) },
                                    onOpenActivity = { detailItemId = it.id },
                                )
                                AppTab.More -> when (morePage) {
                                    MorePage.Menu -> MoreScreen(
                                        member = member,
                                        ministries = ministries,
                                        unreadNotifications = unread,
                                        onWorship = { morePage = MorePage.Worship },
                                        onRecords = { morePage = MorePage.Records },
                                        onPersonnel = { morePage = MorePage.Personnel },
                                        onNotifications = { morePage = MorePage.Notifications },
                                        onChangePassword = { openFormRequest(FormRequest.ChangePassword) },
                                        onPrivacy = { morePage = MorePage.Privacy },
                                        onLogout = { confirmLogout = true },
                                    )
                                    MorePage.Privacy -> PrivacyScreen(
                                        member = member,
                                        onBack = { morePage = MorePage.Menu },
                                        onError = { message -> coroutineScope.launch { snackbar.showSnackbar(message) } },
                                    )
                                    MorePage.Worship -> WorshipScreen(
                                        activities = activities,
                                        members = members,
                                        songs = snapshot?.songs.orEmpty(),
                                        worshipSets = snapshot?.worshipSets.orEmpty(),
                                        canManage = permissions.canManageWorship,
                                        onBack = { morePage = MorePage.Menu },
                                        onCreateSong = { openFormRequest(FormRequest.Song) },
                                        onEditRepertoire = { openFormRequest(FormRequest.Repertoire) },
                                        submitting = submitting,
                                        onOpenFile = ::openFile,
                                        onAddSongFiles = { song, files ->
                                            perform(if (files.size == 1) "Archivo adjuntado" else "${files.size} archivos adjuntados", closeForm = false) {
                                                files.forEach { SupabaseBackend.uploadSongFile(member, song.id, it) }
                                            }
                                        },
                                        onDeleteSongFile = { file ->
                                            perform("Archivo eliminado", closeForm = false) { SupabaseBackend.deleteSongFile(file) }
                                        },
                                        onError = { message -> coroutineScope.launch { snackbar.showSnackbar(message) } },
                                        onEditSong = { song -> openFormRequest(FormRequest.EditSong(song.id)) },
                                        onDeleteSong = { song ->
                                            perform("\"${song.title}\" se eliminó del catálogo", closeForm = false) {
                                                SupabaseBackend.deleteWorshipSong(song, snapshot?.worshipSets.orEmpty())
                                            }
                                        },
                                    )
                                    MorePage.Records -> RecordsScreen(
                                        member = member,
                                        records = snapshot?.meetingRecords.orEmpty(),
                                        available = snapshot?.recordsAvailable ?: true,
                                        ministries = ministries,
                                        members = members,
                                        submitting = submitting,
                                        onBack = { morePage = MorePage.Menu },
                                        onCreate = { openFormRequest(FormRequest.Record) },
                                        onOpenFile = ::openFile,
                                        onAddFiles = { record, files ->
                                            perform(if (files.size == 1) "Archivo adjuntado" else "${files.size} archivos adjuntados", closeForm = false) {
                                                files.forEach { SupabaseBackend.uploadRecordFile(member, record.id, it) }
                                            }
                                        },
                                        onDeleteFile = { file ->
                                            perform("Archivo eliminado", closeForm = false) { SupabaseBackend.deleteRecordFile(file) }
                                        },
                                        onDeleteRecord = { record ->
                                            perform("Registro eliminado", closeForm = false) { SupabaseBackend.deleteMeetingRecord(record) }
                                        },
                                        onError = { message -> coroutineScope.launch { snackbar.showSnackbar(message) } },
                                    )
                                    MorePage.Personnel -> PersonnelScreen(
                                        currentMember = member,
                                        members = members,
                                        ministries = ministries,
                                        onBack = { morePage = MorePage.Menu },
                                        onEdit = { openFormRequest(FormRequest.Profile(it)) },
                                        onCreate = { openFormRequest(FormRequest.NewUser) },
                                    )
                                    MorePage.Notifications -> NotificationsScreen(
                                        notifications = notifications,
                                        submitting = submitting,
                                        onBack = { morePage = MorePage.Menu },
                                        onMarkRead = { id ->
                                            perform("Aviso marcado como leído", closeForm = false) {
                                                SupabaseBackend.markNotificationRead(id)
                                            }
                                        },
                                        onMarkAllRead = {
                                            perform("Todos los avisos están leídos", closeForm = false) {
                                                SupabaseBackend.markAllNotificationsRead(member)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            detailItemId?.let { id ->
                val item = activities.firstOrNull { it.id == id }
                if (item == null) {
                    LaunchedEffect(id) { detailItemId = null }
                } else {
                    ActivityDetailSheet(
                        item = item,
                        member = member,
                        permissions = permissions,
                        activities = activities,
                        ministries = ministries,
                        members = members,
                        projects = projects,
                        worshipSets = snapshot?.worshipSets.orEmpty(),
                        songs = snapshot?.songs.orEmpty(),
                        meetingRecords = snapshot?.meetingRecords.orEmpty(),
                        submitting = submitting,
                        onDismiss = { detailItemId = null },
                        onOpenRecords = {
                            detailItemId = null
                            currentTab = AppTab.More
                            morePage = MorePage.Records
                        },
                        onOpenItem = { detailItemId = it.id },
                        onAddTask = {
                            detailItemId = null
                            openFormRequest(FormRequest.Task(item.id))
                        },
                        onEditRepertoire = {
                            detailItemId = null
                            openFormRequest(FormRequest.Repertoire)
                        },
                        onEditAssignees = {
                            detailItemId = null
                            openFormRequest(FormRequest.EditAssignees(item.id))
                        },
                        onOpenFile = ::openFile,
                        onChangeStatus = { status ->
                            perform("Estado actualizado", closeForm = false) {
                                when {
                                    item.isLegacyTask -> SupabaseBackend.updateLegacyTaskStatus(item.id, status)
                                    item.isTask -> SupabaseBackend.updateTaskStatus(item.id, status)
                                    else -> SupabaseBackend.updateActivityStatus(item.id, status)
                                }
                            }
                        },
                    )
                }
            }

            when (val request = openForm) {
                null -> Unit
                is FormRequest.Project -> ProjectForm(
                    existing = request.existing,
                    members = members,
                    currentMember = member,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        if (draft.id == null) {
                            perform("Proyecto creado") { SupabaseBackend.createProject(member, draft) }
                        } else {
                            perform("Proyecto actualizado") { SupabaseBackend.updateProject(member, draft) }
                        }
                    },
                )
                FormRequest.Activity -> ActivityForm(
                    ministries = ministries,
                    members = members,
                    projects = projects,
                    currentMember = member,
                    permissions = permissions,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Actividad creada") { SupabaseBackend.createActivity(member, draft) }
                    },
                )
                FormRequest.Meeting -> MeetingForm(
                    ministries = ministries,
                    members = members,
                    currentMember = member,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Reunión convocada") { SupabaseBackend.createMeeting(member, draft) }
                    },
                )
                is FormRequest.Task -> TaskForm(
                    initialActivityId = request.activityId,
                    activities = activities,
                    members = members,
                    currentMember = member,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Tarea asignada") { SupabaseBackend.createTask(member, draft) }
                    },
                )
                FormRequest.Song -> SongForm(
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft, files ->
                        permissions.worshipMinistryId?.let { ministryId ->
                            perform("Canción agregada") { SupabaseBackend.createWorshipSong(member, ministryId, draft, files) }
                        }
                    },
                )
                is FormRequest.EditSong -> {
                    val song = snapshot?.songs.orEmpty().firstOrNull { it.id == request.songId }
                    if (song == null) {
                        LaunchedEffect(request) { openForm = null }
                    } else {
                        SongForm(
                            existing = song,
                            submitting = submitting,
                            error = formError,
                            onDismiss = { openForm = null },
                            onSubmit = { draft, files ->
                                perform("Canción actualizada") {
                                    SupabaseBackend.updateWorshipSong(song.id, draft)
                                    val failed = files.filter { file ->
                                        runCatching { SupabaseBackend.uploadSongFile(member, song.id, file) }
                                            .onFailure { if (it is CancellationException) throw it }
                                            .isFailure
                                    }
                                    if (failed.isNotEmpty()) {
                                        throw PartialSaveException(
                                            "Los cambios se guardaron, pero no se pudieron adjuntar: ${failed.joinToString { it.name }}.",
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                FormRequest.NewMinistry -> MinistryForm(
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Ministerio creado") { SupabaseBackend.createMinistry(member, draft) }
                    },
                )
                is FormRequest.EditMinistry -> MinistryForm(
                    existing = request.ministry,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Ministerio actualizado") { SupabaseBackend.updateMinistry(member, request.ministry.id, draft) }
                    },
                )
                is FormRequest.Profile -> ProfileForm(
                    target = request.member,
                    ministries = ministries,
                    isSelf = request.member.id == member.id,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Cuenta actualizada") {
                            SupabaseBackend.updateProfileAsPastor(request.member, draft.fullName, draft.phone, draft.role, draft.active)
                            SupabaseBackend.syncMemberships(request.member, ministries, draft.memberships)
                            if (request.member.id == member.id) {
                                if (!draft.active) {
                                    SupabaseBackend.client.auth.signOut()
                                } else {
                                    currentMember = SupabaseBackend.loadCurrentMember()
                                }
                            }
                        }
                    },
                    onSendReset = {
                        perform("Correo de restablecimiento enviado a ${request.member.email}", closeForm = false) {
                            SupabaseBackend.sendPasswordReset(request.member.email)
                        }
                    },
                    onSetPassword = { password ->
                        perform("Contraseña temporal asignada", closeForm = false) {
                            SupabaseBackend.setTemporaryPassword(request.member.id, password)
                        }
                    },
                )
                FormRequest.NewUser -> NewUserForm(
                    ministries = ministries,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft ->
                        perform("Cuenta creada para ${draft.email.trim()}") {
                            SupabaseBackend.createUserAccount(draft)
                            createdAccount = draft.email.trim() to draft.password
                        }
                    },
                )
                FormRequest.ChangePassword -> ChangePasswordForm(
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { password ->
                        perform("Tu contraseña se actualizó") { SupabaseBackend.changeOwnPassword(password) }
                    },
                )
                is FormRequest.AddMember -> {
                    val ministry = ministries.firstOrNull { it.id == request.ministryId }
                    if (ministry == null) {
                        LaunchedEffect(request) { openForm = null }
                    } else {
                        AddMemberForm(
                            ministry = ministry,
                            candidates = members.filter { it.active && ministry.id !in it.ministryIds },
                            submitting = submitting,
                            error = formError,
                            onDismiss = { openForm = null },
                            onSubmit = { userId, leader ->
                                perform("Integrante agregado") { SupabaseBackend.addMembership(ministry.id, userId, leader) }
                            },
                        )
                    }
                }
                is FormRequest.EditAssignees -> {
                    val item = activities.firstOrNull { it.id == request.itemId }
                    if (item == null) {
                        LaunchedEffect(request) { openForm = null }
                    } else {
                        // Elegibles según RLS: el Pastor asigna a cualquiera; el líder, a integrantes del ministerio.
                        val eligible = members.filter {
                            it.active && (member.isPastor || (item.ministryId != null && item.ministryId in it.ministryIds))
                        }
                        if (item.isTask) {
                            ReassignTaskForm(
                                task = item,
                                eligible = eligible,
                                submitting = submitting,
                                error = formError,
                                onDismiss = { openForm = null },
                                onSubmit = { assigneeId ->
                                    perform("Tarea reasignada") { SupabaseBackend.reassignTask(item.id, assigneeId) }
                                },
                            )
                        } else {
                            AssigneesForm(
                                activity = item,
                                eligible = eligible,
                                submitting = submitting,
                                error = formError,
                                onDismiss = { openForm = null },
                                onSubmit = { selected ->
                                    perform("Responsables actualizados") { SupabaseBackend.setActivityAssignees(member, item, selected) }
                                },
                            )
                        }
                    }
                }
                FormRequest.Record -> RecordForm(
                    meetings = activities,
                    ministries = ministries,
                    members = members,
                    currentMember = member,
                    submitting = submitting,
                    error = formError,
                    onDismiss = { openForm = null },
                    onSubmit = { draft, files ->
                        perform("Registro guardado") { SupabaseBackend.createMeetingRecord(member, draft, files) }
                    },
                )
                FormRequest.Repertoire -> {
                    val ministryId = permissions.worshipMinistryId
                    if (ministryId == null) {
                        LaunchedEffect(request) { openForm = null }
                    } else {
                        RepertoireForm(
                            songs = snapshot?.songs.orEmpty(),
                            members = members,
                            existing = snapshot?.worshipSets.orEmpty().ministryRepertoire(),
                            submitting = submitting,
                            error = formError,
                            onDismiss = { openForm = null },
                            onSubmit = { setSongs ->
                                perform("Repertorio guardado y notificado") {
                                    SupabaseBackend.saveMinistryRepertoire(ministryId, setSongs)
                                }
                            },
                        )
                    }
                }
            }

            createdAccount?.let { (email, password) ->
                CreatedAccountDialog(email, password) { createdAccount = null }
            }

            if (confirmLogout) {
                ConfirmDialog(
                    title = "¿Cerrar sesión?",
                    message = "Tendrás que volver a ingresar tu correo y contraseña.",
                    confirmLabel = "Cerrar sesión",
                    onDismiss = { confirmLogout = false },
                    onConfirm = {
                        confirmLogout = false
                        coroutineScope.launch {
                            try {
                                SupabaseBackend.client.auth.signOut()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                snackbar.showSnackbar("No se pudo cerrar la sesión. Revisa tu conexión.")
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChurchTopBar(member: Member, unread: Int, onNotifications: () -> Unit, onLogout: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Símbolo decorativo: el lector de pantalla lee solo el nombre de la iglesia.
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.14f))
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) { Text("✝", color = Gold, fontSize = 20.sp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Centro Mundial", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("Adoración y Alabanza", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        },
        actions = {
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) } }) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = if (unread > 0) "Avisos, $unread sin leer" else "Avisos",
                        tint = Color.White,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.semantics { contentDescription = "Menú de cuenta de ${member.name}" },
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Gold).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(member.initials, color = Brand, style = MaterialTheme.typography.labelLarge)
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 200.dp)) {
                        Text(member.name, style = MaterialTheme.typography.titleSmall)
                        Text(member.role, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (member.email.isNotBlank()) Text(member.email, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Cerrar sesión") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onLogout()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand),
    )
}

@Composable
private fun LoginScreen(
    message: Pair<String, Boolean>?,
    loading: Boolean,
    onModeChange: () -> Unit,
    onLogin: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    resetCodeSent: Boolean = false,
    onConfirmReset: (email: String, code: String, newPassword: String) -> Unit = { _, _, _ -> },
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var resetMode by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf<LegalDocument?>(null) }
    reading?.let { document ->
        LegalDocumentScreen(document, onBack = { reading = null })
        return
    }
    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    // Paso 2 de la recuperación: código del correo y contraseña nueva.
    val confirmingReset = resetMode && resetCodeSent
    val passwordIssue = if (confirmingReset && password.isNotEmpty()) passwordProblem(password) else null
    val canSubmit = !loading && emailValid && when {
        confirmingReset -> code.length >= 6 && password.isNotEmpty() && passwordIssue == null
        resetMode -> true
        else -> password.isNotBlank()
    }
    val submit = {
        when {
            confirmingReset -> onConfirmReset(email.trim(), code, password)
            resetMode -> onResetPassword(email.trim())
            else -> onLogin(email.trim(), password)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Brand, 0.42f to Brand, 0.42f to AppBackground)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.14f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { Text("✝", color = Gold, fontSize = 32.sp) }
            Spacer(Modifier.height(16.dp))
            Text("Centro Mundial de Adoración y Alabanza", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
            Text("Gestión Iglesia", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White))
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (resetMode) "Recuperar contraseña" else "Inicia sesión",
                        style = MaterialTheme.typography.titleLarge,
                        color = Brand,
                    )
                    Text(
                        if (confirmingReset) {
                            "Revisa tu correo. Escribe el código que te enviamos y elige una contraseña nueva."
                        } else if (resetMode) {
                            "Te enviaremos un código al correo de tu cuenta."
                        } else {
                            "Usa el correo y la contraseña que te entregó la administración."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Correo electrónico") },
                        singleLine = true,
                        isError = email.isNotBlank() && !emailValid,
                        supportingText = if (email.isNotBlank() && !emailValid) {
                            { Text("Escribe un correo válido, por ejemplo nombre@iglesia.cl") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (resetMode) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !loading && !confirmingReset,
                    )
                    if (confirmingReset) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { value -> code = value.filter(Char::isDigit).take(10) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Código del correo") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !loading,
                        )
                    }
                    if (!resetMode || confirmingReset) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (confirmingReset) "Nueva contraseña" else "Contraseña") },
                            isError = passwordIssue != null,
                            supportingText = if (confirmingReset) {
                                { Text(passwordIssue ?: "Mínimo 8 caracteres, con al menos una letra y un número.") }
                            } else {
                                null
                            },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                            shape = MaterialTheme.shapes.medium,
                            enabled = !loading,
                        )
                    }
                    message?.let { (text, isError) ->
                        MessageBanner(text, if (isError) BannerTone.Error else BannerTone.Success)
                    }
                    Button(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        enabled = canSubmit,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Conectando…")
                        } else {
                            Text(
                                when {
                                    confirmingReset -> "Cambiar contraseña"
                                    resetMode -> "Enviar código"
                                    else -> "Ingresar"
                                },
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            resetMode = !resetMode
                            onModeChange()
                        },
                        enabled = !loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(if (resetMode) "Volver al inicio de sesión" else "¿Olvidaste tu contraseña?")
                    }
                    if (confirmingReset) {
                        TextButton(
                            onClick = { onResetPassword(email.trim()) },
                            enabled = !loading,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("No me llegó el código: enviar otro") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Las cuentas las crea el Pastor/Administrador.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            LegalLinks(onOpen = { reading = it })
        }
    }
}

@Composable
private fun SupabaseConfigurationScreen() {
    Column(
        Modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Falta configurar Supabase", color = Brand, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Copia supabase.properties.example como supabase.properties en la raíz del proyecto y agrega la URL y la clave pública anon/publishable. Luego sincroniza Gradle y vuelve a ejecutar la aplicación.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        MessageBanner("No agregues la clave service_role a la aplicación.", BannerTone.Error)
    }
}

@Composable
private fun LoadingScreen() {
    // Fondo institucional: la barra de estado usa íconos claros.
    Column(
        Modifier.fillMaxSize().background(Brand),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✝", color = Gold, fontSize = 40.sp)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))
        Text("Cargando tu cuenta…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Sin conexión al abrir la app: la sesión se conserva y se puede reintentar. */
@Composable
private fun ConnectionErrorScreen(loading: Boolean, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brand).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✝", color = Gold, fontSize = 40.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "No hay conexión",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No pudimos comunicarnos con el servidor. Revisa tu internet y vuelve a intentarlo. Tu sesión sigue abierta.",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(
            onClick = onRetry,
            enabled = !loading,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(color = Brand, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text("Reintentar")
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onSignOut, enabled = !loading) {
            Text("Cerrar sesión", color = Color.White)
        }
    }
}

internal val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
