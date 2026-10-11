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

data class BooksUiState(
    val config: GitHubConfig = GitHubConfig(),
    val token: String = "",
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val lookupLoading: Boolean = false,
    val lookupResult: GoogleBook? = null,
    val lookupError: String? = null,
)

class BookViewModel(private val repository: BookRepository) : ViewModel() {
    private val _state = MutableStateFlow(BooksUiState(config = repository.savedConfig()))
    val state: StateFlow<BooksUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null

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
    }

    fun refresh() {
        val current = _state.value
        if (!hasConnectionDetails(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, notice = null) }
            try {
                val books = repository.loadBooks(current.config, current.token)
                _state.update { it.copy(books = books, isLoading = false, notice = "${books.size} Bücher geladen") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(isLoading = false, error = error.messageForUser()) }
            }
        }
    }

    fun save(book: Book) {
        val current = _state.value
        if (!hasConnectionDetails(current)) return
        val updated = current.books.filterNot { it.id == book.id } + book
        persist(current, updated, "Buch gespeichert")
    }

    fun delete(book: Book) {
        val current = _state.value
        if (!hasConnectionDetails(current)) return
        persist(current, current.books.filterNot { it.id == book.id }, "Buch gelöscht")
    }

    fun lookup(input: String) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _state.update { it.copy(lookupLoading = true, lookupResult = null, lookupError = null) }
            try {
                val result = repository.findBook(input)
                _state.update {
                    it.copy(
                        lookupLoading = false,
                        lookupResult = result,
                        lookupError = if (result == null) "Kein passender Eintrag gefunden" else null,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(lookupLoading = false, lookupError = error.messageForUser()) }
            }
        }
    }

    fun clearLookup() {
        lookupJob?.cancel()
        _state.update { it.copy(lookupResult = null, lookupError = null) }
    }

    fun dismissMessages() {
        _state.update { it.copy(error = null, notice = null) }
    }

    private fun persist(current: BooksUiState, books: List<Book>, successMessage: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, notice = null) }
            try {
                repository.saveBooks(current.config, current.token, books)
                _state.update { it.copy(books = books, isLoading = false, notice = successMessage) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.update { it.copy(isLoading = false, error = error.messageForUser()) }
            }
        }
    }

    private fun hasConnectionDetails(current: BooksUiState): Boolean {
        val missing = current.config.owner.isBlank() || current.config.repository.isBlank() || current.token.isBlank()
        if (missing) {
            _state.update { it.copy(error = "Bitte zuerst Repository und GitHub-Token in den Einstellungen eintragen") }
            return false
        }
        return true
    }

    private fun Exception.messageForUser(): String = when (this) {
        is retrofit2.HttpException -> when (code()) {
            401, 403 -> "GitHub-Zugriff verweigert. Token und Repository-Berechtigungen prüfen."
            404 -> "Repository oder Datei buecher.json wurde nicht gefunden."
            else -> "GitHub-Anfrage fehlgeschlagen (HTTP ${code()})"
        }
        else -> localizedMessage ?: "Anfrage fehlgeschlagen"
    }

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookViewModel(repository) as T
    }
}