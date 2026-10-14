package tv.jellyvault.app.catalog.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The language TMDB tests run in, whatever the machine's locale */
internal val FRENCH = TmdbLanguage.of(Locale.FRANCE)

internal val ENGLISH = TmdbLanguage.of(Locale.US)

class TmdbLanguageTest {
    @Test
    fun `main languages keep their main country whatever the region`() {
        assertEquals("fr-FR", TmdbLanguage.of(Locale.forLanguageTag("fr-CA")).tag)
        assertEquals("fr-FR", TmdbLanguage.of(Locale.forLanguageTag("fr")).tag)
        assertEquals("en-US", TmdbLanguage.of(Locale.UK).tag)
        assertEquals("de-DE", TmdbLanguage.of(Locale.forLanguageTag("de-AT")).tag)
        assertEquals("es-ES", TmdbLanguage.of(Locale.forLanguageTag("es")).tag)
    }

    @Test
    fun `other languages use the locale country when there is one`() {
        assertEquals("pt-BR", TmdbLanguage.of(Locale.forLanguageTag("pt-BR")).tag)
        assertEquals("sv", TmdbLanguage.of(Locale.forLanguageTag("sv")).tag)
    }

    @Test
    fun `renamed language codes are sent under their current name`() {
        assertEquals("he-IL", TmdbLanguage.of(Locale.forLanguageTag("he-IL")).tag)
        assertEquals("he", TmdbLanguage.of(Locale("iw")).code)
        assertEquals("id-ID", TmdbLanguage.of(Locale("in", "ID")).tag)
    }

    @Test
    fun `the profile language wins over the system one`() {
        val previous = TmdbLanguage.profileLanguageTag
        try {
            TmdbLanguage.profileLanguageTag = { "de" }
            assertEquals("de-DE", TmdbLanguage.current().tag)
            TmdbLanguage.profileLanguageTag = { "fr-CA" }
            assertEquals("fr-FR", TmdbLanguage.current().tag)
            TmdbLanguage.profileLanguageTag = { null }
            assertEquals(TmdbLanguage.of(Locale.getDefault()), TmdbLanguage.current())
        } finally {
            TmdbLanguage.profileLanguageTag = previous
        }
    }

    @Test
    fun `an empty locale falls back to English`() {
        assertEquals("en", TmdbLanguage.of(Locale.ROOT).code)
    }

    @Test
    fun `trailers and logos prefer the interface language then English`() {
        assertEquals(listOf("de", "en"), TmdbLanguage.of(Locale.GERMANY).mediaLanguages)
        assertEquals(listOf("en"), ENGLISH.mediaLanguages)
    }
}
