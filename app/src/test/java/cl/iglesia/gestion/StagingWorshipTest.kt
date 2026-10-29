package cl.iglesia.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingWorshipTest {
    private fun entry(song: String, position: Int, key: String = "", lead: String? = null, url: String = "") =
        WorshipSetSong(songId = song, position = position, key = key, leadId = lead, versionUrl = url)

    @Test fun ordenIsSequentialFromOneFollowingListOrder() {
        val rows = StagingWorship.repertoireRows("r1", listOf(entry("a", 9), entry("b", 3), entry("c", 1)))
        assertEquals(listOf("a", "b", "c"), rows.map { it.songId })
        assertEquals(listOf(1, 2, 3), rows.map { it.orden })
        assertTrue(rows.all { it.repertoireId == "r1" })
    }

    @Test fun duplicateSongsKeepOnlyTheFirstEntry() {
        val rows = StagingWorship.repertoireRows("r1", listOf(entry("a", 1, "G"), entry("b", 2), entry("a", 3, "D")))
        assertEquals(listOf("a", "b"), rows.map { it.songId })
        assertEquals("G", rows.first().key)
        assertEquals(listOf(1, 2), rows.map { it.orden })
    }

    @Test fun blankValuesBecomeNullAndTextIsTrimmed() {
        val row = StagingWorship.repertoireRows("r1", listOf(entry("a", 1, key = "  ", lead = " ", url = ""))).single()
        assertNull(row.key)
        assertNull(row.leadId)
        assertNull(row.versionUrl)
        val filled = StagingWorship.repertoireRows("r1", listOf(entry("a", 1, key = " Am ", lead = "u1", url = " https://x.y "))).single()
        assertEquals("Am", filled.key)
        assertEquals("u1", filled.leadId)
        assertEquals("https://x.y", filled.versionUrl)
    }

    @Test fun emptyEntriesProduceNoRows() {
        assertTrue(StagingWorship.repertoireRows("r1", emptyList()).isEmpty())
    }

    @Test fun songFieldsBlankToNull() {
        val fields = StagingWorship.songFields(WorshipSongDraft(" Cristo ", " ", "", " https://v.example "))
        assertEquals("Cristo", fields.title)
        assertNull(fields.artist)
        assertNull(fields.key)
        assertEquals("https://v.example", fields.versionUrl)
    }
}
