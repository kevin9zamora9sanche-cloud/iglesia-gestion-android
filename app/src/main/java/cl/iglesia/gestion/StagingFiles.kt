package cl.iglesia.gestion

/**
 * Pure helpers for rows of staging `archivo` and objects of bucket `archivos`
 * (path `<owner_type>/<owner_id>/<file name>`, PDF/JPG/PNG, at most 5 MB).
 */
object StagingFiles {
    const val BUCKET = "archivos"
    private const val MAX_STORAGE_NAME = 100

    /** Everything needed to upload an object and insert its `archivo` row. */
    class Upload(val path: String, val name: String, val type: String, val sizeBytes: Int)

    /**
     * Validates an upload and builds its path and `archivo` values. [uniqueId] keeps
     * `ruta` unique when the same file name is uploaded twice. Messages are shown to the user.
     */
    fun prepareUpload(
        ownerType: String,
        ownerId: String,
        fileName: String,
        mime: String?,
        sizeBytes: Long,
        uniqueId: String,
    ): Upload {
        val type = StagingMappings.fileTypeFromMime(mime) ?: StagingMappings.fileTypeFromName(fileName)
            ?: throw UserFacingException("Solo se permiten archivos PDF, JPG o PNG.")
        if (!StagingMappings.isFileSizeAllowed(sizeBytes)) {
            throw UserFacingException("Cada archivo debe pesar como máximo 5 MB.")
        }
        // Storage infers the content type from the extension, so the key must end with the real one.
        val safeName = storageSafeName(fileName).let {
            if (StagingMappings.fileTypeFromName(it) == type) it else "$it.${type.lowercase()}"
        }
        val path = StagingMappings.storagePathOrNull(ownerType, ownerId, "${uniqueId}_$safeName")
            ?: throw UserFacingException("No se pudo determinar el propietario del archivo.")
        return Upload(path = path, name = fileName.trim().ifBlank { "archivo" }, type = type, sizeBytes = sizeBytes.toInt())
    }

    /** ASCII-only storage key part: other characters become `_`; long names keep their end (extension). */
    private fun storageSafeName(fileName: String): String {
        val safe = fileName.trim().map { if (it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '-') it else '_' }
            .joinToString("")
            .ifBlank { "archivo" }
        return if (safe.length > MAX_STORAGE_NAME - 40) safe.takeLast(MAX_STORAGE_NAME - 40) else safe
    }

    /** Content type for display; unknown `tipo` falls back to a generic binary type. */
    fun mimeFromType(type: String?): String = when (type?.uppercase()) {
        "PDF" -> "application/pdf"
        "JPG", "JPEG" -> "image/jpeg"
        "PNG" -> "image/png"
        else -> "application/octet-stream"
    }
}
