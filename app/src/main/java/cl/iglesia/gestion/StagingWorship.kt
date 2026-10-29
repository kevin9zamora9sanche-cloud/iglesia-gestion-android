package cl.iglesia.gestion

/** Pure helpers for staging `cancion`, `repertorio` and `repertorio_cancion`. */
object StagingWorship {

    /** One `repertorio_cancion` row. [orden] starts at 1 (check `orden > 0`). */
    data class RepertoireRow(
        val repertoireId: String,
        val songId: String,
        val orden: Int,
        val key: String?,
        val versionUrl: String?,
        val leadId: String?,
    )

    /**
     * Rows for a repertoire in list order. `orden` is renumbered from 1 and a song appears
     * once (unique `repertorio_id, cancion_id`); blank text becomes null.
     */
    fun repertoireRows(repertoireId: String, entries: List<WorshipSetSong>): List<RepertoireRow> =
        entries.distinctBy(WorshipSetSong::songId).mapIndexed { index, entry ->
            RepertoireRow(
                repertoireId = repertoireId,
                songId = entry.songId,
                orden = index + 1,
                key = entry.key.trim().ifBlank { null },
                versionUrl = entry.versionUrl.trim().ifBlank { null },
                leadId = entry.leadId?.trim()?.ifBlank { null },
            )
        }

    /** `cancion` columns: only the title is required. */
    data class SongFields(val title: String, val artist: String?, val key: String?, val versionUrl: String?)

    fun songFields(draft: WorshipSongDraft): SongFields = SongFields(
        title = draft.title.trim(),
        artist = draft.artist.trim().ifBlank { null },
        key = draft.defaultKey.trim().ifBlank { null },
        versionUrl = draft.referenceUrl.trim().ifBlank { null },
    )
}
