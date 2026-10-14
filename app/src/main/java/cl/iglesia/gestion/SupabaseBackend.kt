package cl.iglesia.gestion

import cl.iglesia.gestion.BuildConfig
import android.util.Base64
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object SupabaseBackend {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
            !BuildConfig.SUPABASE_URL.contains("YOUR_PROJECT_REF") &&
            BuildConfig.SUPABASE_ANON_KEY.isPublicClientKey()

    val client by lazy {
        check(isConfigured) { "Configura Supabase en android/supabase.properties." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    suspend fun loadCurrentMember(): Member {
        val user = client.auth.retrieveUserForCurrentSession()
        val profile = client.from("profiles")
            .select {
                filter {
                    eq("id", user.id)
                }
            }
            .decodeSingle<SupabaseProfile>()
        val ministryIds = client.from("ministry_memberships")
            .select {
                filter {
                    eq("user_id", user.id)
                }
            }
            .decodeList<SupabaseMembership>()
            .map(SupabaseMembership::ministryId)

        check(profile.isActive) {
            "Esta cuenta está inactiva. Contacta al Pastor/Administrador."
        }

        val displayName = profile.fullName.ifBlank {
            user.email?.substringBefore("@")?.takeIf(String::isNotBlank) ?: "Usuario"
        }
        return Member(
            id = profile.id,
            name = displayName,
            role = profile.role.toRoleLabel(),
            ministryId = ministryIds.firstOrNull(),
            initials = displayName.split(Regex("\\s+"))
                .take(2)
                .mapNotNull(String::firstOrNull)
                .joinToString("")
                .uppercase(),
            email = user.email.orEmpty(),
            active = profile.isActive,
            phone = profile.phone.orEmpty(),
            ministryIds = ministryIds,
        )
    }

    suspend fun loadChurchSnapshot(currentMember: Member): ChurchSnapshot {
        val ministryRows = client.from("ministries")
            .select()
            .decodeList<SupabaseMinistry>()
            .filter(SupabaseMinistry::isActive)
        val membershipRows = client.from("ministry_memberships")
            .select()
            .decodeList<SupabaseMembership>()
        val memberRows = client.from("profiles")
            .select()
            .decodeList<SupabaseProfile>()
        val members = memberRows.map { profile ->
            val name = profile.fullName.ifBlank {
                if (profile.id == currentMember.id) currentMember.name else "Usuario"
            }
            Member(
                id = profile.id,
                name = name,
                role = profile.role.toRoleLabel(),
                ministryId = membershipRows.firstOrNull { it.userId == profile.id }?.ministryId,
                initials = name.initials(),
                email = if (profile.id == currentMember.id) currentMember.email else "",
                active = profile.isActive,
                phone = profile.phone.orEmpty(),
                ministryIds = membershipRows
                    .filter { it.userId == profile.id }
                    .map(SupabaseMembership::ministryId),
            )
        }
        val leaderByMinistry = membershipRows
            .filter(SupabaseMembership::isLeader)
            .associate { it.ministryId to it.userId }
        val ministries = ministryRows.mapIndexed { index, ministry ->
            Ministry(
                id = ministry.id,
                name = ministry.name,
                icon = ministry.name.ministryIcon(),
                color = MINISTRY_COLORS[index % MINISTRY_COLORS.size],
                leaderId = leaderByMinistry[ministry.id].orEmpty(),
                description = ministry.description,
            )
        }
        val projects = client.from("projects")
            .select()
            .decodeList<SupabaseProject>()
            .map { project ->
                ChurchProject(
                    name = project.name,
                    objective = project.objective,
                    description = project.description,
                    leaderId = project.leaderId,
                    dueInDays = ChronoUnit.DAYS.between(
                        LocalDate.now(),
                        LocalDate.parse(project.dueDate),
                    ).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                    progress = project.progress,
                    status = project.status.toProjectStatusLabel(),
                    budgetEstimated = project.budget?.toLong(),
                    budgetExecuted = project.budgetExecuted?.toLong(),
                    id = project.id,
                )
            }
        val activities = client.from("activities")
            .select()
            .decodeList<SupabaseActivity>()
        val assignedActivityIds = client.from("activity_assignments")
            .select {
                filter {
                    eq("user_id", currentMember.id)
                }
            }
            .decodeList<SupabaseActivityAssignment>()
            .mapTo(mutableSetOf(), SupabaseActivityAssignment::activityId)
        val churchActivities = activities.map {
            it.toChurchActivity(it.id in assignedActivityIds)
        }
        val tasks = client.from("tasks")
            .select()
            .decodeList<SupabaseTask>()
        val activityById = activities.associateBy(SupabaseActivity::id)
        val churchTasks = tasks.map { task ->
            val parentActivity = checkNotNull(activityById[task.activityId]) {
                "No se encontró la actividad asociada a la tarea ${task.id}."
            }
            val dueAt = task.dueAt?.let(OffsetDateTime::parse)
            ChurchActivity(
                id = task.id,
                title = task.title,
                description = task.instructions,
                date = dueAt?.toLocalDate() ?: LocalDate.parse(parentActivity.activityDate),
                time = dueAt?.toLocalTime()?.toString()?.take(5) ?: parentActivity.startTime.take(5),
                endTime = dueAt?.toLocalTime()?.toString()?.take(5) ?: parentActivity.endTime.take(5),
                ministryId = parentActivity.ministryId,
                location = parentActivity.location,
                status = task.status.toTaskStateLabel(),
                kind = "Tarea",
                taskStatus = task.status,
                isAssignedToCurrentUser = task.assigneeId == currentMember.id,
                parentActivityTitle = parentActivity.title,
                priority = task.priority,
            )
        }
        val meetings = client.from("meetings")
            .select()
            .decodeList<SupabaseMeeting>()
            .map(SupabaseMeeting::toChurchActivity)
        val worshipMinistryId = ministries.firstOrNull {
            it.name.contains("alabanza", ignoreCase = true)
        }?.id
        val songs = worshipMinistryId?.let { ministryId ->
            client.from("worship_songs")
                .select {
                    filter {
                        eq("ministry_id", ministryId)
                    }
                }
                .decodeList<SupabaseSong>()
                .map { Triple(it.title, it.artist, it.defaultKey) }
        }.orEmpty()
        val notifications = client.from("notifications")
            .select()
            .decodeList<SupabaseNotification>()
            .map {
                ChurchNotification(
                    id = it.id,
                    message = it.message,
                    type = it.type,
                    createdAt = it.createdAt,
                    read = it.read,
                )
            }

        return ChurchSnapshot(
            members = members,
            ministries = ministries,
            activities = (churchActivities + churchTasks + meetings).sortedWith(
                compareBy(ChurchActivity::date, ChurchActivity::time),
            ),
            projects = projects,
            songs = songs,
            notifications = notifications,
        )
    }

    suspend fun createProject(currentMember: Member, draft: ProjectDraft) {
        require(currentMember.role == "Pastor/Administrador") {
            "Solo el Pastor/Administrador puede crear proyectos."
        }
        client.from("projects").insert(
            ProjectInsert(
                id = UUID.randomUUID().toString(),
                name = draft.name.trim(),
                objective = draft.objective.trim(),
                description = draft.description.trim(),
                leaderId = draft.leaderId,
                startDate = draft.startDate,
                dueDate = draft.dueDate,
                budget = draft.budgetEstimated,
                budgetExecuted = draft.budgetExecuted,
                createdBy = currentMember.id,
            ),
        )
    }

    suspend fun createActivity(currentMember: Member, draft: ActivityDraft) {
        val activityId = UUID.randomUUID().toString()
        if (draft.kind == "meeting") {
            client.from("meetings").insert(
                MeetingInsert(
                    id = activityId,
                    title = draft.title.trim(),
                    purpose = draft.description.trim(),
                    meetingDate = draft.date,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    location = draft.location.trim(),
                    ministryId = draft.ministryId,
                    createdBy = currentMember.id,
                ),
            )
            if (draft.assigneeIds.isNotEmpty()) {
                client.from("meeting_participants").insert(
                    draft.assigneeIds.distinct().map { userId ->
                        MeetingParticipantInsert(activityId, userId)
                    },
                )
            }
            return
        }

        client.from("activities").insert(
            ActivityInsert(
                id = activityId,
                title = draft.title.trim(),
                description = draft.description.trim(),
                activityDate = draft.date,
                startTime = draft.startTime,
                endTime = draft.endTime,
                ministryId = draft.ministryId,
                activityType = if (draft.ministryId == null) "general" else "ministry",
                kind = draft.kind,
                location = draft.location.trim(),
                priority = null,
                taskStatus = null,
                createdBy = currentMember.id,
            ),
        )
        if (draft.assigneeIds.isNotEmpty()) {
            client.from("activity_assignments").insert(
                draft.assigneeIds.distinct().map { userId ->
                    ActivityAssignmentInsert(
                        activityId = activityId,
                        userId = userId,
                        assignedBy = currentMember.id,
                    )
                },
            )
        }
    }

    suspend fun createTask(currentMember: Member, draft: TaskDraft) {
        require(currentMember.role in setOf("Pastor/Administrador", "Líder de ministerio")) {
            "Solo el Pastor/Administrador o el Líder de ministerio puede asignar tareas."
        }
        client.from("tasks").insert(
            TaskInsert(
                id = UUID.randomUUID().toString(),
                activityId = draft.activityId,
                title = draft.title.trim(),
                instructions = draft.instructions.trim(),
                assigneeId = draft.assigneeId,
                dueAt = draft.dueAt,
                priority = draft.priority,
                status = "pending",
                assignedBy = currentMember.id,
            ),
        )
    }

    suspend fun updateTaskStatus(taskId: String, status: String) {
        require(status in setOf("pending", "in_progress", "paused", "completed"))
        client.from("tasks").update(TaskStatusUpdate(status)) {
            filter {
                eq("id", taskId)
            }
        }
    }

    suspend fun updateLegacyTaskStatus(activityId: String, status: String) {
        require(status in setOf("pending", "in_progress", "paused", "completed", "cancelled"))
        client.from("activities").update(LegacyTaskStatusUpdate(status)) {
            filter {
                eq("id", activityId)
                eq("kind", "task")
            }
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        client.from("notifications").update(NotificationReadUpdate(read = true)) {
            filter {
                eq("id", notificationId)
            }
        }
    }

    suspend fun createWorshipSong(currentMember: Member, ministryId: String, draft: WorshipSongDraft) {
        client.from("worship_songs").insert(
            WorshipSongInsert(
                id = UUID.randomUUID().toString(),
                ministryId = ministryId,
                title = draft.title.trim(),
                artist = draft.artist.trim(),
                defaultKey = draft.defaultKey.trim(),
                createdBy = currentMember.id,
            ),
        )
    }

    suspend fun updateProfileAsPastor(
        target: Member,
        fullName: String,
        role: String,
        active: Boolean,
    ) {
        val databaseRole = when (role) {
            "Pastor/Administrador" -> "pastor"
            "Líder de ministerio" -> "ministry_leader"
            "Líder normal" -> "leader"
            else -> error("El rol seleccionado no es válido.")
        }
        client.postgrest.rpc(
            function = "admin_update_profile",
            parameters = buildJsonObject {
                put("target_profile_id", target.id)
                put("target_full_name", fullName.trim())
                put("target_phone", target.phone)
                put("target_role", databaseRole)
                put("target_is_active", active)
            },
        )
    }
}

private val MINISTRY_COLORS = listOf(
    0xFF7C2D3E,
    0xFF1E3A5F,
    0xFF5A7A2B,
    0xFF9A6E1A,
)

private fun String.initials(): String = split(Regex("\\s+"))
    .take(2)
    .mapNotNull(String::firstOrNull)
    .joinToString("")
    .uppercase()

private fun String.ministryIcon(): String = when {
    contains("alabanza", ignoreCase = true) -> "♫"
    contains("juventud", ignoreCase = true) -> "⚡"
    contains("aseo", ignoreCase = true) -> "✦"
    else -> "✝"
}

private fun SupabaseActivity.toChurchActivity(isAssignedToCurrentUser: Boolean) = ChurchActivity(
    id = id,
    title = title,
    description = description,
    date = LocalDate.parse(activityDate),
    time = startTime.take(5),
    endTime = endTime.take(5),
    ministryId = ministryId,
    location = location,
    status = status.toEventStatusLabel(),
    kind = when (kind) {
        "task" -> "Tarea"
        "meeting" -> "Reunión"
        else -> "Actividad"
    },
    taskStatus = taskStatus,
    isAssignedToCurrentUser = isAssignedToCurrentUser,
    isLegacyTask = kind == "task",
)

private fun SupabaseMeeting.toChurchActivity() = ChurchActivity(
    id = id,
    title = title,
    description = purpose,
    date = LocalDate.parse(meetingDate),
    time = startTime.take(5),
    endTime = endTime.take(5),
    ministryId = ministryId,
    location = location,
    status = status.toEventStatusLabel(),
    kind = "Reunión",
)

private fun String.toEventStatusLabel(): String = when (this) {
    "completed" -> "Completada"
    "cancelled" -> "Cancelada"
    else -> "Programada"
}

private fun String.toTaskStateLabel(): String = when (this) {
    "pending" -> "Por hacer"
    "in_progress" -> "En curso"
    "paused" -> "En pausa"
    "completed" -> "Completada"
    "cancelled" -> "Cancelada"
    else -> error("El estado de la tarea no está reconocido.")
}

private fun String.toProjectStatusLabel(): String = when (this) {
    "pending" -> "Por hacer"
    "in_progress" -> "En curso"
    "paused" -> "En pausa"
    "completed" -> "Completada"
    "cancelled" -> "Cancelada"
    else -> error("El estado del proyecto no está reconocido.")
}

private fun String.isPublicClientKey(): Boolean {
    if (startsWith("sb_publishable_")) return true
    if (isBlank() || startsWith("sb_secret_")) return false

    val payload = split(".").getOrNull(1) ?: return false
    return try {
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        JSONObject(String(decoded, StandardCharsets.UTF_8)).optString("role") == "anon"
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: org.json.JSONException) {
        false
    }
}

@Serializable
private data class SupabaseProfile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class SupabaseMembership(
    @SerialName("ministry_id") val ministryId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("is_leader") val isLeader: Boolean,
)

@Serializable
private data class SupabaseMinistry(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class SupabaseProject(
    val id: String,
    val name: String,
    val objective: String = "",
    val description: String,
    @SerialName("ministry_id") val ministryId: String? = null,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("due_date") val dueDate: String,
    val budget: Double? = null,
    @SerialName("budget_executed") val budgetExecuted: Double? = null,
    val progress: Int,
    val status: String,
)

@Serializable
private data class SupabaseActivity(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String? = null,
    val location: String,
    val status: String,
    val kind: String,
    @SerialName("task_status") val taskStatus: String? = null,
)

@Serializable
private data class SupabaseTask(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val title: String,
    val instructions: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String? = null,
    val priority: String,
    val status: String,
)

@Serializable
private data class SupabaseActivityAssignment(
    @SerialName("activity_id") val activityId: String,
)

@Serializable
private data class SupabaseMeeting(
    val id: String,
    val title: String,
    val purpose: String,
    @SerialName("meeting_date") val meetingDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String? = null,
    val location: String,
    val status: String,
)

@Serializable
private data class SupabaseSong(
    val title: String,
    val artist: String,
    @SerialName("default_key") val defaultKey: String,
)

@Serializable
private data class SupabaseNotification(
    val id: String,
    val message: String,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    val read: Boolean,
)

@Serializable
private data class ProjectInsert(
    val id: String,
    val name: String,
    val objective: String,
    val description: String,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("due_date") val dueDate: String,
    val budget: Long?,
    @SerialName("budget_executed") val budgetExecuted: Long?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ActivityInsert(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String?,
    @SerialName("activity_type") val activityType: String,
    val kind: String,
    val location: String,
    val priority: String?,
    @SerialName("task_status") val taskStatus: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ActivityAssignmentInsert(
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("assigned_by") val assignedBy: String,
)

@Serializable
private data class MeetingInsert(
    val id: String,
    val title: String,
    val purpose: String,
    @SerialName("meeting_date") val meetingDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val location: String,
    @SerialName("ministry_id") val ministryId: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class MeetingParticipantInsert(
    @SerialName("meeting_id") val meetingId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class TaskStatusUpdate(
    val status: String,
)

@Serializable
private data class LegacyTaskStatusUpdate(
    @SerialName("task_status") val taskStatus: String,
)

@Serializable
private data class TaskInsert(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val title: String,
    val instructions: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String?,
    val priority: String,
    val status: String,
    @SerialName("assigned_by") val assignedBy: String,
)

@Serializable
private data class NotificationReadUpdate(val read: Boolean)

@Serializable
private data class WorshipSongInsert(
    val id: String,
    @SerialName("ministry_id") val ministryId: String,
    val title: String,
    val artist: String,
    @SerialName("default_key") val defaultKey: String,
    @SerialName("created_by") val createdBy: String,
)

private fun String.toRoleLabel(): String = when (this) {
    "pastor" -> "Pastor/Administrador"
    "ministry_leader" -> "Líder de ministerio"
    "leader" -> "Líder normal"
    else -> error("El rol de esta cuenta no está reconocido.")
}
