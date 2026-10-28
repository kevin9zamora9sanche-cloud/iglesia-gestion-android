package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectProgressTest {

    @Test fun projectWithoutActivitiesHasZeroProgress() {
        assertEquals(0, ProjectProgress.percent(emptyList()))
    }

    @Test fun activityWithoutTasksCountsOnlyWhenCompleted() {
        val done = ProjectProgress.Item("completed", emptyList())
        val open = ProjectProgress.Item("in_progress", emptyList())
        assertEquals(50, ProjectProgress.percent(listOf(done, open)))
    }

    @Test fun activityWithTasksUsesCompletedFraction() {
        val item = ProjectProgress.Item("pending", listOf("completed", "pending", "completed", "paused"))
        assertEquals(50, ProjectProgress.percent(listOf(item)))
    }

    @Test fun averagesActivitiesAndRounds() {
        val a = ProjectProgress.Item("pending", listOf("completed", "pending", "pending")) // 33.3
        val b = ProjectProgress.Item("completed", emptyList()) // 100
        assertEquals(67, ProjectProgress.percent(listOf(a, b)))
    }
}
