package cl.iglesia.gestion

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingActivitiesTest {
    private val bogota = ZoneId.of("America/Bogota")

    @Test fun combinesDateAndTimeIntoInstantInZone() {
        val inicio = StagingActivities.combine(LocalDate.of(2026, 11, 8), LocalTime.of(19, 30), bogota)
        assertEquals(OffsetDateTime.of(2026, 11, 8, 19, 30, 0, 0, ZoneOffset.ofHours(-5)), inicio)
        assertEquals("2026-11-09T00:30Z", inicio.withOffsetSameInstant(ZoneOffset.UTC).toString())
    }

    @Test fun splitsTimestampBackToLocalDateAndTime() {
        val ts = OffsetDateTime.of(2026, 11, 9, 0, 30, 0, 0, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 11, 8), StagingActivities.localDate(ts, bogota))
        assertEquals("19:30", StagingActivities.timeLabel(ts, bogota))
    }

    @Test fun missingEndHasBlankLabel() {
        assertEquals("", StagingActivities.timeLabel(null, bogota))
    }

    @Test fun convocatoriaWithoutMinistryNorPeopleIsEveryone() {
        assertEquals(
            listOf(StagingActivities.Target("TODOS", null, null)),
            StagingActivities.convocatoriaTargets(null, emptyList()),
        )
    }

    @Test fun convocatoriaCombinesMinistryAndDistinctPeople() {
        val targets = StagingActivities.convocatoriaTargets("m1", listOf("u1", "u2", "u1"))
        assertEquals(
            listOf(
                StagingActivities.Target("MINISTERIO", "m1", null),
                StagingActivities.Target("USUARIO", null, "u1"),
                StagingActivities.Target("USUARIO", null, "u2"),
            ),
            targets,
        )
    }

    @Test fun convokedByEveryoneMinistryOrUser() {
        val rows = listOf(
            StagingActivities.Target("MINISTERIO", "m1", null),
            StagingActivities.Target("USUARIO", null, "u9"),
        )
        assertTrue(StagingActivities.isConvoked(rows, "u1", listOf("m1")))
        assertTrue(StagingActivities.isConvoked(rows, "u9", emptyList()))
        assertFalse(StagingActivities.isConvoked(rows, "u1", listOf("m2")))
        assertTrue(StagingActivities.isConvoked(listOf(StagingActivities.Target("TODOS", null, null)), "u1", emptyList()))
        assertFalse(StagingActivities.isConvoked(emptyList(), "u1", listOf("m1")))
    }

    @Test fun meetingClassificationFollowsMinistry() {
        assertEquals(Classification.Ministry, StagingActivities.meetingClassification("m1"))
        assertEquals(Classification.Individual, StagingActivities.meetingClassification(null))
    }

    @Test fun cultoDetailOnlyWhenSomeoneIsChosen() {
        assertNull(StagingActivities.cultoDetail(null, null))
        assertEquals(StagingActivities.CultoDetail("p", null), StagingActivities.cultoDetail("p", null))
    }
}
