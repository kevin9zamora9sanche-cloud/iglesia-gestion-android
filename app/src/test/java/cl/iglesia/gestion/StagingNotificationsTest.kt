package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StagingNotificationsTest {
    @Test fun assignmentTypesMapToAssignment() {
        assertEquals(NotificationKind.Assignment, StagingNotifications.kindOf("ACTIVIDAD_ASIGNADA"))
        assertEquals(NotificationKind.Assignment, StagingNotifications.kindOf("tarea_asignada"))
    }

    @Test fun scheduleTypesMapToSchedule() {
        assertEquals(NotificationKind.Schedule, StagingNotifications.kindOf("ACTIVIDAD_ACTUALIZADA"))
        assertEquals(NotificationKind.Schedule, StagingNotifications.kindOf("REUNION_CONVOCADA"))
        assertEquals(NotificationKind.Schedule, StagingNotifications.kindOf("RECORDATORIO_ACTIVIDAD"))
    }

    @Test fun unknownOrMissingTypesMapToSystem() {
        assertEquals(NotificationKind.System, StagingNotifications.kindOf("OTRA_COSA"))
        assertEquals(NotificationKind.System, StagingNotifications.kindOf(null))
        assertEquals(NotificationKind.System, StagingNotifications.kindOf(""))
    }

    @Test fun activityAndTaskEntitiesOpenTheAgendaItem() {
        assertEquals("a1", StagingNotifications.targetItemId("actividad", "a1"))
        assertEquals("t1", StagingNotifications.targetItemId("tarea", "t1"))
    }

    @Test fun otherEntitiesOrMissingIdsHaveNoTarget() {
        assertNull(StagingNotifications.targetItemId("proyecto", "p1"))
        assertNull(StagingNotifications.targetItemId("actividad", null))
        assertNull(StagingNotifications.targetItemId(null, "a1"))
        assertNull(StagingNotifications.targetItemId("actividad", " "))
    }
}
