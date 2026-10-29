package cl.iglesia.gestion

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

object Roles {
    const val PASTOR = "Pastor/Administrador"
    const val MINISTRY_LEADER = "Líder de ministerio"
    const val LEADER = "Líder normal"
    val all = listOf(PASTOR, MINISTRY_LEADER, LEADER)
}

/** Tipos de actividad definidos en los requerimientos. */
object ActivityTypes {
    const val CULTO = "Culto"
    const val ENSAYO = "Ensayo"
    const val ASEO = "Aseo"
    const val LEADERS_MEETING = "Reunión de líderes"
    const val LIBRE = "Libre"
    val all = listOf(CULTO, ENSAYO, ASEO, LEADERS_MEETING, LIBRE)
}

enum class ItemKind { Activity, Task, Meeting }

/** Clasificación de una actividad; se traduce a `actividad.clasificacion` (ver StagingMappings). */
enum class Classification(val databaseValue: String, val label: String) {
    Ministry("ministry", "Ministerio"),
    Project("general", "Proyecto"),
    Individual("personal", "Individual"),
    General("general", "General"),
}

data class Member(
    val id: String,
    val name: String,
    val role: String,
    val initials: String,
    val email: String,
    val active: Boolean = true,
    val phone: String = "",
    val ministryIds: List<String> = emptyList(),
    val ledMinistryIds: List<String> = emptyList(),
    /** `usuario.debe_cambiar_contrasena`: tras ingresar debe elegir una contraseña propia. */
    val mustChangePassword: Boolean = false,
) {
    val isPastor: Boolean get() = role == Roles.PASTOR
    val isMinistryLeader: Boolean get() = role == Roles.MINISTRY_LEADER
    val firstName: String get() = name.substringBefore(" ")

    /** Refleja `can_manage_ministry` de Supabase. */
    fun canManageMinistry(ministryId: String?): Boolean =
        isPastor || (isMinistryLeader && ministryId != null && ministryId in ledMinistryIds)
}

data class Ministry(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val leaderIds: List<String>,
    val description: String,
    val active: Boolean = true,
    /** `ministerio.es_alabanza`. */
    val isWorship: Boolean = false,
) {
    val shortName: String get() = name.removePrefix("Ministerio de ").removePrefix("Ministerio ")
}

/**
 * Elemento de agenda: una actividad, una tarea hija de una actividad o una
 * convocatoria de reunión. [status] contiene el valor de la base de datos.
 */
data class ChurchActivity(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: String,
    val endTime: String,
    val ministryId: String?,
    val location: String,
    val status: String,
    val kind: ItemKind = ItemKind.Activity,
    val category: String = "",
    val classification: Classification = Classification.General,
    val projectId: String? = null,
    val worshipLeaderId: String? = null,
    val preacherId: String? = null,
    val assigneeIds: List<String> = emptyList(),
    val isAssignedToCurrentUser: Boolean = false,
    val parentActivityId: String? = null,
    val parentActivityTitle: String? = null,
    val priority: String? = null,
    val taskComments: List<TaskComment> = emptyList(),
    val dueAt: OffsetDateTime? = null,
) {
    val isTask: Boolean get() = kind == ItemKind.Task
    val isMeeting: Boolean get() = kind == ItemKind.Meeting
    val isDone: Boolean get() = status == "completed"

    /** Etiqueta usada en los filtros de tipo de la agenda. */
    val typeLabel: String
        get() = when (kind) {
            ItemKind.Task -> "Tarea"
            ItemKind.Meeting -> "Reunión"
            ItemKind.Activity -> category.ifBlank { ActivityTypes.LIBRE }
        }

    /** Participación personal: asignaciones, tareas propias y roles de culto. */
    fun involves(memberId: String): Boolean =
        isAssignedToCurrentUser ||
            memberId in assigneeIds ||
            preacherId == memberId ||
            worshipLeaderId == memberId

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean = !isDone && date.isBefore(today)
}

/** Fila de `comentario_tarea`; [authorId] es nulo si el autor ya no existe. */
data class TaskComment(
    val id: String,
    val authorId: String?,
    val text: String,
    val createdAt: OffsetDateTime?,
)

data class ChurchProject(
    val id: String,
    val name: String,
    val objective: String,
    val description: String,
    val leaderId: String,
    val startDate: LocalDate,
    val dueDate: LocalDate,
    val progress: Int,
    val status: String,
    val budgetEstimated: Long? = null,
    val budgetExecuted: Long? = null,
)

data class WorshipSong(
    val id: String,
    val title: String,
    val artist: String,
    val defaultKey: String,
    val referenceUrl: String,
    /** Partituras, letras o acordes (PDF, JPG o PNG). */
    val files: List<RecordFile> = emptyList(),
)

data class WorshipSetSong(
    val songId: String,
    val position: Int,
    val key: String,
    val leadId: String?,
    /** `repertorio_cancion.enlace_version`; se conserva al guardar aunque la pantalla no lo edite. */
    val versionUrl: String = "",
)

