package cl.iglesia.gestion

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingRecordsTest {
    private val bogota = ZoneId.of("America/Bogota")

    @Test fun everyoneBecomesSingleTodosRow() {
        val rows = StagingRecords.visibilityRows(RecordVisibility.Everyone, "m1", listOf("u1"))
        assertEquals(listOf(StagingRecords.VisibilityRow("TODOS", null, null)), rows)
    }

    @Test fun ministryBecomesSingleMinisterioRow() {
        val rows = StagingRecords.visibilityRows(RecordVisibility.Ministry, "m1", listOf("u1"))
        assertEquals(listOf(StagingRecords.VisibilityRow("MINISTERIO", "m1", null)), rows)
    }

    @Test fun ministryWithoutIdProducesNoRowsLeastExposure() {
        assertTrue(StagingRecords.visibilityRows(RecordVisibility.Ministry, null, emptyList()).isEmpty())
    }

    @Test fun selectedBecomesOneUsuarioRowPerDistinctPerson() {
        val rows = StagingRecords.visibilityRows(RecordVisibility.Selected, "m1", listOf("u1", "u2", "u1"))
        assertEquals(
            listOf(StagingRecords.VisibilityRow("USUARIO", null, "u1"), StagingRecords.VisibilityRow("USUARIO", null, "u2")),
            rows,
        )
    }

    @Test fun rowsResolveBackToBroadestVisibility() {
        val everyone = StagingRecords.resolveVisibility(
            listOf(StagingRecords.VisibilityRow("USUARIO", null, "u1"), StagingRecords.VisibilityRow("TODOS", null, null)),
        )
        assertEquals(RecordVisibility.Everyone, everyone.visibility)

        val ministry = StagingRecords.resolveVisibility(
            listOf(StagingRecords.VisibilityRow("USUARIO", null, "u1"), StagingRecords.VisibilityRow("MINISTERIO", "m9", null)),
        )
        assertEquals(RecordVisibility.Ministry, ministry.visibility)
        assertEquals("m9", ministry.ministryId)
    }

    @Test fun usuarioRowsResolveToSelectedWithViewers() {
        val resolved = StagingRecords.resolveVisibility(
            listOf(StagingRecords.VisibilityRow("USUARIO", null, "u1"), StagingRecords.VisibilityRow("USUARIO", null, "u2")),
        )
        assertEquals(RecordVisibility.Selected, resolved.visibility)
        assertEquals(listOf("u1", "u2"), resolved.viewerIds)
    }

    @Test fun noRowsResolveToSelectedWithoutViewers() {
        val resolved = StagingRecords.resolveVisibility(emptyList())
        assertEquals(RecordVisibility.Selected, resolved.visibility)
        assertTrue(resolved.viewerIds.isEmpty())
    }

    @Test fun recordDateIsStoredAsLocalStartOfDayAndReadBack() {
        val timestamp = StagingRecords.dateToTimestamp(LocalDate.of(2026, 10, 25), bogota)
        assertEquals("2026-10-25T00:00-05:00", timestamp.toString())
        assertEquals(LocalDate.of(2026, 10, 25), StagingRecords.timestampToDate(timestamp, bogota))
    }
}
