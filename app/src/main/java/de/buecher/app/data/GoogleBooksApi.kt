package de.buecher.app.data

import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApi {
    @GET("books/v1/volumes")
    suspend fun search(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 5,
        @Query("printType") printType: String = "books",
    ): GoogleBooksResponse
}

data class GoogleBooksResponse(val items: List<GoogleBooksItem>?)

data class GoogleBooksItem(val volumeInfo: GoogleBooksVolumeInfo?)

data class GoogleBooksVolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val pageCount: Int?,
    val language: String?,
    val imageLinks: GoogleBooksImageLinks?,
)

data class GoogleBooksImageLinks(val thumbnail: String?, val smallThumbnail: String?)