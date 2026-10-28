package cl.iglesia.gestion

/**
 * Pure mappers between the app's domain values and the Supabase staging schema
 * (project `dmdysvqailticvodbmyl`, owned by the CMAA web repo).
 *
 * No Android or Supabase dependencies, so everything here is unit-testable.
 * Unknown inputs always resolve to the least-privileged / least-exposed value.
 */
object StagingMappings {

    // ---- Roles -----------------------------------------------------------

    fun roleToStaging(appRole: String): String = when (appRole) {
        Roles.PASTOR -> "PASTOR_ADMIN"
        Roles.MINISTRY_LEADER -> "LIDER_MINISTERIO"
        else -> "LIDER_NORMAL"
    }

    /** Unknown or missing roles map to [Roles.LEADER] (least privilege). */
    fun roleFromStaging(role: String?): String = when (role) {
        "PASTOR_ADMIN" -> Roles.PASTOR
        "LIDER_MINISTERIO" -> Roles.MINISTRY_LEADER
        else -> Roles.LEADER
    }

    // ---- Status (proyecto / actividad / tarea) ----------------------------

    /**
     * App status values: pending, in_progress, paused, completed.
     * Staging has no `cancelled`: it is stored as `EN_PAUSA` (the item stays
     * open and recoverable instead of being reported as completed). Unknown
     * values fall back to `POR_HACER`.
     */
    fun statusToStaging(appStatus: String): String = when (appStatus) {
        "pending" -> "POR_HACER"
        "in_progress" -> "EN_CURSO"
        "paused", "cancelled" -> "EN_PAUSA"
        "completed" -> "COMPLETADA"
        else -> "POR_HACER"
    }

    /** Unknown or missing values map to `pending`. */
    fun statusFromStaging(status: String?): String = when (status) {
        "EN_CURSO" -> "in_progress"
        "EN_PAUSA" -> "paused"
        "COMPLETADA" -> "completed"
        else -> "pending"
    }

    // ---- Activity type ----------------------------------------------------

    /** Unknown types map to `LIBRE`. */
    fun activityTypeToStaging(appType: String): String = when (appType) {
        ActivityTypes.CULTO -> "CULTO"
        ActivityTypes.ENSAYO -> "ENSAYO"
        ActivityTypes.ASEO -> "ASEO"
        ActivityTypes.LEADERS_MEETING -> "REUNION_LIDERES"
        else -> "LIBRE"
    }

    fun activityTypeFromStaging(type: String?): String = when (type) {
        "CULTO" -> ActivityTypes.CULTO
        "ENSAYO" -> ActivityTypes.ENSAYO
        "ASEO" -> ActivityTypes.ASEO
        "REUNION_LIDERES" -> ActivityTypes.LEADERS_MEETING
        else -> ActivityTypes.LIBRE
    }

    // ---- Classification ---------------------------------------------------

    fun classificationToStaging(classification: Classification): String = when (classification) {
        Classification.Ministry -> "MINISTERIAL"
        Classification.Project, Classification.General -> "PROYECTO"
        Classification.Individual -> "INDIVIDUAL"
    }

    /** `PROYECTO` maps to [Classification.Project]; unknown values to [Classification.General]. */
    fun classificationFromStaging(value: String?): Classification = when (value) {
        "MINISTERIAL" -> Classification.Ministry
        "PROYECTO" -> Classification.Project
        "INDIVIDUAL" -> Classification.Individual
        else -> Classification.General
    }

    // ---- Task priority ----------------------------------------------------

    /** App priorities: high, medium, low. Missing or unknown map to `MEDIA`. */
    fun priorityToStaging(priority: String?): String = when (priority) {
        "high" -> "ALTA"
        "low" -> "BAJA"
        else -> "MEDIA"
    }

    fun priorityFromStaging(priority: String?): String = when (priority) {
        "ALTA" -> "high"
        "BAJA" -> "low"
        else -> "medium"
    }

    // ---- Visibility scope -------------------------------------------------

    fun visibilityToStaging(visibility: RecordVisibility): String = when (visibility) {
        RecordVisibility.Everyone -> "TODOS"
        RecordVisibility.Ministry -> "MINISTERIO"
        RecordVisibility.Selected -> "USUARIO"
    }

    /** Unknown values map to [RecordVisibility.Selected] (least exposure). */
    fun visibilityFromStaging(value: String?): RecordVisibility = when (value) {
        "TODOS" -> RecordVisibility.Everyone
        "MINISTERIO" -> RecordVisibility.Ministry
        else -> RecordVisibility.Selected
    }

    // ---- Files ------------------------------------------------------------

    /** Maximum file size accepted by the `archivos` bucket (5 MiB). */
    const val MAX_FILE_BYTES: Long = 5_242_880L

    fun isFileSizeAllowed(sizeBytes: Long): Boolean = sizeBytes in 1..MAX_FILE_BYTES

    /** Returns PDF, JPG or PNG, or null when the MIME type is not accepted. */
    fun fileTypeFromMime(mime: String?): String? = when (mime?.trim()?.lowercase()) {
        "application/pdf" -> "PDF"
        "image/jpeg" -> "JPG"
        "image/png" -> "PNG"
        else -> null
    }

    /** Returns PDF, JPG or PNG from the file extension, or null when not accepted. */
    fun fileTypeFromName(name: String?): String? {
        val trimmed = name?.trim() ?: return null
        if (!trimmed.contains('.')) return null
        return when (trimmed.substringAfterLast('.').lowercase()) {
            "pdf" -> "PDF"
            "jpg", "jpeg" -> "JPG"
            "png" -> "PNG"
            else -> null
        }
    }

    private val OWNER_TYPES = setOf("cancion", "reunion", "actividad", "ministerio")

    /**
     * Builds `<owner_type>/<owner_id>/<file name>` for bucket `archivos`.
     * Path separators in the file name are replaced with `_`.
     * Returns null for an unknown owner type, an invalid owner id or a blank/dot-only name.
     */
    fun storagePathOrNull(ownerType: String, ownerId: String, fileName: String): String? {
        if (ownerType !in OWNER_TYPES) return null
        if (ownerId.isBlank() || ownerId.any { it == '/' || it == '\\' }) return null
        val safeName = fileName.trim().replace('/', '_').replace('\\', '_')
        if (safeName.isBlank() || safeName.all { it == '.' || it == '_' }) return null
        return "$ownerType/$ownerId/$safeName"
    }

    /** Same as [storagePathOrNull] but throws [IllegalArgumentException] on invalid input. */
    fun storagePath(ownerType: String, ownerId: String, fileName: String): String =
        requireNotNull(storagePathOrNull(ownerType, ownerId, fileName)) {
            "Invalid storage path input: $ownerType/$ownerId/$fileName"
        }

    // ---- Users ------------------------------------------------------------

    fun userStatusToStaging(active: Boolean): String = if (active) "ACTIVO" else "INACTIVO"

    fun isActiveFromStaging(status: String?): Boolean = status == "ACTIVO"

    /**
     * Splits a full name into (`nombres`, `apellidos`). The last two words are
     * the surnames when there are at least three words; with two words the
     * second is the surname; a single word is only a given name.
     */
    fun splitFullName(fullName: String): Pair<String, String> {
        val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "" to ""
        val surnameCount = minOf(2, words.size - 1)
        val given = words.dropLast(surnameCount).joinToString(" ")
        val surnames = words.takeLast(surnameCount).joinToString(" ")
        return given to surnames
    }

    fun joinFullName(nombres: String?, apellidos: String?): String =
        listOfNotNull(nombres?.trim(), apellidos?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
}
