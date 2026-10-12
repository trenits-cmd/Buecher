package de.buecher.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.buecher.app.data.Book
import de.buecher.app.data.BookRepository
import de.buecher.app.data.GitHubConfig
import de.buecher.app.data.GoogleBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class ReadingForecast(
    val book: Book,
    val remainingPages: Int,
    val progressPercent: Int,
    val estimatedDaysRemaining: Int?,
    val dailySpeed: Float,
    val startDateFormatted: String,
    val estimatedCompletionDateFormatted: String?,
)

data class LanguageStats(
    val language: String,
    val booksReadCount: Int,
    val pagesReadCount: Int,
)

data class YearlyStats(
    val year: Int,
    val languageBreakdown: Map<String, Int>,
    val totalBooks: Int,
)

data class YearlyGoalProgress(
    val currentYear: Int,
    val booksReadThisYear: Int,
    val yearlyGoal: Int,
    val percent: Int,
)

data class BooksUiState(
    val config: GitHubConfig = GitHubConfig(),
    val token: String = "",
    val yearlyGoal: Int = 12,
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val lookupLoading: Boolean = false,
    val lookupResults: List<GoogleBook> = emptyList(),
    val lookupResult: GoogleBook? = null,
    val lookupError: String? = null,
)

class BookViewModel(private val repository: BookRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        BooksUiState(
            config = repository.savedConfig(),
            yearlyGoal = repository.savedYearlyGoal(),
        ),
    )
    val state: StateFlow<BooksUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null

    init {
        refresh()
    }

    fun configure(config: GitHubConfig, token: String) {
        repository.saveConfig(config)
        _state.update {
            it.copy(
                config = config,
                token = token.trim().ifBlank { it.token },
                error = null,
                notice = "Einstellungen gespeichert",
            )
        }
        refresh()
    }

    fun setYearlyGoal(goal: Int) {
        val newGoal = goal.coerceAtLeast(1)
        repository.saveYearlyGoal(newGoal)
        _state.update { it.copy(yearlyGoal = newGoal, notice = "Leseziel auf $newGoal Bücher gesetzt") }
    }

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, notice = null) }
            try {
                val books = repository.loadBooks(current.config, current.token)
                _state.update { it.copy(books = books, isLoading = false, notice = "${books.size} Bücher geladen") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val localBooks = repository.loadBooks(GitHubConfig(), "")
                _state.update { it.copy(books = localBooks, isLoading = false, error = error.messageForUser("GitHub")) }
            }
        }
    }

    fun save(book: Book) {
        val current = _state.value
        val isCompleted = book.status == "Gelesen" || book.isRead
        val now = System.currentTimeMillis()
        val calculatedFinishDate = if (isCompleted) (book.finishDate ?: now) else null
        val calculatedFinishYear = if (isCompleted) {
            if (calculatedFinishDate != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = calculatedFinishDate }
                cal.get(Calendar.YEAR)
            } else {
                book.finishYear ?: Calendar.getInstance().get(Calendar.YEAR)
            }
        } else {
            book.finishYear
        }

        val preparedBook = book.copy(
            isRead = isCompleted,
            status = if (isCompleted) "Gelesen" else book.status,
            finishYear = calculatedFinishYear,
            finishDate = calculatedFinishDate,
        )
        val updated = current.books.filterNot { it.id == preparedBook.id } + preparedBook
        persist(current, updated, "Buch gespeichert")
    }

    fun delete(book: Book) {
        val current = _state.value
        persist(current, current.books.filterNot { it.id == book.id }, "Buch gelöscht")
    }

    fun lookup(input: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _state.update { it.copy(lookupLoading = true, lookupResults = emptyList(), lookupResult = null, lookupError = null) }
            try {
                val results = repository.findBooks(input)
                val first = results.firstOrNull()
                _state.update {
                    it.copy(
                        lookupLoading = false,
                        lookupResults = results,
                        lookupResult = first,
                        lookupError = if (results.isEmpty()) "Kein passender Eintrag gefunden" else null,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(lookupLoading = false, lookupError = error.messageForUser("Google Books")) }
            }
        }
    }

    fun selectLookupResult(book: GoogleBook) {
        _state.update { it.copy(lookupResult = book) }
    }

    fun clearLookup() {
        lookupJob?.cancel()
        _state.update { it.copy(lookupResults = emptyList(), lookupResult = null, lookupError = null) }
    }

    fun dismissMessages() {
        _state.update { it.copy(error = null, notice = null) }
    }

    // Analytics & Forecast Calculations
    fun getActiveForecasts(books: List<Book>): List<ReadingForecast> {
        val active = books.filter { it.status == "Wird gelesen" || (!it.isRead && it.pagesRead > 0 && it.status != "Pausiert") }
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return active.map { book ->
            val total = if (book.pageCount > 0) book.pageCount else 100
            val read = book.pagesRead.coerceIn(0, total)
            val remaining = max(0, total - read)
            val percent = (read * 100) / total
            val daysElapsed = max(1L, (now - book.startDate) / (1000 * 60 * 60 * 24))
            val speed = read.toFloat() / daysElapsed.toFloat()
            val daysRemaining = if (speed > 0f) ceil(remaining / speed).toInt() else null

            val startDateStr = dateFormat.format(Date(book.startDate))
            val completionDateStr = if (daysRemaining != null && daysRemaining >= 0) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, daysRemaining)
                dateFormat.format(calendar.time)
            } else {
                null
            }

            ReadingForecast(
                book = book,
                remainingPages = remaining,
                progressPercent = percent,
                estimatedDaysRemaining = daysRemaining,
                dailySpeed = speed,
                startDateFormatted = startDateStr,
                estimatedCompletionDateFormatted = completionDateStr,
            )
        }
    }

    fun getYearlyGoalProgress(books: List<Book>, yearlyGoal: Int): YearlyGoalProgress {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val readThisYear = books.count { book ->
            val isCompleted = book.isRead || book.status == "Gelesen"
            if (!isCompleted) return@count false
            effectiveFinishYear(book) == currentYear
        }
        val goal = yearlyGoal.coerceAtLeast(1)
        val percent = (readThisYear * 100) / goal
        return YearlyGoalProgress(currentYear, readThisYear, goal, percent)
    }

    fun getLanguageStats(books: List<Book>): List<LanguageStats> {
        val supportedLangs = listOf("Deutsch", "Englisch", "Französisch")
        return supportedLangs.map { lang ->
            val matching = books.filter { it.language.equals(lang, ignoreCase = true) }
            val booksCount = matching.count { it.isRead || it.status == "Gelesen" }
            val pagesCount = matching.sumOf { book ->
                if (book.isRead || book.status == "Gelesen") {
                    if (book.pageCount > 0) book.pageCount else book.pagesRead
                } else {
                    book.pagesRead
                }
            }
            LanguageStats(lang, booksCount, pagesCount)
        }
    }

    fun getPagesReadByLanguage(books: List<Book>, year: Int?): Map<String, Int> {
        val filteredBooks = if (year == null) books else books.filter {
            ((it.isRead || it.status == "Gelesen") && effectiveFinishYear(it) == year) || (it.status == "Wird gelesen")
        }
        val supportedLangs = listOf("Deutsch", "Englisch", "Französisch")
        return supportedLangs.associateWith { lang ->
            filteredBooks.filter { it.language.equals(lang, ignoreCase = true) }
                .sumOf { book ->
                    if (book.isRead || book.status == "Gelesen") {
                        if (book.pageCount > 0) book.pageCount else book.pagesRead
                    } else {
                        book.pagesRead
                    }
                }
        }
    }

    fun getYearlyStats(books: List<Book>): List<YearlyStats> {
        val completed = books.filter { it.isRead || it.status == "Gelesen" }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (completed.map { effectiveFinishYear(it) } + currentYear).distinct().sortedDescending()
        val supportedLangs = listOf("Deutsch", "Englisch", "Französisch")

        return years.map { year ->
            val yearBooks = completed.filter { effectiveFinishYear(it) == year }
            val breakdown = supportedLangs.associateWith { lang ->
                yearBooks.count { it.language.equals(lang, ignoreCase = true) }
            }
            YearlyStats(year, breakdown, yearBooks.size)
        }
    }

    fun exportCsv(books: List<Book>): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        sb.append("Titel,Autor,Sprache,Seiten,Gelesen,Status,Bewertung,Beendigungsjahr,Startdatum,Beendigungsdatum\n")
        for (b in books) {
            val title = "\"${b.title.replace("\"", "\"\"")}\""
            val author = "\"${b.author.replace("\"", "\"\"")}\""
            val lang = "\"${b.language}\""
            val startStr = dateFormat.format(Date(b.startDate))
            val finishStr = if (b.finishDate != null) dateFormat.format(Date(b.finishDate)) else ""
            val yearStr = effectiveFinishYear(b)
            sb.append("$title,$author,$lang,${b.pageCount},${b.pagesRead},\"${b.status}\",${b.rating},$yearStr,$startStr,$finishStr\n")
        }
        return sb.toString()
    }

    private fun persist(current: BooksUiState, books: List<Book>, successMessage: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, notice = null) }
            try {
                repository.saveBooks(current.config, current.token, books)
                _state.update { it.copy(books = books, isLoading = false, notice = successMessage) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(books = books, isLoading = false, notice = successMessage) }
            }
        }
    }

    private fun Exception.messageForUser(service: String): String = formatHttpError(this, service)

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookViewModel(repository) as T
    }
}

internal fun effectiveFinishYear(book: Book): Int {
    if (book.finishDate != null && book.finishDate > 0) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = book.finishDate
        return cal.get(Calendar.YEAR)
    }
    if (book.finishYear != null && book.finishYear > 0) {
        return book.finishYear
    }
    return Calendar.getInstance().get(Calendar.YEAR)
}

internal fun formatHttpError(error: Throwable, service: String): String = when (error) {
    is retrofit2.HttpException -> when (service) {
        "GitHub" -> when (error.code()) {
            401, 403 -> "GitHub-Zugriff verweigert. Token und Repository-Berechtigungen prüfen."
            404 -> "Repository oder Datei buecher.json wurde nicht gefunden."
            else -> "GitHub-Anfrage fehlgeschlagen (HTTP ${error.code()})"
        }
        "Google Books" -> when (error.code()) {
            401, 403, 429 -> "Google Books-Anfrage fehlgeschlagen (HTTP ${error.code()})"
            else -> "Google Books-Anfrage fehlgeschlagen (HTTP ${error.code()})"
        }
        else -> "$service-Anfrage fehlgeschlagen (HTTP ${error.code()})"
    }
    else -> error.localizedMessage ?: "$service-Anfrage fehlgeschlagen"
}
