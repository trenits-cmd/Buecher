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

    suspend fun loadBooks(config: GitHubConfig, token: String): List<Book> {
        return try {
            val remote = github.getFile(config.owner, config.repository, FILE_PATH, authorization(token))
            decode(remote.content)
        } catch (error: HttpException) {
            if (error.code() == 404) emptyList() else throw error
        }
    }

    suspend fun saveBooks(config: GitHubConfig, token: String, books: List<Book>) {
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

    suspend fun findBook(input: String): GoogleBook? = withContext(Dispatchers.IO) {
        val query = input.trim()
        if (query.isBlank()) return@withContext null

        // If input is a URL, try to resolve it and extract metadata or identifiers
        if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) || query.startsWith("www.", ignoreCase = true)) {
            val urlString = if (query.startsWith("www.", ignoreCase = true)) "https://$query" else query
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val html = response.body?.string().orEmpty()

                    // 1. Try extracting ISBN or ASIN
                    val isbnOrAsin = extractIsbnOrAsin(finalUrl) ?: extractIsbnOrAsin(html)
                    if (isbnOrAsin != null) {
                        val book = searchGoogleBooks(isbnOrAsin)
                        if (book != null) return@withContext book
                    }

                    // 2. Try Open Graph meta tags (og:title, og:image, etc.)
                    val ogTitle = extractMetaTag(html, "og:title")
                    val ogImage = extractMetaTag(html, "og:image")
                    val ogDesc = extractMetaTag(html, "og:description")

                    if (!ogTitle.isNullOrBlank()) {
                        val cleanTitle = ogTitle.substringBefore(" - ").substringBefore(" | ").trim()
                        val cover = ogImage?.replace("http://", "https://").orEmpty()

                        val googleResult = searchGoogleBooks(cleanTitle)
                        if (googleResult != null) {
                            return@withContext googleResult.copy(
                                coverImageUrl = cover.ifBlank { googleResult.coverImageUrl }
                            )
                        }

                        return@withContext GoogleBook(
                            title = cleanTitle,
                            author = extractAuthorFromDescription(ogDesc).orEmpty(),
                            pageCount = 0,
                            language = "",
                            coverImageUrl = cover,
                        )
                    }

                    // 3. Try slug query from URL path
                    val slugQuery = extractSlugQuery(finalUrl)
                    if (!slugQuery.isNullOrBlank()) {
                        val book = searchGoogleBooks(slugQuery)
                        if (book != null) return@withContext book
                    }
                }
            } catch (e: Exception) {
                // Fallback to standard search if URL fetch fails
            }
        }

        // Standard search logic
        val amazonId = extractAmazonProductId(query)
        val searches = when {
            amazonId != null -> listOf("isbn:$amazonId", amazonId)
            query.matches(Regex("[0-9Xx-]{10,17}")) -> listOf("isbn:${query.replace("-", "")}")
            else -> listOf(query)
        }

        for (search in searches) {
            val book = searchGoogleBooks(search)
            if (book != null) return@withContext book
        }
        return@withContext null
    }

    private suspend fun searchGoogleBooks(query: String): GoogleBook? {
        return try {
            val items = googleBooks.search(query).items ?: return null
            for (item in items) {
                val volume = item.volumeInfo ?: continue
                val title = volume.title?.takeIf(String::isNotBlank) ?: continue
                val cover = (volume.imageLinks?.thumbnail ?: volume.imageLinks?.smallThumbnail)
                    ?.replace("http://", "https://")
                    .orEmpty()
                return GoogleBook(
                    title = title,
                    author = volume.authors.orEmpty().joinToString(", "),
                    pageCount = volume.pageCount ?: 0,
                    language = volume.language.orEmpty(),
                    coverImageUrl = cover,
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(content: String?): List<Book> {
        if (content.isNullOrBlank()) return emptyList()
        val json = Base64.decode(content, Base64.DEFAULT).toString(Charsets.UTF_8)
        val type = object : TypeToken<List<Book>>() {}.type
        return gson.fromJson<List<Book>>(json, type).orEmpty()
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