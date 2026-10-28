package cl.iglesia.gestion

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Pure helpers for the staging `actividad` model: `inicio`/`fin` timestamps,
 * `convocatoria` rows for meetings and `detalle_culto` details.
 */
object StagingActivities {

    /** Merges the old separate date + start time into the `inicio` timestamptz. */
    fun combine(date: LocalDate, time: LocalTime, zone: ZoneId): OffsetDateTime =
        date.atTime(time).atZone(zone).toOffsetDateTime()

    fun localDate(timestamp: OffsetDateTime, zone: ZoneId): LocalDate =
        timestamp.atZoneSameInstant(zone).toLocalDate()

    /** `HH:mm` in [zone], or blank when the timestamp is missing (`fin` is nullable). */
    fun timeLabel(timestamp: OffsetDateTime?, zone: ZoneId): String =
        timestamp?.atZoneSameInstant(zone)?.toLocalTime()?.toString()?.take(5).orEmpty()

    /** One `convocatoria` row: [alcance] is TODOS, MINISTERIO or USUARIO. */
    data class Target(val alcance: String, val ministryId: String?, val userId: String?)

    /**
     * Rows that call a meeting: the ministry (if any) plus every distinct person
     * invited; with neither, the whole church (`TODOS`).
     */
    fun convocatoriaTargets(ministryId: String?, participantIds: List<String>): List<Target> {
        val targets = buildList {
            if (ministryId != null) add(Target("MINISTERIO", ministryId, null))
            participantIds.distinct().forEach { add(Target("USUARIO", null, it)) }
        }
        return targets.ifEmpty { listOf(Target("TODOS", null, null)) }
    }

    fun isConvoked(targets: List<Target>, userId: String, userMinistryIds: List<String>): Boolean =
        targets.any {
            when (it.alcance) {
                "TODOS" -> true
                "MINISTERIO" -> it.ministryId in userMinistryIds
                "USUARIO" -> it.userId == userId
                else -> false
            }
        }

    /** `actividad.clasificacion` allows only MINISTERIAL, PROYECTO or INDIVIDUAL. */
    fun meetingClassification(ministryId: String?): Classification =
        if (ministryId != null) Classification.Ministry else Classification.Individual

    data class CultoDetail(val preacherId: String?, val worshipLeaderId: String?)

    /** `detalle_culto` is written only when a preacher or worship director was chosen. */
    fun cultoDetail(preacherId: String?, worshipLeaderId: String?): CultoDetail? =
        if (preacherId == null && worshipLeaderId == null) null else CultoDetail(preacherId, worshipLeaderId)
}
