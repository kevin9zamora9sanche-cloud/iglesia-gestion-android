package cl.iglesia.gestion

enum class NotificationKind { Assignment, Schedule, System }

/**
 * Pure helpers for staging `notificacion`. Rows are created only by database triggers;
 * the app reads them and flips `leida`.
 */
object StagingNotifications {

    /** Groups `notificacion.tipo` (e.g. `ACTIVIDAD_ASIGNADA`) for the icon and label. Unknown types are [NotificationKind.System]. */
    fun kindOf(tipo: String?): NotificationKind {
        val value = tipo?.trim()?.uppercase().orEmpty()
        return when {
            value.contains("ASIGNAD") -> NotificationKind.Assignment
            listOf("ACTIVIDAD", "REUNION", "CONVOCATORIA", "CULTO", "RECORDATORIO").any(value::contains) -> NotificationKind.Schedule
            else -> NotificationKind.System
        }
    }

    /** Agenda item (activity or task id) a notification points to, or null when it has no screen. */
    fun targetItemId(entityType: String?, entityId: String?): String? {
        val id = entityId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (entityType == "actividad" || entityType == "tarea") id else null
    }
}
