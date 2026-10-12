package de.buecher.app.data

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenLibraryApi {
    @GET("api/books")
    suspend fun getBookByIsbn(
        @Query("bibkeys") bibKeys: String,
        @Query("format") format: String = "json",
        @Query("jscmd") jscmd: String = "data",
    ): Map<String, OpenLibraryBook?>

    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
    ): OpenLibrarySearchResponse
}

data class OpenLibraryBook(
    val title: String?,
    val authors: List<OpenLibraryAuthor>?,
    val number_of_pages: Int?,
    val languages: List<OpenLibraryLanguage>?,
    val cover: OpenLibraryCover?,
)

data class OpenLibraryAuthor(val name: String?)
data class OpenLibraryLanguage(val key: String?, val name: String?)
data class OpenLibraryCover(val small: String?, val medium: String?, val large: String?)

data class OpenLibrarySearchResponse(
    val docs: List<OpenLibrarySearchDoc>?,
)

data class OpenLibrarySearchDoc(
    val title: String?,
    val author_name: List<String>?,
    val number_of_pages_median: Int?,
    val language: List<String>?,
    val isbn: List<String>?,
    val cover_i: Int?,
)
