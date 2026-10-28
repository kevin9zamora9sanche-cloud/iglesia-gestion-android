package cl.iglesia.gestion

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Pure helpers for staging `reunion` and `visibilidad_reunion`. */
object StagingRecords {

    /** One `visibilidad_reunion` row: [alcance] is TODOS, MINISTERIO or USUARIO. */
    data class VisibilityRow(val alcance: String, val ministryId: String?, val userId: String?)

    /**
     * Rows for a visibility choice (one row per person for USUARIO). A ministry choice
     * without a ministry yields no rows, so only the Pastor can see the record.
     */
    fun visibilityRows(visibility: RecordVisibility, ministryId: String?, viewerIds: List<String>): List<VisibilityRow> =
        when (visibility) {
            RecordVisibility.Everyone -> listOf(VisibilityRow("TODOS", null, null))
            RecordVisibility.Ministry -> listOfNotNull(ministryId?.let { VisibilityRow("MINISTERIO", it, null) })
            RecordVisibility.Selected -> viewerIds.distinct().map { VisibilityRow("USUARIO", null, it) }
        }

    class ResolvedVisibility(val visibility: RecordVisibility, val ministryId: String?, val viewerIds: List<String>)

    /** Broadest scope wins: TODOS, then MINISTERIO, otherwise the listed people (possibly none). */
    fun resolveVisibility(rows: List<VisibilityRow>): ResolvedVisibility {
        if (rows.any { it.alcance == "TODOS" }) return ResolvedVisibility(RecordVisibility.Everyone, null, emptyList())
        rows.firstOrNull { it.alcance == "MINISTERIO" && it.ministryId != null }?.let {
            return ResolvedVisibility(RecordVisibility.Ministry, it.ministryId, emptyList())
        }
        return ResolvedVisibility(
            RecordVisibility.Selected,
            null,
            rows.filter { it.alcance == "USUARIO" }.mapNotNull(VisibilityRow::userId).distinct(),
        )
    }

    /** `reunion.fecha` is a timestamptz: the chosen day at local midnight. */
    fun dateToTimestamp(date: LocalDate, zone: ZoneId): OffsetDateTime =
        date.atStartOfDay(zone).toOffsetDateTime()

    fun timestampToDate(timestamp: OffsetDateTime, zone: ZoneId): LocalDate =
        StagingActivities.localDate(timestamp, zone)
}
