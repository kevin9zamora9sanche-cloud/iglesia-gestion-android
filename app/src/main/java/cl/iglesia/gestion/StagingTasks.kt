package cl.iglesia.gestion

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Pure helpers for staging `tarea` and `comentario_tarea`. */
object StagingTasks {
    const val MAX_COMMENT_LENGTH = 2000

    /** Trimmed comment text, or null when blank. Over-long text is cut to [MAX_COMMENT_LENGTH]. */
    fun commentOrNull(text: String?): String? =
        text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_COMMENT_LENGTH)

    /** Where a task shows up in the agenda: its due date, or else the start of its activity. */
    class Schedule(val date: LocalDate, val time: String, val hasDueDate: Boolean)

    fun schedule(dueAt: OffsetDateTime?, activityStart: OffsetDateTime, zone: ZoneId): Schedule {
        val moment = dueAt ?: activityStart
        return Schedule(
            date = StagingActivities.localDate(moment, zone),
            time = StagingActivities.timeLabel(moment, zone),
            hasDueDate = dueAt != null,
        )
    }
}
