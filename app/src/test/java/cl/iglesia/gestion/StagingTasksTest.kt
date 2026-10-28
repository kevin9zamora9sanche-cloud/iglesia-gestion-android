package cl.iglesia.gestion

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StagingTasksTest {
    private val bogota = ZoneId.of("America/Bogota")

    @Test fun blankCommentIsRejected() {
        assertNull(StagingTasks.commentOrNull(null))
        assertNull(StagingTasks.commentOrNull("   \n "))
    }

    @Test fun commentIsTrimmed() {
        assertEquals("Listo", StagingTasks.commentOrNull("  Listo \n"))
    }

    @Test fun commentIsCappedAtMaximumLength() {
        val long = "x".repeat(StagingTasks.MAX_COMMENT_LENGTH + 50)
        assertEquals(StagingTasks.MAX_COMMENT_LENGTH, StagingTasks.commentOrNull(long)?.length)
    }

    @Test fun scheduleUsesDueDateWhenPresent() {
        val due = OffsetDateTime.of(2026, 11, 10, 3, 0, 0, 0, ZoneOffset.UTC) // 22:00 on the 9th in Bogota
        val activity = OffsetDateTime.of(2026, 11, 20, 15, 0, 0, 0, ZoneOffset.UTC)
        val schedule = StagingTasks.schedule(due, activity, bogota)
        assertEquals(LocalDate.of(2026, 11, 9), schedule.date)
        assertEquals("22:00", schedule.time)
        assertEquals(true, schedule.hasDueDate)
    }

    @Test fun scheduleFallsBackToActivityStart() {
        val activity = OffsetDateTime.of(2026, 11, 20, 15, 0, 0, 0, ZoneOffset.UTC)
        val schedule = StagingTasks.schedule(null, activity, bogota)
        assertEquals(LocalDate.of(2026, 11, 20), schedule.date)
        assertEquals("10:00", schedule.time)
        assertEquals(false, schedule.hasDueDate)
    }
}
