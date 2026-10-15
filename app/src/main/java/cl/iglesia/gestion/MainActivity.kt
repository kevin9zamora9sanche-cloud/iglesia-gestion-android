package cl.iglesia.gestion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Inicio", Icons.Outlined.Home),
    Calendar("Agenda", Icons.Outlined.CalendarMonth),
    Ministries("Ministerios", Icons.Outlined.Groups),
    Projects("Proyectos", Icons.Outlined.FolderOpen),
    More("Más", Icons.Outlined.MoreHoriz),
}

private enum class MorePage { Menu, Worship, Personnel, Notifications }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(30, 58, 138)),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.rgb(250, 248, 245),
                android.graphics.Color.rgb(250, 248, 245),
            ),
        )
        setContent {
            ChurchTheme {
                ChurchApp()
            }
        }
    }
}

@Composable
private fun ChurchApp() {
    var currentMember by remember { mutableStateOf<Member?>(null) }
    var churchSnapshot by remember { mutableStateOf<ChurchSnapshot?>(null) }
    var authLoading by remember { mutableStateOf(SupabaseBackend.isConfigured) }
    var authError by remember { mutableStateOf<String?>(null) }
    var modulesLoading by remember { mutableStateOf(false) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var morePage by remember { mutableStateOf(MorePage.Menu) }
    var showProjectForm by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ChurchProject?>(null) }
    var showActivityForm by remember { mutableStateOf(false) }
    var showTaskForm by remember { mutableStateOf(false) }
    var showSongForm by remember { mutableStateOf(false) }
    var writeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(SupabaseBackend.isConfigured) {
        if (!SupabaseBackend.isConfigured) return@LaunchedEffect

        SupabaseBackend.client.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.LoadingFromStorage -> authLoading = true
                is SessionStatus.NotAuthenticated -> {
                    currentMember = null
                    churchSnapshot = null
                    modulesLoading = false
                    moduleError = null
                    authLoading = false
                }
                is SessionStatus.NetworkError -> {
                    authLoading = false
                    if (currentMember == null) {
                        authError = "No se pudo validar la sesión. Revisa tu conexión e inténtalo de nuevo."
                    }
                }
                is SessionStatus.Authenticated -> {
                    authLoading = true
                    try {
                        val member = SupabaseBackend.loadCurrentMember()
                        currentMember = member
                        authError = null
                        modulesLoading = true
                        try {
                            churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                            moduleError = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            churchSnapshot = null
                            moduleError = "No se pudieron cargar los módulos desde Supabase. Revisa las migraciones y los permisos."
                        } finally {
                            modulesLoading = false
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        currentMember = null
                        authError = "No se pudo cargar el perfil. Verifica la migración de Supabase, la cuenta y sus permisos."
                        SupabaseBackend.client.auth.signOut()
                    } finally {
                        authLoading = false
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceWarm,
    ) {
        val member = currentMember
        if (!SupabaseBackend.isConfigured) {
            SupabaseConfigurationScreen()
        } else if (authLoading && member == null) {
            LoadingScreen()
        } else if (member == null) {
            SupabaseLoginScreen(
                error = authError,
                onLogin = { email, password ->
                    coroutineScope.launch {
                        authLoading = true
                        authError = null
                        try {
                            SupabaseBackend.client.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            authError = "No se pudo iniciar sesión. Revisa tus datos y conexión e inténtalo de nuevo."
                            authLoading = false
                        }
                    }
                },
                onResetPassword = { email ->
                    coroutineScope.launch {
                        authLoading = true
                        authError = null
                        try {
                            SupabaseBackend.client.auth.resetPasswordForEmail(email)
                            authError = "Si el correo está registrado, recibirás instrucciones para restablecer la contraseña."
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            authError = "No fue posible solicitar el restablecimiento. Revisa tu conexión e inténtalo de nuevo."
                        } finally {
                            authLoading = false
                        }
                    }
                },
                loading = authLoading,
            )
        } else {
            Scaffold(
                containerColor = SurfaceWarm,
                topBar = {
                    ChurchTopBar(
                        member = member,
                        onLogout = {
                            coroutineScope.launch {
                                authLoading = true
                                try {
                                    SupabaseBackend.client.auth.signOut()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    authError = "No se pudo cerrar la sesión. Revisa tu conexión e inténtalo de nuevo."
                                    authLoading = false
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    currentTab = tab
                                    if (tab == AppTab.More) morePage = MorePage.Menu
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand,
                                    selectedTextColor = Brand,
                                    indicatorColor = 0xFFEFF6FF.toColor(),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                ),
                            )
                        }
                    }
                },
            ) { scaffoldPadding ->
                val snapshot = churchSnapshot
                val moduleActivities = snapshot?.activities.orEmpty()
                val moduleMinistries = snapshot?.ministries.orEmpty()
                val moduleMembers = snapshot?.members.orEmpty()
                val moduleProjects = snapshot?.projects.orEmpty()
                val canManage = member.role in setOf("Pastor/Administrador", "Líder de ministerio")
                val canCreateProjects = member.role == "Pastor/Administrador"
                val worshipMinistryId = moduleMinistries.firstOrNull {
                    it.name.contains("alabanza", ignoreCase = true)
                }?.id
                val canManageWorship = member.role == "Pastor/Administrador" ||
                    (member.role == "Líder de ministerio" && member.ministryId == worshipMinistryId)
                Column(Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = 0xFFFBF4E0.toColor()),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when {
                                    modulesLoading -> "Cargando datos de Supabase…"
                                    moduleError != null -> moduleError.orEmpty()
                                    writeError != null -> writeError.orEmpty()
                                    else -> "Sesión autenticada. Datos sincronizados con Supabase."
                                },
                                modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                                color = if (moduleError == null && writeError == null) 0xFF9A6E1A.toColor() else 0xFFB42318.toColor(),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                            if (moduleError != null && !modulesLoading) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            modulesLoading = true
                                            moduleError = null
                                            try {
                                                churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (_: Exception) {
                                                moduleError = "No se pudieron cargar los módulos desde Supabase. Revisa las migraciones y los permisos."
                                            } finally {
                                                modulesLoading = false
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text("Reintentar", color = Brand, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    when (currentTab) {
                        AppTab.Home -> DashboardScreen(
                            member,
                            scaffoldPadding,
                            moduleActivities,
                            moduleMinistries,
                            moduleMembers,
                            moduleProjects,
                        )
                        AppTab.Calendar -> CalendarScreen(
                            scaffoldPadding,
                            moduleActivities,
                            moduleMinistries,
                            moduleMembers,
                            moduleProjects,
                            canManage,
                            onCreateActivity = { showActivityForm = true },
                            onCreateTask = { showTaskForm = true },
                            onUpdateTask = { activity, status ->
                                coroutineScope.launch {
                                    writeError = null
                                    try {
                                        if (activity.isLegacyTask) {
                                            SupabaseBackend.updateLegacyTaskStatus(activity.id, status)
                                        } else {
                                            SupabaseBackend.updateTaskStatus(activity.id, status)
                                        }
                                        churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (_: Exception) {
                                        writeError = "No se pudo actualizar la tarea. Verifica que estés asignado y tengas permisos."
                                    }
                                }
                            },
                        )
                        AppTab.Ministries -> MinistriesScreen(
                            scaffoldPadding,
                            moduleMinistries,
                            moduleMembers,
                        )
                        AppTab.Projects -> ProjectsScreen(
                            scaffoldPadding,
                            moduleProjects,
                            moduleMembers,
                            canCreateProjects,
                            onCreateProject = { showProjectForm = true },
                            onEditProject = { editingProject = it },
                        )
                        AppTab.More -> when (morePage) {
                            MorePage.Menu -> MoreScreen(
                                padding = scaffoldPadding,
                                onWorship = { morePage = MorePage.Worship },
                                onPersonnel = { morePage = MorePage.Personnel },
                                onNotifications = { morePage = MorePage.Notifications },
                                unreadNotifications = snapshot?.notifications.orEmpty().count { !it.read },
                            )
                            MorePage.Worship -> WorshipScreen(
                                padding = scaffoldPadding,
                                onBack = { morePage = MorePage.Menu },
                                activities = moduleActivities,
                                songs = snapshot?.songs.orEmpty(),
                                canManage = canManageWorship && worshipMinistryId != null,
                                onCreateSong = { showSongForm = true },
                            )
                            MorePage.Personnel -> PersonnelScreen(
                                padding = scaffoldPadding,
                                onBack = { morePage = MorePage.Menu },
                                currentMember = member,
                                members = moduleMembers,
                                ministries = moduleMinistries,
                                onUpdateMember = { target, name, role, active ->
                                    coroutineScope.launch {
                                        writeError = null
                                        try {
                                            SupabaseBackend.updateProfileAsPastor(target, name, role, active)
                                            if (target.id == member.id && !active) {
                                                SupabaseBackend.client.auth.signOut()
                                            } else {
                                                val updatedMember = SupabaseBackend.loadCurrentMember()
                                                currentMember = updatedMember
                                                churchSnapshot = SupabaseBackend.loadChurchSnapshot(updatedMember)
                                            }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            writeError = "No se pudo actualizar el perfil. Verifica que tengas permisos de Pastor."
                                        }
                                    }
                                },
                            )
                            MorePage.Notifications -> NotificationsScreen(
                                padding = scaffoldPadding,
                                notifications = snapshot?.notifications.orEmpty(),
                                onBack = { morePage = MorePage.Menu },
                                onMarkRead = { notificationId ->
                                    coroutineScope.launch {
                                        writeError = null
                                        try {
                                            SupabaseBackend.markNotificationRead(notificationId)
                                            churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            writeError = "No se pudo marcar el aviso como leído."
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                if (showProjectForm) {
                    ProjectFormDialog(
                        members = moduleMembers,
                        currentMember = member,
                        error = writeError,
                        onDismiss = { showProjectForm = false },
                        onSubmit = { draft ->
                            coroutineScope.launch {
                                writeError = null
                                try {
                                    SupabaseBackend.createProject(member, draft)
                                    churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    showProjectForm = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    writeError = "No se pudo crear el proyecto: ${error.message ?: "verifica los datos y tus permisos."}"
                                }
                            }
                        },
                    )
                }
                editingProject?.let { projectToEdit ->
                    ProjectEditDialog(
                        project = projectToEdit,
                        error = writeError,
                        onDismiss = { editingProject = null },
                        onSubmit = { draft ->
                            coroutineScope.launch {
                                writeError = null
                                try {
                                    SupabaseBackend.updateProjectAsPastor(member, draft)
                                    churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    editingProject = null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    writeError = "No se pudo actualizar el proyecto: ${error.message ?: "verifica los datos y tus permisos."}"
                                }
                            }
                        },
                    )
                }
                if (showActivityForm) {
                    ActivityFormDialog(
                        ministries = moduleMinistries,
                        members = moduleMembers,
                        projects = moduleProjects,
                        currentMember = member,
                        error = writeError,
                        onDismiss = { showActivityForm = false },
                        onSubmit = { draft ->
                            coroutineScope.launch {
                                writeError = null
                                try {
                                    SupabaseBackend.createActivity(member, draft)
                                    churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    showActivityForm = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    writeError = "No se pudo guardar: ${error.message ?: "verifica los datos y tus permisos."}"
                                }
                            }
                        },
                    )
                }
                if (showTaskForm) {
                    TaskFormDialog(
                        activities = moduleActivities,
                        members = moduleMembers,
                        currentMember = member,
                        error = writeError,
                        onDismiss = { showTaskForm = false },
                        onSubmit = { draft ->
                            coroutineScope.launch {
                                writeError = null
                                try {
                                    SupabaseBackend.createTask(member, draft)
                                    churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    showTaskForm = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    writeError = "No se pudo crear la tarea. Verifica la actividad, el responsable y tus permisos."
                                }
                            }
                        },
                    )
                }
                if (showSongForm && worshipMinistryId != null) {
                    SongFormDialog(
                        error = writeError,
                        onDismiss = { showSongForm = false },
                        onSubmit = { draft ->
                            coroutineScope.launch {
                                writeError = null
                                try {
                                    SupabaseBackend.createWorshipSong(member, worshipMinistryId, draft)
                                    churchSnapshot = SupabaseBackend.loadChurchSnapshot(member)
                                    showSongForm = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    writeError = "No se pudo agregar la canción. Verifica tu permiso para administrar Alabanza."
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChurchTopBar(member: Member, onLogout: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✝", color = Gold, fontSize = 21.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Centro Mundial",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text("Adoración y Alabanza", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp)
                }
            }
        },
        actions = {
            Text(
                member.initials,
                color = Brand,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Gold)
                    .padding(horizontal = 9.dp, vertical = 8.dp),
            )
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Cerrar sesión", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand),
    )
}

@Composable
private fun SupabaseLoginScreen(
    error: String?,
    loading: Boolean,
    onLogin: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetMode by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWarm)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand),
            contentAlignment = Alignment.Center,
        ) { Text("✝", color = Gold, fontSize = 30.sp) }
        Spacer(Modifier.height(28.dp))
        Text("CENTRO MUNDIAL DE ADORACIÓN Y ALABANZA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text("Inicia sesión", color = Brand, fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Accede con el correo y la contraseña de tu cuenta.",
            color = TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Bienvenido", color = Brand, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !loading,
                )
                if (!resetMode) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !loading,
                    )
                }
                TextButton(
                    onClick = { resetMode = !resetMode },
                    enabled = !loading,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (resetMode) "Volver al inicio de sesión" else "¿Olvidaste tu contraseña?",
                        color = Brand,
                        fontSize = 13.sp,
                    )
                }
                if (error != null) {
                    Text(
                        error,
                        color = if (error.startsWith("Si el correo")) Green else 0xFFB42318.toColor(),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (resetMode) onResetPassword(email) else onLogin(email, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand),
                    enabled = !loading && email.isNotBlank() && (resetMode || password.isNotBlank()),
                ) {
                    Text(
                        when {
                            loading -> "Conectando…"
                            resetMode -> "Enviar instrucciones"
                            else -> "Ingresar al sistema"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupabaseConfigurationScreen() {
    Column(
        Modifier.fillMaxSize().background(SurfaceWarm).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Falta configurar Supabase", color = Brand, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Copia android/supabase.properties.example como android/supabase.properties y agrega la URL del proyecto y su clave pública anon/publishable. Después sincroniza Gradle y vuelve a ejecutar la aplicación.",
            color = TextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "No agregues la clave service_role a la aplicación.",
            color = 0xFFB42318.toColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(SurfaceWarm),
        contentAlignment = Alignment.Center,
    ) {
        Text("Conectando con Supabase…", color = Brand, fontSize = 15.sp)
    }
}

@Composable
private fun DashboardScreen(
    member: Member,
    padding: PaddingValues,
    activities: List<ChurchActivity>,
    ministries: List<Ministry>,
    members: List<Member>,
    projects: List<ChurchProject>,
) {
    val today = LocalDate.now()
    val upcoming = remember(activities) {
        activities.filter { !it.date.isBefore(today) }.sortedBy { it.date }.take(4)
    }
    val myMinistry = ministries.find { it.id == member.ministryId }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CL"))).replaceFirstChar { it.titlecase(Locale("es", "CL")) }, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Hola, ${member.name.substringBefore(" ")}", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${member.role} · ${myMinistry?.name ?: "Vista general"}", color = TextMuted, fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    "Actividades",
                    activities.count { it.kind != "Tarea" && it.status == "Programada" }.toString(),
                    "▣",
                    BrandLight,
                    Modifier.weight(1f),
                )
                StatCard(
                    "Completadas",
                    activities.count { it.kind != "Tarea" && it.status == "Completada" }.toString(),
                    "✓",
                    Green,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Ministerios", ministries.size.toString(), "✝", Navy, Modifier.weight(1f))
                StatCard("Tareas", activities.count { it.kind == "Tarea" }.toString(), "◷", 0xFF9A6E1A.toColor(), Modifier.weight(1f))
            }
        }
        item { SectionHeading("Próximas actividades", "Ver agenda") }
        if (upcoming.isEmpty()) {
            item { EmptyCard("Sin actividades próximas", "Cuando se programen actividades, aparecerán aquí.") }
        } else {
            items(upcoming, key = { it.id }) { activity ->
                ActivityCard(activity, ministries, members, projects)
            }
        }
        item {
            SectionHeading("Ministerios", null)
            Spacer(Modifier.height(10.dp))
            ministries.forEach { ministry ->
                MinistryCompactCard(ministry, members)
                Spacer(Modifier.height(9.dp))
            }
            if (ministries.isEmpty()) {
                EmptyCard("Sin ministerios registrados", "Los ministerios aparecerán aquí cuando se creen en Supabase.")
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, symbol: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, color = color, fontSize = 21.sp)
                Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            Text(label, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Brand, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) Text(action, color = BrandLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActivityCard(
    activity: ChurchActivity,
    ministries: List<Ministry>,
    members: List<Member> = emptyList(),
    projects: List<ChurchProject> = emptyList(),
) {
    val ministry = ministries.find { it.id == activity.ministryId }
    val project = projects.find { it.id == activity.projectId }
    val preacher = members.find { it.id == activity.preacherId }
    val worshipLeader = members.find { it.id == activity.worshipLeaderId }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background((ministry?.color?.toColor() ?: MutedWarm).copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) { Text(ministry?.icon ?: "▣", color = ministry?.color?.toColor() ?: Brand, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(activity.title, color = Color(0xFF1C1917), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (activity.category.isNotBlank() && activity.kind != "Tarea") {
                            Text(
                                activity.category,
                                color = BrandLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(0xFFEFF6FF.toColor())
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${activity.date.toCalendarLabel()} · ${activity.time} · ${activity.location}", color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (project != null) {
                Spacer(Modifier.height(6.dp))
                Text("Proyecto: ${project.name}", color = Brand, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            if (preacher != null || worshipLeader != null) {
                Spacer(Modifier.height(4.dp))
                if (preacher != null) {
                    Text("Predicador: ${preacher.name}", color = TextMuted, fontSize = 11.sp)
                }
                if (worshipLeader != null) {
                    Text("Director de alabanza: ${worshipLeader.name}", color = TextMuted, fontSize = 11.sp)
                }
            }
            if (activity.kind == "Tarea") {
                Spacer(Modifier.height(8.dp))
                activity.parentActivityTitle?.let {
                    Text("Actividad: $it", color = Brand, fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    "Estado: ${activity.taskStatus?.toTaskStatusLabel() ?: "Pendiente"}",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
                activity.priority?.let {
                    Text("Prioridad: ${it.toPriorityLabel()}", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(
    padding: PaddingValues,
    activities: List<ChurchActivity>,
    ministries: List<Ministry>,
    members: List<Member>,
    projects: List<ChurchProject>,
    canManage: Boolean,
    onCreateActivity: () -> Unit,
    onCreateTask: () -> Unit,
    onUpdateTask: (ChurchActivity, String) -> Unit,
) {
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }
    val week = (0..13).map { today.plusDays(it.toLong()) }
    val dayActivities = activities.filter { it.date == selectedDate }.sortedBy { it.time }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("TU SEMANA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Calendario", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Actividades y responsabilidades", color = TextMuted, fontSize = 13.sp)
                if (canManage) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreateActivity, colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                            Text("Crear actividad")
                        }
                        OutlinedButton(onClick = onCreateTask, enabled = activities.any { it.kind != "Tarea" && it.kind != "Reunión" }) {
                            Text("Crear tarea")
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                week.forEach { date ->
                    val selected = date == selectedDate
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) Brand else Color.White)
                            .clickable { selectedDate = date }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(date.format(DateTimeFormatter.ofPattern("EE", Locale("es", "CL"))).replaceFirstChar { it.titlecase(Locale("es", "CL")) }, color = if (selected) Color.White.copy(alpha = 0.72f) else TextMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(date.dayOfMonth.toString(), color = if (selected) Color.White else Brand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (activities.any { it.date == date }) Gold else Color.Transparent))
                    }
                }
            }
        }
        item {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "CL"))).replaceFirstChar { it.titlecase(Locale("es", "CL")) },
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Brand,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (dayActivities.isEmpty()) {
            item { EmptyCard("No hay actividades para este día", "Elige otra fecha para consultar tu agenda.") }
        } else {
            items(dayActivities, key = { it.id }) { activity ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(activity.time, color = BrandLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.size(9.dp).clip(CircleShape).background(Gold))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        Column {
                            ActivityCard(activity, ministries, members, projects)
                            if (
                                activity.isAssignedToCurrentUser &&
                                activity.kind == "Tarea" &&
                                activity.taskStatus !in setOf("completed", "cancelled")
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val nextStatus = when (activity.taskStatus) {
                                        "in_progress" -> "paused"
                                        "paused" -> "pending"
                                        else -> "in_progress"
                                    }
                                    TextButton(
                                        onClick = { onUpdateTask(activity, nextStatus) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            when (activity.taskStatus) {
                                                "in_progress" -> "Pausar"
                                                "paused" -> "Reanudar"
                                                else -> "Iniciar"
                                            },
                                            color = Brand,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (activity.taskStatus in setOf("in_progress", "paused")) {
                                        TextButton(
                                            onClick = { onUpdateTask(activity, "completed") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("Completar", color = Brand, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinistriesScreen(
    padding: PaddingValues,
    ministries: List<Ministry>,
    members: List<Member>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("COMUNIDAD", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Ministerios", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Equipos que sirven y crecen juntos.", color = TextMuted, fontSize = 13.sp)
        }
        if (ministries.isEmpty()) {
            item {
                EmptyCard("Sin ministerios registrados", "Los ministerios aparecerán aquí cuando se creen en Supabase.")
            }
        }
        items(ministries, key = { it.id }) { ministry ->
            val leader = members.find { it.id == ministry.leaderId }
            val count = members.count { it.ministryId == ministry.id }
            MinistryCard(ministry, leader, count)
        }
    }
}

@Composable
private fun MinistryCard(ministry: Ministry, leader: Member?, count: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(ministry.color.toColor().copy(alpha = 0.11f)),
                    contentAlignment = Alignment.Center,
                ) { Text(ministry.icon, color = ministry.color.toColor(), fontSize = 21.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ministry.name, color = Brand, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count integrantes", color = TextMuted, fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
            }
            Spacer(Modifier.height(13.dp))
            Text(ministry.description, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(13.dp))
            Text("Líder: ${leader?.name ?: "Sin asignar"}", color = ministry.color.toColor(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MinistryCompactCard(ministry: Ministry, members: List<Member>) {
    val leader = members.find { it.id == ministry.leaderId }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ministry.icon, fontSize = 20.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(ministry.name.removePrefix("Ministerio de "), color = Brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Líder: ${leader?.name?.split(" ")?.take(2)?.joinToString(" ") ?: "Sin asignar"}", color = TextMuted, fontSize = 11.sp)
            }
            Text(members.count { it.ministryId == ministry.id }.toString(), color = ministry.color.toColor(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectsScreen(
    padding: PaddingValues,
    projects: List<ChurchProject>,
    members: List<Member>,
    canCreateProjects: Boolean,
    onCreateProject: () -> Unit,
    onEditProject: (ChurchProject) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("PLANIFICACIÓN", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Proyectos", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Objetivos y avance de la iglesia.", color = TextMuted, fontSize = 13.sp)
            if (canCreateProjects) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCreateProject, colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                    Text("Crear proyecto")
                }
            }
        }
        if (projects.isEmpty()) {
            item {
                EmptyCard("Sin proyectos registrados", "Los proyectos aparecerán aquí cuando se creen en Supabase.")
            }
        }
        items(projects, key = { it.id }) { project ->
            val leader = members.find { it.id == project.leaderId }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("PROYECTO INSTITUCIONAL", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(project.name, color = Brand, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Objetivo", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(project.objective, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(project.description, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Estado: ${project.status}", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Avance", color = TextMuted, fontSize = 12.sp)
                        Text("${project.progress}%", color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(7.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { project.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = BrandLight,
                        trackColor = MutedWarm,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("RESPONSABLE", color = TextMuted, fontSize = 9.sp, letterSpacing = 0.6.sp)
                            Text(leader?.name?.split(" ")?.take(2)?.joinToString(" ") ?: "Sin asignar", color = Color(0xFF44403C), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("FECHA OBJETIVO", color = TextMuted, fontSize = 9.sp, letterSpacing = 0.6.sp)
                            Text("En ${project.dueInDays} días", color = Color(0xFF44403C), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (project.budgetEstimated != null || project.budgetExecuted != null) {
                        Spacer(Modifier.height(12.dp))
                        project.budgetEstimated?.let {
                            Text("Presupuesto estimado: ${it.toCLPCurrency()}", color = TextMuted, fontSize = 12.sp)
                        }
                        project.budgetExecuted?.let {
                            Text("Presupuesto ejecutado: ${it.toCLPCurrency()}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (canCreateProjects) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { onEditProject(project) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Actualizar estado / presupuesto", color = Brand, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    padding: PaddingValues,
    onWorship: () -> Unit,
    onPersonnel: () -> Unit,
    onNotifications: () -> Unit,
    unreadNotifications: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HERRAMIENTAS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Más opciones", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Accede a los espacios de servicio y al directorio.", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        MoreOption(Icons.Outlined.MusicNote, "Alabanza", "Repertorio y catálogo de canciones", onWorship)
        MoreOption(Icons.Outlined.People, "Personal", "Directorio de líderes y colaboradores", onPersonnel)
        MoreOption(
            Icons.Outlined.NotificationsNone,
            "Avisos",
            if (unreadNotifications == 0) "No tienes avisos sin leer" else "$unreadNotifications avisos sin leer",
            onNotifications,
        )
    }
}

@Composable
private fun MoreOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(0xFFEFF6FF.toColor()), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = BrandLight)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

@Composable
private fun WorshipScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    activities: List<ChurchActivity>,
    songs: List<Triple<String, String, String>>,
    canManage: Boolean,
    onCreateSong: () -> Unit,
) {
    val nextService = activities.firstOrNull {
        it.kind == "Actividad" && it.date >= LocalDate.now()
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = Brand)
            Text("Volver", color = Brand)
        }
        Text("MINISTERIO DE ALABANZA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Repertorio", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (canManage) {
            Button(onClick = onCreateSong, colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                Text("Agregar canción")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Brand), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("PRÓXIMO SERVICIO", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(7.dp))
                Text(nextService?.title ?: "Sin servicio programado", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    nextService?.let { "${it.date.toCalendarLabel()} · ${it.time}" } ?: "Los próximos servicios aparecerán aquí.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                )
            }
        }
        SectionHeading("Canciones", null)
        if (songs.isEmpty()) {
            EmptyCard("Sin canciones en el catálogo", "Agrega canciones al catálogo del ministerio de Alabanza.")
        }
        songs.forEachIndexed { index, song ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(0xFFEFF6FF.toColor()), contentAlignment = Alignment.Center) {
                        Text((index + 1).toString(), color = BrandLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.first, color = Brand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(song.second, color = TextMuted, fontSize = 11.sp)
                    }
                    Text(song.third, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Catálogo sincronizado con Supabase", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun PersonnelScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    currentMember: Member,
    members: List<Member>,
    ministries: List<Ministry>,
    onUpdateMember: (Member, String, String, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    val filtered = remember(members, query) {
        members.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(
        Modifier.fillMaxSize().padding(padding),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = Brand)
                Text("Volver", color = Brand)
            }
            Text("DIRECTORIO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Personal", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                placeholder = { Text("Buscar persona") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyCard("Sin integrantes visibles", "El directorio solo muestra perfiles autorizados por tus permisos.")
                }
            }
            items(filtered, key = { it.id }) { person ->
                val ministry = ministries.find { it.id == person.ministryId }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(43.dp).clip(CircleShape).background((ministry?.color?.toColor() ?: BrandLight).copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                            Text(person.initials, color = ministry?.color?.toColor() ?: BrandLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.name, color = Brand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(person.role, color = TextMuted, fontSize = 11.sp)
                            Text(ministry?.name?.removePrefix("Ministerio de ") ?: "Administración general", color = ministry?.color?.toColor() ?: Navy, fontSize = 10.sp)
                            if (!person.active) {
                                Text("Cuenta inactiva", color = Color(0xFFB42318), fontSize = 10.sp)
                            }
                            if (currentMember.role == "Pastor/Administrador") {
                                TextButton(
                                    onClick = { editingMember = person },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                ) {
                                    Text("Editar perfil y rol", color = Brand, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editingMember?.let { person ->
        AdminProfileDialog(
            member = person,
            onDismiss = { editingMember = null },
            onSave = { name, role, active ->
                onUpdateMember(person, name, role, active)
                editingMember = null
            },
        )
    }
}

@Composable
private fun AdminProfileDialog(
    member: Member,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var name by remember(member.id) { mutableStateOf(member.name) }
    var role by remember(member.id) { mutableStateOf(member.role) }
    var active by remember(member.id) { mutableStateOf(member.active) }
    val roles = listOf("Pastor/Administrador", "Líder de ministerio", "Líder normal")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrar perfil", color = Brand) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(member.email.ifBlank { member.id }, color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                roles.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { role = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = role == option, onCheckedChange = { if (it) role = option })
                        Text(option, color = Brand, fontSize = 13.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { active = !active },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Cuenta activa", color = Brand, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), role, active) },
            ) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
    )
}

@Composable
private fun SongFormDialog(
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (WorshipSongDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var songKey by remember { mutableStateOf("C") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar canción", color = Brand) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(songKey, { songKey = it }, label = { Text("Tonalidad") }, singleLine = true)
                if (error != null) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && songKey.isNotBlank(),
                onClick = {
                    onSubmit(WorshipSongDraft(title.trim(), artist.trim(), songKey.trim()))
                },
            ) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
    )
}

@Composable
private fun NotificationsScreen(
    padding: PaddingValues,
    notifications: List<ChurchNotification>,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = Brand)
            Text("Volver", color = Brand)
        }
        Text("NOTIFICACIONES", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Avisos", color = Brand, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (notifications.isEmpty()) {
            EmptyCard("Sin avisos", "Las notificaciones dirigidas a tu cuenta aparecerán aquí.")
        } else {
            notifications.forEach { notification ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (notification.read) Color.White else 0xFFFBF4E0.toColor(),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(notification.message, color = Brand, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text("${notification.type.toNotificationLabel()} · ${notification.createdAt.take(10)}", color = TextMuted, fontSize = 11.sp)
                        if (!notification.read) {
                            TextButton(
                                onClick = { onMarkRead(notification.id) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                            ) {
                                Text("Marcar como leído", color = Brand, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectFormDialog(
    members: List<Member>,
    currentMember: Member,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ProjectDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    var budgetEstimated by remember { mutableStateOf("") }
    var budgetExecuted by remember { mutableStateOf("") }
    var leaderId by remember { mutableStateOf(currentMember.id) }
    val datesValid = runCatching {
        !LocalDate.parse(dueDate).isBefore(LocalDate.parse(startDate))
    }.getOrDefault(false)
    val estimatedBudgetValid = budgetEstimated.isBlank() ||
        budgetEstimated.toLongOrNull()?.let { it >= 0 } == true
    val executedBudgetValid = budgetExecuted.isBlank() ||
        budgetExecuted.toLongOrNull()?.let { it >= 0 } == true
    val leaderOptions = members.filter(Member::active)
    val canSubmit = name.isNotBlank() && objective.isNotBlank() && datesValid &&
        estimatedBudgetValid && executedBudgetValid && leaderOptions.any { it.id == leaderId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear proyecto", color = Brand) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(objective, { objective = it }, label = { Text("Objetivo") })
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
                Text("Responsable", color = TextMuted, fontSize = 12.sp)
                leaderOptions.forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clickable { leaderId = candidate.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = leaderId == candidate.id, onCheckedChange = { leaderId = candidate.id })
                        Text(candidate.name, color = Brand, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(startDate, { startDate = it }, label = { Text("Fecha de inicio (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Fecha objetivo (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(budgetEstimated, { budgetEstimated = it }, label = { Text("Presupuesto estimado en pesos (opcional)") }, singleLine = true)
                OutlinedTextField(budgetExecuted, { budgetExecuted = it }, label = { Text("Presupuesto ejecutado en pesos (opcional)") }, singleLine = true)
                if (!datesValid) Text("Revisa las fechas y asegúrate de que la fecha objetivo no sea anterior.", color = Color(0xFFB42318), fontSize = 11.sp)
                if (!estimatedBudgetValid || !executedBudgetValid) Text("Los presupuestos deben ser números enteros no negativos.", color = Color(0xFFB42318), fontSize = 11.sp)
                if (error != null) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        ProjectDraft(
                            name = name.trim(),
                            objective = objective.trim(),
                            description = description.trim(),
                            leaderId = leaderId,
                            startDate = startDate,
                            dueDate = dueDate,
                            budgetEstimated = budgetEstimated.takeIf { it.isNotBlank() }?.toLongOrNull(),
                            budgetExecuted = budgetExecuted.takeIf { it.isNotBlank() }?.toLongOrNull(),
                        ),
                    )
                },
            ) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
    )
}

@Composable
private fun ProjectEditDialog(
    project: ChurchProject,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ProjectUpdateDraft) -> Unit,
) {
    val statuses = listOf(
        "Por hacer" to "Por hacer",
        "En curso" to "En curso",
        "En pausa" to "En pausa",
        "Completada" to "Completada",
    )
    var selectedStatus by remember { mutableStateOf(project.status) }
    var budgetExecutedText by remember {
        mutableStateOf(project.budgetExecuted?.toString() ?: "")
    }
    val executedBudgetValid = budgetExecutedText.isBlank() ||
        budgetExecutedText.toLongOrNull()?.let { it >= 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizar proyecto", color = Brand) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(project.name, color = Brand, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Estado", color = TextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    statuses.forEach { (value, label) ->
                        OutlinedButton(onClick = { selectedStatus = value }) {
                            Text(if (selectedStatus == value) "✓ $label" else label)
                        }
                    }
                }
                OutlinedTextField(
                    value = budgetExecutedText,
                    onValueChange = { budgetExecutedText = it },
                    label = { Text("Presupuesto ejecutado en pesos") },
                    singleLine = true,
                )
                if (!executedBudgetValid) {
                    Text("El presupuesto debe ser un número entero no negativo.", color = Color(0xFFB42318), fontSize = 11.sp)
                }
                if (error != null) {
                    Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = executedBudgetValid,
                onClick = {
                    onSubmit(
                        ProjectUpdateDraft(
                            id = project.id,
                            status = selectedStatus,
                            budgetExecuted = budgetExecutedText.takeIf { it.isNotBlank() }?.toLongOrNull(),
                        ),
                    )
                },
            ) {
                Text("Actualizar", color = Brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) }
        },
    )
}

@Composable
private fun ActivityFormDialog(
    ministries: List<Ministry>,
    members: List<Member>,
    projects: List<ChurchProject>,
    currentMember: Member,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (ActivityDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf("18:00") }
    var endTime by remember { mutableStateOf("19:00") }
    var location by remember { mutableStateOf("") }
    var ministryId by remember { mutableStateOf(currentMember.ministryId) }
    var kind by remember { mutableStateOf("activity") }
    val categories = listOf("Culto", "Ensayo", "Aseo", "Reunión de líderes", "Libre")
    var category by remember { mutableStateOf("Libre") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var preacherId by remember { mutableStateOf<String?>(null) }
    var worshipLeaderId by remember { mutableStateOf<String?>(null) }
    val assignees = remember { mutableStateOf(setOf(currentMember.id)) }
    val ministryLeader = currentMember.role == "Líder de ministerio"
    val managedMinistryIds = ministries
        .filter { it.leaderId == currentMember.id }
        .mapTo(mutableSetOf(), Ministry::id)
    val availableMinistries = if (ministryLeader) {
        ministries.filter { it.id in managedMinistryIds }
    } else {
        ministries
    }
    val availableMembers = if (ministryLeader) {
        members.filter {
            it.active && ministryId != null && ministryId in it.ministryIds
        }
    } else {
        members.filter(Member::active)
    }
    val isCulto = kind == "activity" && category == "Culto"
    val scheduleValid = runCatching {
        LocalDate.parse(date)
        LocalTime.parse(startTime).isBefore(LocalTime.parse(endTime))
    }.getOrDefault(false)
    val cultoValid = !isCulto || (preacherId != null && worshipLeaderId != null)
    val canSubmit = title.isNotBlank() &&
        scheduleValid &&
        cultoValid &&
        (ministryId == null || ministries.any { it.id == ministryId }) &&
        (!ministryLeader || ministryId in managedMinistryIds) &&
        (kind == "meeting" ||
            (assignees.value.isNotEmpty() &&
                assignees.value.all { assigneeId ->
                    availableMembers.any { it.id == assigneeId }
                }))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear actividad o reunión", color = Brand) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("activity" to "Actividad", "meeting" to "Reunión").forEach { (value, label) ->
                        OutlinedButton(onClick = { kind = value }) {
                            Text(if (kind == value) "✓ $label" else label)
                        }
                    }
                }
                if (kind == "activity") {
                    Text("Categoría", color = TextMuted, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEach { cat ->
                            OutlinedButton(onClick = { category = cat }) {
                                Text(if (category == cat) "✓ $cat" else cat)
                            }
                        }
                    }
                    if (category == "Culto") {
                        Text("Predicador (obligatorio)", color = TextMuted, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            availableMembers.forEach { candidate ->
                                OutlinedButton(onClick = { preacherId = candidate.id }) {
                                    Text(if (preacherId == candidate.id) "✓ ${candidate.name}" else candidate.name)
                                }
                            }
                        }
                        Text("Director de alabanza (obligatorio)", color = TextMuted, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            availableMembers.forEach { candidate ->
                                OutlinedButton(onClick = { worshipLeaderId = candidate.id }) {
                                    Text(if (worshipLeaderId == candidate.id) "✓ ${candidate.name}" else candidate.name)
                                }
                            }
                        }
                    }
                    if (projects.isNotEmpty()) {
                        Text("Proyecto asociado (opcional)", color = TextMuted, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedButton(onClick = { selectedProjectId = null }) {
                                Text(if (selectedProjectId == null) "✓ Ninguno" else "Ninguno")
                            }
                            projects.forEach { prj ->
                                OutlinedButton(onClick = { selectedProjectId = prj.id }) {
                                    Text(if (selectedProjectId == prj.id) "✓ ${prj.name}" else prj.name)
                                }
                            }
                        }
                    }
                }
                Text("Ministerio", color = TextMuted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!ministryLeader) {
                        OutlinedButton(onClick = { ministryId = null }) {
                            Text(if (ministryId == null) "✓ General" else "General")
                        }
                    }
                    availableMinistries.forEach { ministry ->
                        OutlinedButton(onClick = {
                            ministryId = ministry.id
                            assignees.value = emptySet()
                        }) {
                            Text(if (ministryId == ministry.id) "✓ ${ministry.name}" else ministry.name)
                        }
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(startTime, { startTime = it }, label = { Text("Inicio (HH:MM)") }, singleLine = true)
                OutlinedTextField(endTime, { endTime = it }, label = { Text("Término (HH:MM)") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("Lugar") }, singleLine = true)
                Text(if (kind == "meeting") "Participantes" else "Responsables", color = TextMuted, fontSize = 12.sp)
                availableMembers.forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            assignees.value = if (candidate.id in assignees.value) {
                                assignees.value - candidate.id
                            } else {
                                assignees.value + candidate.id
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = candidate.id in assignees.value,
                            onCheckedChange = { checked ->
                                assignees.value = if (checked) assignees.value + candidate.id else assignees.value - candidate.id
                            },
                        )
                        Text(candidate.name, color = Brand, fontSize = 12.sp)
                    }
                }
                if (kind == "activity" && availableMembers.isEmpty()) {
                    Text(
                        "No hay miembros activos disponibles en este ministerio para asignar.",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                if (!scheduleValid) Text("Usa fechas y horas válidas; el término debe ser posterior al inicio.", color = Color(0xFFB42318), fontSize = 11.sp)
                if (isCulto && !cultoValid) Text("Para un Culto, debes seleccionar un predicador y un director de alabanza.", color = Color(0xFFB42318), fontSize = 11.sp)
                if (error != null) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        ActivityDraft(
                            title = title.trim(),
                            description = description.trim(),
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            ministryId = ministryId,
                            location = location.trim(),
                            kind = kind,
                            category = if (kind == "activity") category else "Libre",
                            projectId = if (kind == "activity") selectedProjectId else null,
                            worshipLeaderId = if (isCulto) worshipLeaderId else null,
                            preacherId = if (isCulto) preacherId else null,
                            assigneeIds = assignees.value.toList(),
                        ),
                    )
                },
            ) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
    )
}

@Composable
private fun TaskFormDialog(
    activities: List<ChurchActivity>,
    members: List<Member>,
    currentMember: Member,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (TaskDraft) -> Unit,
) {
    val ministryLeader = currentMember.role == "Líder de ministerio"
    val parentActivities = activities.filter {
        it.kind != "Tarea" &&
            it.kind != "Reunión" &&
            (!ministryLeader || it.ministryId in currentMember.ministryIds)
    }
    var activityId by remember(parentActivities) {
        mutableStateOf(parentActivities.firstOrNull()?.id.orEmpty())
    }
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var dueTime by remember { mutableStateOf("18:00") }
    var priority by remember { mutableStateOf("medium") }
    var assigneeId by remember { mutableStateOf(currentMember.id) }
    val selectedParentMinistryId = parentActivities.firstOrNull { it.id == activityId }?.ministryId

    val availableMembers = members.filter { member ->
        member.active &&
            (!ministryLeader ||
                (selectedParentMinistryId != null && selectedParentMinistryId in member.ministryIds))
    }
    val deadlineValid = dueDate.isBlank() || runCatching {
        LocalDate.parse(dueDate)
        LocalTime.parse(dueTime)
    }.isSuccess
    val canSubmit = title.isNotBlank() &&
        instructions.isNotBlank() &&
        parentActivities.any { it.id == activityId } &&
        availableMembers.any { it.id == assigneeId } &&
        deadlineValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear tarea", color = Brand) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instrucciones") })
                Text("Actividad asociada", color = TextMuted, fontSize = 12.sp)
                parentActivities.forEach { activity ->
                    val activityMembers = members.filter { member ->
                        member.active &&
                            (!ministryLeader || activity.ministryId in member.ministryIds)
                    }
                    OutlinedButton(
                        onClick = {
                            activityId = activity.id
                            if (assigneeId !in activityMembers.map(Member::id)) {
                                assigneeId = activityMembers.firstOrNull()?.id.orEmpty()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (activity.id == activityId) "✓ ${activity.title}" else activity.title)
                    }
                }
                if (parentActivities.isEmpty()) {
                    Text("Primero debes crear una actividad para poder asignarle tareas.", color = TextMuted, fontSize = 12.sp)
                }
                Text("Responsable", color = TextMuted, fontSize = 12.sp)
                availableMembers.forEach { candidate ->
                    Row(
                        Modifier.fillMaxWidth().clickable { assigneeId = candidate.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = assigneeId == candidate.id,
                            onCheckedChange = { checked ->
                                if (checked) assigneeId = candidate.id
                            },
                        )
                        Text(candidate.name, color = Brand, fontSize = 12.sp)
                    }
                }
                if (availableMembers.isEmpty()) {
                    Text(
                        "No hay miembros activos disponibles en esta actividad para asignar.",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Text("Prioridad", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("high" to "Alta", "medium" to "Media", "low" to "Baja").forEach { (value, label) ->
                        OutlinedButton(onClick = { priority = value }) {
                            Text(if (priority == value) "✓ $label" else label)
                        }
                    }
                }
                OutlinedTextField(
                    dueDate,
                    { dueDate = it },
                    label = { Text("Fecha límite (AAAA-MM-DD, opcional)") },
                    singleLine = true,
                )
                if (dueDate.isNotBlank()) {
                    OutlinedTextField(
                        dueTime,
                        { dueTime = it },
                        label = { Text("Hora límite (HH:MM)") },
                        singleLine = true,
                    )
                }
                if (!deadlineValid) {
                    Text("Revisa el formato de la fecha y hora límite.", color = Color(0xFFB42318), fontSize = 11.sp)
                }
                if (error != null) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val dueAt = dueDate.takeIf(String::isNotBlank)?.let {
                        LocalDateTime.parse("${it}T$dueTime")
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                    }
                    onSubmit(
                        TaskDraft(
                            activityId = activityId,
                            title = title.trim(),
                            instructions = instructions.trim(),
                            assigneeId = assigneeId,
                            dueAt = dueAt,
                            priority = priority,
                        ),
                    )
                },
            ) { Text("Guardar", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
    )
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarm),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Brand, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}

private fun Long.toColor() = Color(this)
private fun Int.toColor() = Color(this)

private fun LocalDate.toCalendarLabel(): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "CL")))
        .replaceFirstChar { it.titlecase(Locale("es", "CL")) }

private fun Long.toCLPCurrency(): String =
    "$" + String.format(Locale("es", "CL"), "%,d", this).replace(',', '.')

private fun String.toTaskStatusLabel(): String = when (this) {
    "pending" -> "Por hacer"
    "in_progress" -> "En curso"
    "paused" -> "En pausa"
    "completed" -> "Completada"
    "cancelled" -> "Cancelada"
    else -> error("El estado de la tarea no está reconocido.")
}

private fun String.toPriorityLabel(): String = when (this) {
    "high" -> "Alta"
    "medium" -> "Media"
    "low" -> "Baja"
    else -> error("La prioridad de la tarea no está reconocida.")
}

private fun String.toNotificationLabel(): String = when (this) {
    "assignment" -> "Asignación"
    "schedule" -> "Agenda"
    else -> "Sistema"
}
