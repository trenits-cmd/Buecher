package de.buecher.app.data

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.TimeUnit

class BookRepository(context: Context) {
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val github = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GitHubApi::class.java)
    private val googleBooks = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleBooksApi::class.java)
    private val openLibrary = Retrofit.Builder()
        .baseUrl("https://openlibrary.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenLibraryApi::class.java)
    private val preferences = context.getSharedPreferences("buecher_settings", Context.MODE_PRIVATE)

    fun savedConfig(): GitHubConfig = GitHubConfig(
        owner = preferences.getString("owner", "").orEmpty(),
        repository = preferences.getString("repository", "").orEmpty(),
        branch = preferences.getString("branch", "main").orEmpty(),
    )

    fun saveConfig(config: GitHubConfig) {
        preferences.edit()
            .putString("owner", config.owner.trim())
            .putString("repository", config.repository.trim())
            .putString("branch", config.branch.trim().ifBlank { "main" })
            .apply()
    }

    fun savedYearlyGoal(): Int = preferences.getInt("yearly_goal", 12)

    fun saveYearlyGoal(goal: Int) {
        preferences.edit().putInt("yearly_goal", goal.coerceAtLeast(1)).apply()
    }

    suspend fun loadBooks(config: GitHubConfig, token: String): List<Book> {
        if (config.owner.isBlank() || config.repository.isBlank() || token.isBlank()) {
            return loadLocalBooks()
        }
        return try {
            val remote = github.getFile(config.owner, config.repository, FILE_PATH, authorization(token))
            val books = decode(remote.content)
            saveLocalBooks(books)
            books
        } catch (_: Exception) {
            loadLocalBooks()
        }
    }

    suspend fun saveBooks(config: GitHubConfig, token: String, books: List<Book>) {
        saveLocalBooks(books)

        if (config.owner.isBlank() || config.repository.isBlank() || token.isBlank()) {
            return
        }

        val authorization = authorization(token)
        val sha = try {
            github.getFile(config.owner, config.repository, FILE_PATH, authorization).sha
        } catch (error: HttpException) {
            if (error.code() == 404) null else throw error
        }
        val json = gson.toJson(books)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        github.putFile(
            owner = config.owner,
            repository = config.repository,
            path = FILE_PATH,
            authorization = authorization,
            request = GitHubPutRequest(
                message = "Bücherregal aktualisieren",
                content = encoded,
                sha = sha,
                branch = config.branch,
            ),
        )
    }

    private fun loadLocalBooks(): List<Book> {
        val json = preferences.getString("local_books_cache", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Book>>() {}.type
            gson.fromJson<List<Book>>(json, type).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocalBooks(books: List<Book>) {
        try {
            val json = gson.toJson(books)
            preferences.edit().putString("local_books_cache", json).apply()
        } catch (e: Exception) {
        }
    }

    suspend fun findBooks(input: String): List<GoogleBook> = withContext(Dispatchers.IO) {
        val query = input.trim()
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<GoogleBook>()

        // 1. URL handling
        if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) || query.startsWith("www.", ignoreCase = true)) {
            val urlString = if (query.startsWith("www.", ignoreCase = true)) "https://$query" else query
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val html = response.body?.string().orEmpty()
                    val isbnOrAsin = extractIsbnOrAsin(finalUrl) ?: extractIsbnOrAsin(html)
                    if (isbnOrAsin != null) {
                        searchOpenLibraryByIsbn(isbnOrAsin)?.let { results.add(it) }
                        results.addAll(searchGoogleBooksList("isbn:$isbnOrAsin"))
                    }
                    val ogTitle = extractMetaTag(html, "og:title")
                    if (!ogTitle.isNullOrBlank()) {
                        val cleanTitle = ogTitle.substringBefore(" - ").substringBefore(" | ").trim()
                        val cover = extractMetaTag(html, "og:image")?.replace("http://", "https://").orEmpty()
                        val list = searchGoogleBooksList(cleanTitle)
                        if (list.isNotEmpty()) {
                            results.addAll(list.map { if (it.coverImageUrl.isBlank()) it.copy(coverImageUrl = cover) else it })
                        } else {
                            results.add(GoogleBook(cleanTitle, extractAuthorFromDescription(extractMetaTag(html, "og:description")).orEmpty(), 0, "", cover))
                        }
                    }
                    val slugQuery = extractSlugQuery(finalUrl)
                    if (!slugQuery.isNullOrBlank()) {
                        results.addAll(searchGoogleBooksList(slugQuery))
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. ISBN lookup
        val cleanIsbn = cleanIsbn(query)
        if (cleanIsbn != null) {
            searchOpenLibraryByIsbn(cleanIsbn)?.let { results.add(it) }
            results.addAll(searchGoogleBooksList("isbn:$cleanIsbn"))
        }

        // 3. Open Library search
        try {
            val olSearch = openLibrary.search(query, limit = 5)
            olSearch.docs.orEmpty().forEach { doc ->
                val title = doc.title?.takeIf(String::isNotBlank) ?: return@forEach
                val author = doc.author_name.orEmpty().joinToString(", ")
                var pageCount = doc.number_of_pages_median ?: 0
                val language = doc.language?.firstOrNull().orEmpty()
                val coverIsbn = doc.isbn?.firstOrNull()
                val cover = if (doc.cover_i != null && doc.cover_i > 0) {
                    "https://covers.openlibrary.org/b/id/${doc.cover_i}-L.jpg"
                } else if (!coverIsbn.isNullOrBlank()) {
                    "https://covers.openlibrary.org/b/isbn/$coverIsbn-L.jpg"
                } else {
                    ""
                }
                if (pageCount <= 0) {
                    val gbMatch = searchGoogleBooksList("$title $author").firstOrNull { it.pageCount > 0 }
                    if (gbMatch != null && gbMatch.pageCount > 0) {
                        pageCount = gbMatch.pageCount
                    }
                }
                results.add(GoogleBook(title, author, pageCount, language, cover))
            }
        } catch (_: Exception) {}

        // 4. Google Books search list
        val amazonId = extractAmazonProductId(query)
        val searches = when {
            amazonId != null -> listOf("isbn:$amazonId", amazonId)
            query.matches(Regex("[0-9Xx-]{10,17}")) -> listOf("isbn:${query.replace("-", "")}")
            else -> listOf(query, "intitle:$query")
        }

        for (search in searches) {
            if (search.startsWith("isbn:")) {
                val isbnPart = search.removePrefix("isbn:")
                searchOpenLibraryByIsbn(isbnPart)?.let { results.add(it) }
            }
            results.addAll(searchGoogleBooksList(search))
        }

        return@withContext results
            .distinctBy { "${it.title.lowercase()}-${it.author.lowercase()}" }
            .map { book ->
                if (book.pageCount <= 0) {
                    val found = searchGoogleBooksList(book.title).firstOrNull { it.pageCount > 0 }
                    if (found != null) book.copy(pageCount = found.pageCount, language = book.language.ifBlank { found.language }, coverImageUrl = book.coverImageUrl.ifBlank { found.coverImageUrl }) else book
                } else {
                    book
                }
            }
    }

    private suspend fun searchOpenLibraryByIsbn(isbn: String): GoogleBook? {
        return try {
            val response = openLibrary.getBookByIsbn("ISBN:$isbn")
            val book = response["ISBN:$isbn"] ?: response["ISBN:${isbn.replace("-", "")}"] ?: return null
            val title = book.title?.takeIf(String::isNotBlank) ?: return null
            val author = book.authors.orEmpty().mapNotNull { it.name }.joinToString(", ")
            var pageCount = book.number_of_pages ?: 0
            val rawLang = book.languages?.firstOrNull()?.key ?: ""
            val language = normalizeLanguage(rawLang)
            val cover = book.cover?.large ?: book.cover?.medium ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
            if (pageCount <= 0) {
                val gbMatch = searchGoogleBooksList("$title $author").firstOrNull { it.pageCount > 0 }
                if (gbMatch != null && gbMatch.pageCount > 0) {
                    pageCount = gbMatch.pageCount
                }
            }
            GoogleBook(
                title = title,
                author = author,
                pageCount = pageCount,
                language = language,
                coverImageUrl = cover,
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun searchGoogleBooksList(query: String): List<GoogleBook> {
        return try {
            val items = googleBooks.search(query).items ?: return emptyList()
            items.mapNotNull { item ->
                val volume = item.volumeInfo ?: return@mapNotNull null
                val title = volume.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val cover = (volume.imageLinks?.thumbnail ?: volume.imageLinks?.smallThumbnail)
                    ?.replace("http://", "https://")
                    .orEmpty()
                GoogleBook(
                    title = title,
                    author = volume.authors.orEmpty().joinToString(", "),
                    pageCount = volume.pageCount ?: 0,
                    language = normalizeLanguage(volume.language),
                    coverImageUrl = cover,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun cleanIsbn(input: String): String? {
        val digits = input.replace(Regex("[^0-9Xx]"), "")
        return if (digits.length == 10 || digits.length == 13) digits else null
    }

    private fun decode(content: String?): List<Book> {
        if (content.isNullOrBlank()) return emptyList()
        val json = Base64.decode(content, Base64.DEFAULT).toString(Charsets.UTF_8)
        val type = object : TypeToken<List<Book>>() {}.type
        val books = gson.fromJson<List<Book>>(json, type).orEmpty()
        return books.map { it.copy(language = normalizeLanguage(it.language)) }
    }

    private fun authorization(token: String) = "Bearer ${token.trim()}"

    private companion object {
        const val FILE_PATH = "buecher.json"
    }
}

internal fun extractAmazonProductId(input: String): String? = AMAZON_PRODUCT_ID_REGEX.find(input)?.groupValues?.get(1)

private val AMAZON_PRODUCT_ID_REGEX = Regex(
    "(?:https?://)?(?:www\\.)?(?:amzn\\.[a-z.]+|amazon\\.[a-z.]+)/(?:dp|gp/product|d|gp/aw/d)/([A-Z0-9]{8,10})(?:[/?]|$)",
    RegexOption.IGNORE_CASE,
)

private fun extractIsbnOrAsin(text: String): String? {
    val isbnRegex = Regex("(?:isbn[-:]?\\s*)?([0-9]{9}[0-9Xx]|[0-9]{13})", RegexOption.IGNORE_CASE)
    isbnRegex.find(text)?.groupValues?.get(1)?.let { return it }
    return extractAmazonProductId(text)
}

private fun extractMetaTag(html: String, property: String): String? {
    val patterns = listOf(
        Regex("<meta[^>]+(?:property|name)=[\"']$property[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']$property[\"']", RegexOption.IGNORE_CASE)
    )
    for (pattern in patterns) {
        pattern.find(html)?.groupValues?.get(1)?.let {
            return it.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
    }
    return null
}

private fun extractSlugQuery(url: String): String? {
    try {
        val uri = URI(url)
        val path = uri.path ?: return null
        val segments = path.split("/").filter { it.isNotBlank() }
        val slug = segments.maxByOrNull { it.length } ?: return null
        if (slug.length > 3 && (slug.contains("-") || slug.contains("_"))) {
            return slug.replace("-", " ").replace("_", " ").trim()
        }
    } catch (e: Exception) {
    }
    return null
}

private fun extractAuthorFromDescription(desc: String?): String? {
    if (desc.isNullOrBlank()) return null
    val authorRegex = Regex("(?:von|by|author:?)\\s+([A-ZÄÖÜ][a-zäöüß]+(?:\\s+[A-ZÄÖÜ][a-zäöüß]+)+)", RegexOption.IGNORE_CASE)
    return authorRegex.find(desc)?.groupValues?.get(1)
}

fun normalizeLanguage(lang: String?): String {
    val l = lang?.lowercase().orEmpty()
    return when {
        l.contains("en") || l.contains("eng") || l.contains("english") -> "Englisch"
        l.contains("fr") || l.contains("fra") || l.contains("french") -> "Französisch"
        else -> "Deutsch"
    }
}
