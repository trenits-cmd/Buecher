package de.buecher.app.ui

import de.buecher.app.data.extractAmazonProductId
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BookViewModelTest {
    @Test
    fun googleBooksHttpErrorIsMappedToGoogleBooksService() {
        val exception = HttpException(Response.error<Any>(403, okhttp3.ResponseBody.create(null, "Forbidden")))

        assertEquals("Google Books-Anfrage fehlgeschlagen (HTTP 403)", formatHttpError(exception, "Google Books"))
    }

    @Test
    fun githubHttpErrorIsMappedToGitHubService() {
        val exception = HttpException(Response.error<Any>(401, okhttp3.ResponseBody.create(null, "Unauthorized")))

        assertEquals("GitHub-Zugriff verweigert. Token und Repository-Berechtigungen prüfen.", formatHttpError(exception, "GitHub"))
    }

    @Test
    fun amazonShortLinkIsExtracted() {
        assertEquals("02w5kUeW", extractAmazonProductId("https://amzn.eu/d/02w5kUeW"))
    }

    @Test
    fun amazonProductLinkIsExtracted() {
        assertEquals("1234567890", extractAmazonProductId("https://www.amazon.de/dp/1234567890/ref=sr_1_1"))
    }

    @Test
    fun amazonMobileProductLinkIsExtracted() {
        assertEquals("B08N5WRWNW", extractAmazonProductId("https://www.amazon.de/gp/aw/d/B08N5WRWNW"))
    }

    @Test
    fun csvParsingWorksCorrectly() {
        val csv = "ID,Titel,Autor,Sprache,GesamteSeiten,GeleseneSeiten,Status,Bewertung,CoverURL,AmazonURL,Startdatum,Beendigungsdatum,Beendigungsjahr\n" +
                "\"id-123\",\"Das Parfum\",\"Patrick Süskind\",\"Deutsch\",320,320,\"Gelesen\",5,\"http://cover.jpg\",\"http://amazon.de\",15.02.2025,25.02.2025,2025\n"

        val books = parseCsv(csv)
        assertEquals(1, books.size)
        val book = books.first()
        assertEquals("id-123", book.id)
        assertEquals("Das Parfum", book.title)
        assertEquals("Patrick Süskind", book.author)
        assertEquals("Deutsch", book.language)
        assertEquals(320, book.pageCount)
        assertEquals("Gelesen", book.status)
        assertEquals(5, book.rating)
        assertEquals(2025, book.finishYear)
    }
}
