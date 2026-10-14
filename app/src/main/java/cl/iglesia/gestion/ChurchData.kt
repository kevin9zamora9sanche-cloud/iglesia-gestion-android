package cl.iglesia.gestion

import java.time.LocalDate

data class Member(
    val id: String,
    val name: String,
    val role: String,
    val ministryId: String?,
    val initials: String,
    val email: String,
    val active: Boolean = true,
    val phone: String = "",
    val ministryIds: List<String> = ministryId?.let(::listOf).orEmpty(),
)

data class Ministry(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val leaderId: String,
    val description: String,
)

data class ChurchActivity(
    val id: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: String,
    val endTime: String,
    val ministryId: String?,
    val location: String,
    val status: String = "Programada",
    val kind: String = "Actividad",
    val taskStatus: String? = null,
    val isAssignedToCurrentUser: Boolean = false,
    val parentActivityTitle: String? = null,
    val priority: String? = null,
    val isLegacyTask: Boolean = false,
)

data class ChurchProject(
    val name: String,
    val objective: String,
    val description: String,
    val leaderId: String,
    val dueInDays: Int,
    val progress: Int,
    val status: String,
    val budgetEstimated: Long? = null,
    val budgetExecuted: Long? = null,
    val id: String = name,
)

data class ChurchSnapshot(
    val members: List<Member>,
    val ministries: List<Ministry>,
    val activities: List<ChurchActivity>,
    val projects: List<ChurchProject>,
    val songs: List<Triple<String, String, String>>,
    val notifications: List<ChurchNotification>,
)

data class ChurchNotification(
    val id: String,
    val message: String,
    val type: String,
    val createdAt: String,
    val read: Boolean,
)

data class ProjectDraft(
    val name: String,
    val objective: String,
    val description: String,
    val leaderId: String,
    val startDate: String,
    val dueDate: String,
    val budgetEstimated: Long?,
    val budgetExecuted: Long?,
)

data class ActivityDraft(
    val title: String,
    val description: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val ministryId: String?,
    val location: String,
    val kind: String,
    val assigneeIds: List<String>,
)

data class TaskDraft(
    val activityId: String,
    val title: String,
    val instructions: String,
    val assigneeId: String,
    val dueAt: String?,
    val priority: String,
)

data class WorshipSongDraft(
    val title: String,
    val artist: String,
    val defaultKey: String,
)

object ChurchData {
    val members = listOf(
        Member("u1", "Pastor Rodrigo Méndez", "Pastor", null, "RM", "pastor@iglesia.cl"),
        Member("u2", "Ana Sofía Herrera", "Líder de Ministerio", "alabanza", "AH", "ana@iglesia.cl"),
        Member("u3", "Matías Fuentes", "Líder de Ministerio", "juventud", "MF", "matias@iglesia.cl"),
        Member("u4", "Carmen Rosa López", "Líder de Ministerio", "aseo", "CL", "carmen@iglesia.cl"),
        Member("u5", "Felipe Castillo", "Líder", "alabanza", "FC", "felipe@iglesia.cl"),
        Member("u6", "Valentina Torres", "Líder", "alabanza", "VT", "valentina@iglesia.cl"),
        Member("u7", "Diego Navarro", "Líder", "juventud", "DN", "diego@iglesia.cl"),
        Member("u8", "Lucía Ramírez", "Líder", "juventud", "LR", "lucia@iglesia.cl"),
        Member("u9", "Jorge Muñoz", "Líder", "aseo", "JM", "jorge@iglesia.cl"),
    )

    val ministries = listOf(
        Ministry("alabanza", "Ministerio de Alabanza", "♫", 0xFF7C2D3E, "u2", "Dirigir la adoración y la música en cultos y eventos especiales."),
        Ministry("juventud", "Ministerio de Juventud", "⚡", 0xFF1E3A5F, "u3", "Discipulado, actividades y formación espiritual para jóvenes."),
        Ministry("aseo", "Ministerio de Aseo", "✦", 0xFF5A7A2B, "u4", "Mantenimiento y limpieza de las instalaciones de la iglesia."),
    )

    fun activities(today: LocalDate = LocalDate.now()) = listOf(
        ChurchActivity("a1", "Culto Dominical", "Culto de adoración y predicación para toda la congregación.", today.plusDays(2), "10:00", "12:30", "alabanza", "Santuario Principal"),
        ChurchActivity("a2", "Ensayo Coro", "Ensayo semanal del coro y músicos para el culto.", today.plusDays(5), "19:00", "21:00", "alabanza", "Sala de Música"),
        ChurchActivity("a3", "Reunión de Líderes", "Coordinación entre el pastor y los líderes de ministerio.", today.plusDays(7), "18:00", "20:00", null, "Sala de Reuniones"),
        ChurchActivity("a4", "Retiro de Jóvenes", "Retiro espiritual de dos días para el ministerio de juventud.", today.plusDays(10), "08:00", "18:00", "juventud", "Campamento Los Robles"),
        ChurchActivity("a5", "Limpieza General", "Aseo de las instalaciones previo a las actividades especiales.", today.plusDays(14), "09:00", "13:00", "aseo", "Todas las instalaciones", kind = "Tarea"),
        ChurchActivity("a6", "Noche de Alabanza", "Servicio de adoración abierto a la comunidad.", today.plusDays(18), "20:00", "22:30", "alabanza", "Santuario Principal"),
        ChurchActivity("a7", "Revisión de Instalaciones", "Inspección mensual de las áreas comunes.", today.minusDays(3), "10:00", "12:00", "aseo", "Instalaciones", "Completada", "Tarea"),
    )

    val projects = listOf(
        ChurchProject(
            name = "Mejora de instalaciones",
            objective = "Mejorar los espacios para las actividades de toda la iglesia.",
            description = "Revisión, mantenimiento y preparación de los espacios de la iglesia.",
            leaderId = "u4",
            dueInDays = 30,
            progress = 55,
            status = "En curso",
            budgetEstimated = 450000,
        ),
        ChurchProject(
            name = "Encuentro anual de jóvenes",
            objective = "Fortalecer la formación y convivencia de la congregación.",
            description = "Planificación integral del próximo encuentro de formación y convivencia.",
            leaderId = "u3",
            dueInDays = 60,
            progress = 20,
            status = "Por hacer",
        ),
    )

    val songs = listOf(
        Triple("La Bondad de Dios", "Bethel Music", "G"),
        Triple("Santo Por Siempre", "Christine D'Clario", "A"),
        Triple("Digno y Santo", "Gateway Worship", "D"),
        Triple("Eres Todopoderoso", "Danilo Montero", "E"),
        Triple("Cuán Grande es Él", "Himno tradicional", "C"),
    )
}
