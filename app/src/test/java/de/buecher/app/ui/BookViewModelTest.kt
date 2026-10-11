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
}
