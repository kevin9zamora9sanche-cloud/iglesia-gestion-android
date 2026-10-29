package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingAccountsTest {

    @Test fun activeAccountHasNoAccessProblem() {
        assertNull(StagingAccounts.accessProblem("ACTIVO"))
    }

    @Test fun inactiveOrUnknownAccountIsBlocked() {
        val message = "Esta cuenta está inactiva. Contacta al Pastor/Administrador."
        assertEquals(message, StagingAccounts.accessProblem("INACTIVO"))
        assertEquals(message, StagingAccounts.accessProblem(null))
        assertEquals(message, StagingAccounts.accessProblem("otro"))
    }

    @Test fun createUserRequiresGivenAndFamilyNames() {
        assertNull(StagingAccounts.createUserProblem("Ana Pérez", "ana@iglesia.cl"))
        assertNotNull(StagingAccounts.createUserProblem("Ana", "ana@iglesia.cl"))
        assertNotNull(StagingAccounts.createUserProblem("   ", "ana@iglesia.cl"))
    }

    @Test fun createUserRejectsOversizedFields() {
        val long = "a".repeat(101)
        assertNotNull(StagingAccounts.createUserProblem("$long Pérez", "ana@iglesia.cl"))
        assertNotNull(StagingAccounts.createUserProblem("Ana $long", "ana@iglesia.cl"))
        val email = "a".repeat(250) + "@b.cl"
        assertTrue(email.length > 254)
        assertNotNull(StagingAccounts.createUserProblem("Ana Pérez", email))
    }

    @Test fun createUserErrorMessages() {
        assertEquals("Ya existe un usuario con ese correo.", StagingAccounts.createUserErrorMessage(409, "x"))
        assertEquals("Solo el Pastor/Administrador puede crear cuentas.", StagingAccounts.createUserErrorMessage(403, null))
        assertEquals("Tu sesión venció. Vuelve a ingresar.", StagingAccounts.createUserErrorMessage(401, null))
        assertEquals("Los apellidos son obligatorios", StagingAccounts.createUserErrorMessage(400, "Los apellidos son obligatorios"))
        assertEquals("Datos no válidos para crear la cuenta.", StagingAccounts.createUserErrorMessage(400, null))
        assertEquals("No se pudo crear la cuenta. Inténtalo de nuevo.", StagingAccounts.createUserErrorMessage(500, "No se pudo crear el usuario"))
        assertEquals("No se pudo crear la cuenta. Inténtalo de nuevo.", StagingAccounts.createUserErrorMessage(405, null))
    }
}
