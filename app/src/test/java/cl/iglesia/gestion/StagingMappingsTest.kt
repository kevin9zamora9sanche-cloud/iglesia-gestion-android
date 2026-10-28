package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StagingMappingsTest {

    @Test fun roleToStaging() {
        assertEquals("PASTOR_ADMIN", StagingMappings.roleToStaging(Roles.PASTOR))
        assertEquals("LIDER_MINISTERIO", StagingMappings.roleToStaging(Roles.MINISTRY_LEADER))
        assertEquals("LIDER_NORMAL", StagingMappings.roleToStaging(Roles.LEADER))
        assertEquals("LIDER_NORMAL", StagingMappings.roleToStaging("otro"))
    }

    @Test fun roleFromStaging() {
        assertEquals(Roles.PASTOR, StagingMappings.roleFromStaging("PASTOR_ADMIN"))
        assertEquals(Roles.MINISTRY_LEADER, StagingMappings.roleFromStaging("LIDER_MINISTERIO"))
        assertEquals(Roles.LEADER, StagingMappings.roleFromStaging("LIDER_NORMAL"))
        assertEquals(Roles.LEADER, StagingMappings.roleFromStaging("DESCONOCIDO"))
        assertEquals(Roles.LEADER, StagingMappings.roleFromStaging(null))
    }

    @Test fun statusRoundTrip() {
        val pairs = mapOf(
            "pending" to "POR_HACER",
            "in_progress" to "EN_CURSO",
            "paused" to "EN_PAUSA",
            "completed" to "COMPLETADA",
        )
        for ((app, db) in pairs) {
            assertEquals(db, StagingMappings.statusToStaging(app))
            assertEquals(app, StagingMappings.statusFromStaging(db))
        }
    }

    @Test fun statusFallbacks() {
        assertEquals("EN_PAUSA", StagingMappings.statusToStaging("cancelled"))
        assertEquals("POR_HACER", StagingMappings.statusToStaging("???"))
        assertEquals("pending", StagingMappings.statusFromStaging("???"))
        assertEquals("pending", StagingMappings.statusFromStaging(null))
    }

    @Test fun activityTypeRoundTrip() {
        val pairs = mapOf(
            ActivityTypes.CULTO to "CULTO",
            ActivityTypes.ENSAYO to "ENSAYO",
            ActivityTypes.ASEO to "ASEO",
            ActivityTypes.LEADERS_MEETING to "REUNION_LIDERES",
            ActivityTypes.LIBRE to "LIBRE",
        )
        for ((app, db) in pairs) {
            assertEquals(db, StagingMappings.activityTypeToStaging(app))
            assertEquals(app, StagingMappings.activityTypeFromStaging(db))
        }
        assertEquals("LIBRE", StagingMappings.activityTypeToStaging("raro"))
        assertEquals(ActivityTypes.LIBRE, StagingMappings.activityTypeFromStaging("raro"))
    }

    @Test fun classification() {
        assertEquals("MINISTERIAL", StagingMappings.classificationToStaging(Classification.Ministry))
        assertEquals("PROYECTO", StagingMappings.classificationToStaging(Classification.Project))
        assertEquals("INDIVIDUAL", StagingMappings.classificationToStaging(Classification.General))
        assertEquals("INDIVIDUAL", StagingMappings.classificationToStaging(Classification.Individual))
        assertEquals(Classification.Ministry, StagingMappings.classificationFromStaging("MINISTERIAL"))
        assertEquals(Classification.Project, StagingMappings.classificationFromStaging("PROYECTO"))
        assertEquals(Classification.Individual, StagingMappings.classificationFromStaging("INDIVIDUAL"))
        assertEquals(Classification.General, StagingMappings.classificationFromStaging("x"))
    }

    @Test fun priority() {
        assertEquals("ALTA", StagingMappings.priorityToStaging("high"))
        assertEquals("MEDIA", StagingMappings.priorityToStaging("medium"))
        assertEquals("BAJA", StagingMappings.priorityToStaging("low"))
        assertEquals("MEDIA", StagingMappings.priorityToStaging(null))
        assertEquals("MEDIA", StagingMappings.priorityToStaging("x"))
        assertEquals("high", StagingMappings.priorityFromStaging("ALTA"))
        assertEquals("medium", StagingMappings.priorityFromStaging("MEDIA"))
        assertEquals("low", StagingMappings.priorityFromStaging("BAJA"))
        assertEquals("medium", StagingMappings.priorityFromStaging(null))
    }

    @Test fun visibility() {
        assertEquals("TODOS", StagingMappings.visibilityToStaging(RecordVisibility.Everyone))
        assertEquals("MINISTERIO", StagingMappings.visibilityToStaging(RecordVisibility.Ministry))
        assertEquals("USUARIO", StagingMappings.visibilityToStaging(RecordVisibility.Selected))
        assertEquals(RecordVisibility.Everyone, StagingMappings.visibilityFromStaging("TODOS"))
        assertEquals(RecordVisibility.Ministry, StagingMappings.visibilityFromStaging("MINISTERIO"))
        assertEquals(RecordVisibility.Selected, StagingMappings.visibilityFromStaging("USUARIO"))
        // Least exposure for unknown values.
        assertEquals(RecordVisibility.Selected, StagingMappings.visibilityFromStaging("x"))
    }

    @Test fun fileType() {
        assertEquals("PDF", StagingMappings.fileTypeFromMime("application/pdf"))
        assertEquals("JPG", StagingMappings.fileTypeFromMime("image/jpeg"))
        assertEquals("PNG", StagingMappings.fileTypeFromMime("image/png"))
        assertEquals("PDF", StagingMappings.fileTypeFromMime("Application/PDF"))
        assertNull(StagingMappings.fileTypeFromMime("image/gif"))
        assertNull(StagingMappings.fileTypeFromMime(null))
        assertEquals("PDF", StagingMappings.fileTypeFromName("a.pdf"))
        assertEquals("JPG", StagingMappings.fileTypeFromName("a.JPG"))
        assertEquals("JPG", StagingMappings.fileTypeFromName("a.jpeg"))
        assertEquals("PNG", StagingMappings.fileTypeFromName("a.png"))
        assertNull(StagingMappings.fileTypeFromName("a.exe"))
        assertNull(StagingMappings.fileTypeFromName("pdf"))
    }

    @Test fun fileSize() {
        assertEquals(5_242_880L, StagingMappings.MAX_FILE_BYTES)
        assertTrue(StagingMappings.isFileSizeAllowed(1))
        assertTrue(StagingMappings.isFileSizeAllowed(5_242_880))
        assertFalse(StagingMappings.isFileSizeAllowed(5_242_881))
        assertFalse(StagingMappings.isFileSizeAllowed(0))
        assertFalse(StagingMappings.isFileSizeAllowed(-1))
    }

    @Test fun storagePath() {
        assertEquals("cancion/abc/partitura.pdf", StagingMappings.storagePath("cancion", "abc", "partitura.pdf"))
        assertEquals("reunion/1/a.png", StagingMappings.storagePath("reunion", "1", "a.png"))
        assertEquals("actividad/1/a.png", StagingMappings.storagePath("actividad", "1", "a.png"))
        assertEquals("ministerio/1/a.png", StagingMappings.storagePath("ministerio", "1", "a.png"))
    }

    @Test fun storagePathSanitizesName() {
        assertEquals("cancion/1/a_b.pdf", StagingMappings.storagePath("cancion", "1", "a/b.pdf"))
        assertEquals("cancion/1/a_b.pdf", StagingMappings.storagePath("cancion", "1", "a\\b.pdf"))
        assertEquals("cancion/1/x.pdf", StagingMappings.storagePath("cancion", "1", "  x.pdf  "))
    }

    @Test fun storagePathRejectsInvalid() {
        assertNull(StagingMappings.storagePathOrNull("otro", "1", "a.pdf"))
        assertNull(StagingMappings.storagePathOrNull("cancion", "", "a.pdf"))
        assertNull(StagingMappings.storagePathOrNull("cancion", "a/b", "a.pdf"))
        assertNull(StagingMappings.storagePathOrNull("cancion", "1", "   "))
        assertNull(StagingMappings.storagePathOrNull("cancion", "1", ".."))
        assertNull(StagingMappings.storagePathOrNull("cancion", "1", "/"))
        try {
            StagingMappings.storagePath("otro", "1", "a.pdf")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun userStatus() {
        assertEquals("ACTIVO", StagingMappings.userStatusToStaging(true))
        assertEquals("INACTIVO", StagingMappings.userStatusToStaging(false))
        assertTrue(StagingMappings.isActiveFromStaging("ACTIVO"))
        assertFalse(StagingMappings.isActiveFromStaging("INACTIVO"))
        assertFalse(StagingMappings.isActiveFromStaging(null))
    }

    @Test fun fullName() {
        assertEquals("Ana María" to "Pérez Soto", StagingMappings.splitFullName("Ana María Pérez Soto"))
        assertEquals("Ana" to "Pérez", StagingMappings.splitFullName("Ana Pérez"))
        assertEquals("Ana" to "", StagingMappings.splitFullName("  Ana "))
        assertEquals("" to "", StagingMappings.splitFullName("   "))
        assertEquals("Ana María Pérez Soto", StagingMappings.joinFullName("Ana María", "Pérez Soto"))
        assertEquals("Ana", StagingMappings.joinFullName("Ana", ""))
        assertEquals("Pérez", StagingMappings.joinFullName(null, "Pérez"))
    }
}