/**
 * Fila de `repertorio`. En staging no depende de un culto ni de un ministerio: la app usa
 * el más reciente como repertorio vigente (ver [ministryRepertoire]).
 */
data class WorshipSet(
    val id: String,
    val updatedAt: OffsetDateTime?,
    val songs: List<WorshipSetSong>,
)

/** Repertorio vigente: la lista llega ordenada del más reciente al más antiguo. */
fun List<WorshipSet>.ministryRepertoire(): WorshipSet? = firstOrNull()

/** Visibilidad de un registro de reunión. */
enum class RecordVisibility(val databaseValue: String, val label: String) {
    Everyone("everyone", "Toda la iglesia"),
    Ministry("ministry", "Un ministerio"),
    Selected("selected", "Personas seleccionadas"),
}

data class RecordFile(
    val id: String,
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
    /** `archivo.ruta` dentro del bucket `archivos`. */
    val objectPath: String,
)

/** Fila de `reunion`: registro posterior a una reunión, distinto de la convocatoria del calendario. */
data class MeetingRecord(
    val id: String,
    val meetingId: String?,
    val title: String,
    val date: LocalDate,
    val summary: String,
    val visibility: RecordVisibility,
    val ministryId: String?,
    val viewerIds: List<String>,
    val files: List<RecordFile>,
)

data class MeetingRecordDraft(
    val meetingId: String?,
    val title: String,
    val date: LocalDate,
    val summary: String,
    val visibility: RecordVisibility,
    val ministryId: String?,
    val viewerIds: List<String>,
)

/** Archivo elegido en el dispositivo, ya validado (tipo y tamaño). */
class PickedFile(
    val name: String,
    val contentType: String,
    val bytes: ByteArray,
)

/** Error con un mensaje que puede mostrarse tal cual a la persona usuaria. */
open class UserFacingException(message: String) : Exception(message)

/** Parte de la operación se guardó: el formulario se cierra y se recarga lo persistido. */
class PartialSaveException(message: String) : UserFacingException(message)

const val MAX_FILE_BYTES = 5L * 1024 * 1024
val ALLOWED_FILE_TYPES = mapOf(
    "application/pdf" to "pdf",
    "image/jpeg" to "jpg",
    "image/png" to "png",
)

data class ChurchSnapshot(
    val members: List<Member>,
    val ministries: List<Ministry>,
    /** Ministerios desactivados; solo el Pastor los recibe (RLS). */
    val inactiveMinistries: List<Ministry> = emptyList(),
    val activities: List<ChurchActivity>,
    val projects: List<ChurchProject>,
    val songs: List<WorshipSong>,
    val worshipSets: List<WorshipSet>,
    val notifications: List<ChurchNotification>,
    val meetingRecords: List<MeetingRecord> = emptyList(),
)

/** Fila de `notificacion` del usuario; [itemId] es la actividad o tarea a la que apunta, si existe. */
data class ChurchNotification(
    val id: String,
    val message: String,
    val kind: NotificationKind,
    val itemId: String?,
    val createdAt: OffsetDateTime?,
    val read: Boolean,
)

data class ProjectDraft(
    val id: String? = null,
    val name: String,
    val objective: String,
    val description: String,
    val leaderId: String,
    val startDate: LocalDate,
    val dueDate: LocalDate,
    val status: String,
    val budgetEstimated: Long?,
    val budgetExecuted: Long?,
)

data class ActivityDraft(
    val title: String,
    val description: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val classification: Classification,
    val ministryId: String?,
    val projectId: String?,
    val location: String,
    val category: String,
    val worshipLeaderId: String?,
    val preacherId: String?,
    val assigneeIds: List<String>,
)

data class MeetingDraft(
    val title: String,
    val purpose: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val ministryId: String?,
    val location: String,
    val participantIds: List<String>,
)

data class TaskDraft(
    val activityId: String,
    val title: String,
    val instructions: String,
    val assigneeId: String,
    val dueAt: OffsetDateTime?,
    val priority: String,
    val comments: String,
)

data class WorshipSongDraft(
    val title: String,
    val artist: String,
    val defaultKey: String,
    val referenceUrl: String,
)

/** Política propuesta: mínimo 8 caracteres con al menos una letra y un número. */
fun passwordProblem(password: String): String? = when {
    password.length < 8 -> "Debe tener al menos 8 caracteres."
    password.none(Char::isLetter) || password.none(Char::isDigit) -> "Debe incluir al menos una letra y un número."
    else -> null
}

data class NewUserDraft(
    val email: String,
    val fullName: String,
    val phone: String,
    val role: String,
    /** ministryId -> es líder */
    val memberships: Map<String, Boolean>,
)

data class ProfileDraft(
    val fullName: String,
    val phone: String,
    val role: String,
    val active: Boolean,
    /** ministryId -> es líder */
    val memberships: Map<String, Boolean>,
)

data class MinistryDraft(
    val name: String,
    val description: String,
)
