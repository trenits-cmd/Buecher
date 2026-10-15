package de.buecher.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.buecher.app.data.Book
import de.buecher.app.data.BookRepository
import de.buecher.app.data.GitHubConfig
import de.buecher.app.data.GoogleBook
import de.buecher.app.data.normalizeLanguage
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
import java.util.UUID
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

data class MonthlyPagesStats(
    val monthIndex: Int,
    val monthName: String,
    val pages: Int,
    val booksCount: Int,
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

    fun getMonthlyPagesStats(books: List<Book>, year: Int?, language: String?): List<MonthlyPagesStats> {
        val targetYear = year ?: Calendar.getInstance().get(Calendar.YEAR)
        val completed = books.filter { it.isRead || it.status == "Gelesen" }
        val filtered = completed.filter { book ->
            val bookYear = effectiveFinishYear(book)
            val matchYear = bookYear == targetYear
            val matchLang = language == null || book.language.equals(language, ignoreCase = true)
            matchYear && matchLang
        }

        val monthNames = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
        val pagesMap = mutableMapOf<Int, Int>()
        val countMap = mutableMapOf<Int, Int>()

        for (book in filtered) {
            val cal = Calendar.getInstance()
            if (book.finishDate != null && book.finishDate > 0) {
                cal.timeInMillis = book.finishDate
            } else {
                cal.set(targetYear, Calendar.JANUARY, 1)
            }
            val m = cal.get(Calendar.MONTH)
            val pages = if (book.pageCount > 0) book.pageCount else book.pagesRead
            pagesMap[m] = (pagesMap[m] ?: 0) + pages
            countMap[m] = (countMap[m] ?: 0) + 1
        }

        return monthNames.mapIndexed { index, name ->
            MonthlyPagesStats(
                monthIndex = index,
                monthName = name,
                pages = pagesMap[index] ?: 0,
                booksCount = countMap[index] ?: 0,
            )
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
        sb.append("ID,Titel,Autor,Sprache,GesamteSeiten,GeleseneSeiten,Status,Bewertung,CoverURL,AmazonURL,Startdatum,Beendigungsdatum,Beendigungsjahr\n")
        for (b in books) {
            val id = "\"${b.id.replace("\"", "\"\"")}\""
            val title = "\"${b.title.replace("\"", "\"\"")}\""
            val author = "\"${b.author.replace("\"", "\"\"")}\""
            val lang = "\"${b.language}\""
            val status = "\"${b.status}\""
            val cover = "\"${b.coverImageUrl.replace("\"", "\"\"")}\""
            val amazon = "\"${b.amazonUrl.replace("\"", "\"\"")}\""
            val startStr = dateFormat.format(Date(b.startDate))
            val finishStr = if (b.finishDate != null) dateFormat.format(Date(b.finishDate)) else ""
            val yearStr = effectiveFinishYear(b)

            sb.append("$id,$title,$author,$lang,${b.pageCount},${b.pagesRead},$status,${b.rating},$cover,$amazon,$startStr,$finishStr,$yearStr\n")
        }
        return sb.toString()
    }

    fun showNotice(message: String, isError: Boolean = false) {
        _state.update {
            if (isError) it.copy(error = message, notice = null)
            else it.copy(notice = message, error = null)
        }
    }

    fun importCsv(csvContent: String) {
        viewModelScope.launch {
            try {
                if (csvContent.isBlank()) {
                    _state.update { it.copy(error = "Die ausgewählte CSV-Datei ist leer.") }
                    return@launch
                }
                val newBooks = parseCsv(csvContent)
                if (newBooks.isEmpty()) {
                    _state.update { it.copy(error = "Keine gültigen Bücher in der CSV-Datei gefunden. Bitte Spaltenüberschriften prüfen.") }
                    return@launch
                }
                val current = _state.value
                val existingMap = current.books.associateBy { it.id }.toMutableMap()
                for (book in newBooks) {
                    existingMap[book.id] = book
                }
                val mergedList = existingMap.values.toList()
                persist(current, mergedList, "${newBooks.size} Bücher erfolgreich aus CSV-Datei importiert")
            } catch (e: Exception) {
                _state.update { it.copy(error = "Fehler beim Importieren der CSV-Datei: ${e.localizedMessage ?: "Ungültiges Format"}") }
            }
        }
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

internal fun parseCsv(csvContent: String): List<Book> {
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.size <= 1) return emptyList()

    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val header = parseCsvLine(lines.first()).map { it.trim().lowercase() }
    val titleIdx = header.indexOfFirst { it.contains("titel") || it.contains("title") }
    if (titleIdx == -1) return emptyList()

    val idIdx = header.indexOfFirst { it == "id" }
    val authorIdx = header.indexOfFirst { it.contains("autor") || it.contains("author") }
    val langIdx = header.indexOfFirst { it.contains("sprache") || it.contains("language") }
    val pagesIdx = header.indexOfFirst { it.contains("gesamteseiten") || it == "seiten" || it.contains("pages") }
    val readPagesIdx = header.indexOfFirst { it.contains("geleseneseiten") || it == "gelesen" }
    val statusIdx = header.indexOfFirst { it == "status" }
    val ratingIdx = header.indexOfFirst { it.contains("bewertung") || it.contains("rating") }
    val coverIdx = header.indexOfFirst { it.contains("coverurl") || it.contains("cover") }
    val amazonIdx = header.indexOfFirst { it.contains("amazonurl") || it.contains("amazon") }
    val startIdx = header.indexOfFirst { it.contains("startdatum") }
    val finishIdx = header.indexOfFirst { it.contains("beendigungsdatum") || it.contains("beendet") }
    val yearIdx = header.indexOfFirst { it.contains("beendigungsjahr") }

    val books = mutableListOf<Book>()
    for (i in 1 until lines.size) {
        val row = parseCsvLine(lines[i])
        if (row.size <= titleIdx) continue
        val title = row.getOrNull(titleIdx)?.trim().orEmpty()
        if (title.isBlank()) continue

        val id = idIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }?.ifBlank { null } ?: UUID.randomUUID().toString()
        val author = authorIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val rawLang = langIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val lang = normalizeLanguage(rawLang)
        val pages = pagesIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val readPages = readPagesIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val rawStatus = statusIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val rating = ratingIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val cover = coverIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val amazon = amazonIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()

        val startStr = startIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val finishStr = finishIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val yearVal = yearIdx.takeIf { it != -1 }?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }

        val startDate = try { if (startStr.isNotBlank()) dateFormat.parse(startStr)?.time else null } catch (_: Exception) { null } ?: System.currentTimeMillis()
        val finishDate = try { if (finishStr.isNotBlank()) dateFormat.parse(finishStr)?.time else null } catch (_: Exception) { null }
        val isCompleted = rawStatus.equals("Gelesen", ignoreCase = true) || (pages > 0 && readPages >= pages)
        val status = if (isCompleted) "Gelesen" else if (rawStatus.isNotBlank()) rawStatus else "Ungelesen"

        books.add(
            Book(
                id = id,
                title = title,
                author = author,
                pageCount = pages,
                language = lang,
                rating = rating,
                isRead = isCompleted,
                coverImageUrl = cover,
                amazonUrl = amazon,
                pagesRead = if (isCompleted && pages > 0) pages else readPages,
                status = status,
                finishYear = yearVal ?: if (finishDate != null) Calendar.getInstance().apply { timeInMillis = finishDate }.get(Calendar.YEAR) else null,
                startDate = startDate,
                finishDate = finishDate,
            ),
        )
    }
    return books
}

internal fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                sb.append('"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (c == ',' && !inQuotes) {
            result.add(sb.toString())
            sb.clear()
        } else {
            sb.append(c)
        }
        i++
    }
    result.add(sb.toString())
    return result
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
