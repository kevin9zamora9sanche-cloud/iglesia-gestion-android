package cl.iglesia.gestion

/**
 * Pure rules for accounts on Supabase staging (`usuario` + Edge Function `create-user`).
 * No Android or Supabase dependencies, so everything here is unit-testable.
 */
object StagingAccounts {
    private const val MAX_NAME_LENGTH = 100
    private const val MAX_EMAIL_LENGTH = 254

    /**
     * Inactive accounts cannot use the app: RLS denies everything for them.
     * Returns the message to show, or null when the account may continue.
     */
    fun accessProblem(estado: String?): String? =
        if (StagingMappings.isActiveFromStaging(estado)) null
        else "Esta cuenta está inactiva. Contacta al Pastor/Administrador."

    /** Mirrors the `create-user` validation so the pastor gets feedback before the request. */
    fun createUserProblem(fullName: String, email: String): String? {
        val (nombres, apellidos) = StagingMappings.splitFullName(fullName)
        return when {
            nombres.isBlank() || apellidos.isBlank() -> "Escribe nombres y apellidos."
            nombres.length > MAX_NAME_LENGTH || apellidos.length > MAX_NAME_LENGTH -> "El nombre es demasiado largo."
            email.trim().length > MAX_EMAIL_LENGTH -> "El correo es demasiado largo."
            else -> null
        }
    }

    /**
     * Spanish message for a failed `create-user` call. 400 keeps the server text
     * (already Spanish and field-specific); other statuses use fixed copy.
     */
    fun createUserErrorMessage(status: Int, serverError: String?): String = when (status) {
        400 -> serverError?.takeIf(String::isNotBlank) ?: "Datos no válidos para crear la cuenta."
        401 -> "Tu sesión venció. Vuelve a ingresar."
        403 -> "Solo el Pastor/Administrador puede crear cuentas."
        409 -> "Ya existe un usuario con ese correo."
        else -> "No se pudo crear la cuenta. Inténtalo de nuevo."
    }
}
