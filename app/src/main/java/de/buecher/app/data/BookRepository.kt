package de.buecher.app.data

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException

class BookRepository(context: Context) {
    private val gson = Gson()
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

    suspend fun findBook(input: String): GoogleBook? {
        val query = input.trim()
        if (query.isBlank()) return null

        val amazonId = AMAZON_PRODUCT_ID.find(query)?.groupValues?.get(1)
        val searches = when {
            amazonId != null -> listOf("isbn:$amazonId", amazonId)
            query.matches(Regex("[0-9Xx-]{10,17}")) -> listOf("isbn:${query.replace("-", "")}")
            else -> listOf(query)
        }

        for (search in searches) {
            val volume = googleBooks.search(search).items
                ?.firstNotNullOfOrNull { it.volumeInfo }
                ?: continue
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
        return null
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
        val AMAZON_PRODUCT_ID = Regex("/(?:dp|gp/product)/([A-Z0-9]{10})(?:[/?]|$)", RegexOption.IGNORE_CASE)
    }
}