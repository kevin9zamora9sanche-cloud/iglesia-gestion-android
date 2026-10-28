package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StagingFilesTest {
    private val owner = "11111111-1111-1111-1111-111111111111"

    @Test fun buildsArchivoRowFromUploadMetadata() {
        val upload = StagingFiles.prepareUpload("cancion", owner, "Partitura 1.pdf", "application/pdf", 1234, "abc123")
        assertEquals("cancion/$owner/abc123_Partitura_1.pdf", upload.path)
        assertEquals("Partitura 1.pdf", upload.name)
        assertEquals("PDF", upload.type)
        assertEquals(1234, upload.sizeBytes)
    }

    @Test fun pathHasExactlyTwoFoldersAndNoSeparatorsInName() {
        val upload = StagingFiles.prepareUpload("reunion", owner, "a/b\\c.png", "image/png", 10, "u1")
        assertEquals("reunion/$owner/u1_a_b_c.png", upload.path)
        assertEquals("PNG", upload.type)
    }

    @Test fun accentsAndSpacesAreNormalizedInPathButKeptInName() {
        val upload = StagingFiles.prepareUpload("reunion", owner, "Acta Año 2026.jpg", "image/jpeg", 10, "u1")
        assertEquals("reunion/$owner/u1_Acta_A_o_2026.jpg", upload.path)
        assertEquals("Acta Año 2026.jpg", upload.name)
        assertEquals("JPG", upload.type)
    }

    @Test fun rejectsUnsupportedTypeOversizeAndEmpty() {
        assertFailsWithMessage("PDF, JPG o PNG") { StagingFiles.prepareUpload("cancion", owner, "a.exe", "application/zip", 10, "u") }
        assertFailsWithMessage("5 MB") { StagingFiles.prepareUpload("cancion", owner, "a.pdf", "application/pdf", 5_242_881, "u") }
        assertFailsWithMessage("5 MB") { StagingFiles.prepareUpload("cancion", owner, "a.pdf", "application/pdf", 0, "u") }
    }

    @Test fun fallsBackToExtensionWhenMimeIsGeneric() {
        val upload = StagingFiles.prepareUpload("cancion", owner, "x.PDF", "application/octet-stream", 10, "u")
        assertEquals("PDF", upload.type)
    }

    @Test fun rejectsUnknownOwnerType() {
        assertFailsWithMessage("propietario") { StagingFiles.prepareUpload("usuario", owner, "a.pdf", "application/pdf", 10, "u") }
    }

    @Test fun longNamesAreTruncatedKeepingExtension() {
        val upload = StagingFiles.prepareUpload("cancion", owner, "a".repeat(300) + ".pdf", "application/pdf", 10, "u")
        assertTrue(upload.path.endsWith(".pdf"))
        assertTrue(upload.path.substringAfterLast('/').length <= 100)
    }

    @Test fun keyAlwaysEndsWithTheRealExtension() {
        val upload = StagingFiles.prepareUpload("cancion", owner, "foto", "image/png", 10, "u")
        assertEquals("cancion/$owner/u_foto.png", upload.path)
        assertEquals("foto", upload.name)
    }

    @Test fun mimeFromTypeRoundTrips() {
        assertEquals("application/pdf", StagingFiles.mimeFromType("PDF"))
        assertEquals("image/jpeg", StagingFiles.mimeFromType("JPG"))
        assertEquals("image/jpeg", StagingFiles.mimeFromType("JPEG"))
        assertEquals("image/png", StagingFiles.mimeFromType("PNG"))
        assertEquals("application/octet-stream", StagingFiles.mimeFromType(null))
    }

    private fun assertFailsWithMessage(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("Expected UserFacingException containing '$fragment'")
        } catch (error: UserFacingException) {
            assertTrue(error.message, error.message.orEmpty().contains(fragment))
        }
    }
}
