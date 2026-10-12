package de.buecher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.buecher.app.data.Book
import de.buecher.app.data.GitHubConfig
import kotlinx.coroutines.delay

private val Leaf = Color(0xFF28745D)
private val Ink = Color(0xFF202923)
private val Paper = Color(0xFFF7F8F5)
private val SoftGreen = Color(0xFFE4EEE8)
private val Gold = Color(0xFFE0A52B)

private sealed interface AppScreen {
    data object Library : AppScreen
    data class Details(val book: Book) : AppScreen
    data class Form(val book: Book?) : AppScreen
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BooksApp(viewModel: BookViewModel) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Library) }
    var settingsOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Leaf,
            onPrimary = Color.White,
            secondary = Color(0xFFB85C45),
            background = Paper,
            surface = Color.White,
            onSurface = Ink,
            error = Color(0xFFB33C32),
        ),
    ) {
        Scaffold(
            containerColor = Paper,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Bücherregal", fontWeight = FontWeight.Bold)
                            Text("${state.books.size} Titel", style = MaterialTheme.typography.labelMedium, color = Leaf)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Synchronisieren")
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "GitHub-Einstellungen")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (screen == AppScreen.Library) {
                    FloatingActionButton(onClick = {
                        viewModel.clearLookup()
                        screen = AppScreen.Form(null)
                    }, containerColor = Leaf, contentColor = Color.White) {
                        Icon(Icons.Default.Add, contentDescription = "Buch hinzufügen")
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Leaf)
                state.error?.let { MessageBanner(it, isError = true) { viewModel.dismissMessages() } }
                state.notice?.let { MessageBanner(it, isError = false) { viewModel.dismissMessages() } }

                when (val currentScreen = screen) {
                    AppScreen.Library -> LibraryScreen(
                        books = state.books,
                        onSelect = { screen = AppScreen.Details(it) },
                        onAdd = { screen = AppScreen.Form(null) },
                    )
                    is AppScreen.Details -> BookDetailsScreen(
                        book = currentScreen.book,
                        onBack = { screen = AppScreen.Library },
                        onEdit = { screen = AppScreen.Form(currentScreen.book) },
                        onDelete = { deleteTarget = currentScreen.book },
                        onProgress = { updated ->
                            screen = AppScreen.Details(updated)
                            viewModel.save(updated)
                        },
                    )
                    is AppScreen.Form -> BookFormScreen(
                        original = currentScreen.book,
                        state = state,
                        viewModel = viewModel,
                        onCancel = { screen = if (currentScreen.book == null) AppScreen.Library else AppScreen.Details(currentScreen.book) },
                        onSave = {
                            viewModel.save(it)
                            screen = AppScreen.Details(it)
                        },
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsDialog(
                config = state.config,
                currentToken = state.token,
                onDismiss = { settingsOpen = false },
                onSave = { config, token ->
                    viewModel.configure(config, token)
                    settingsOpen = false
                    viewModel.refresh()
                },
            )
        }
        deleteTarget?.let { book ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Buch löschen?") },
                text = { Text("„${book.title}“ wird aus dem GitHub-Regal entfernt.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(book)
                        deleteTarget = null
                        screen = AppScreen.Library
                    }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") } },
            )
        }
    }
}

@Composable
private fun LibraryScreen(books: List<Book>, onSelect: (Book) -> Unit, onAdd: () -> Unit) {
    if (books.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Leaf, modifier = Modifier.size(50.dp))
            Spacer(Modifier.height(16.dp))
            Text("Dein Regal ist noch leer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Füge deinen ersten Titel hinzu.", color = Color(0xFF65716A))
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAdd) { Text("Buch hinzufügen") }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(books.sortedBy { it.title.lowercase() }, key = { it.id }) { book ->
                BookRow(book = book, onClick = { onSelect(book) })
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverImageUrl, Modifier.width(58.dp).height(82.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Text(book.author.ifBlank { "Unbekannter Autor" }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF65716A), maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(book.rating, interactive = false)
                    Spacer(Modifier.width(8.dp))
                    Text(if (book.isRead) "Gelesen" else progressLabel(book), style = MaterialTheme.typography.labelMedium, color = Leaf)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress(book) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                    color = Leaf,
                    trackColor = SoftGreen,
                )
            }
        }
    }
}

@Composable
private fun BookDetailsScreen(
    book: Book,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onProgress: (Book) -> Unit,
) {
    var pagesRead by remember(book.id) { mutableFloatStateOf(book.pagesRead.toFloat()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text("Buchdetails", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error) }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BookCover(book.coverImageUrl, Modifier.width(142.dp).height(205.dp))
            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(book.author, style = MaterialTheme.typography.titleMedium, color = Color(0xFF65716A))
            RatingStars(book.rating, interactive = false)
            Divider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailValue("Seiten", book.pageCount.takeIf { it > 0 }?.toString() ?: "–")
                DetailValue("Sprache", book.language.ifBlank { "–" })
                DetailValue("Status", if (book.isRead) "Gelesen" else "Offen")
            }
            if (!book.isRead && book.pageCount > 0) {
                val currentProgress = (pagesRead.toInt().coerceIn(0, book.pageCount) * 100) / book.pageCount
                Text("Fortschritt: $currentProgress %", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                Slider(
                    value = pagesRead.coerceIn(0f, book.pageCount.toFloat()),
                    onValueChange = { pagesRead = it },
                    valueRange = 0f..book.pageCount.toFloat(),
                    onValueChangeFinished = { onProgress(book.copy(pagesRead = pagesRead.toInt())) },
                )
                Text("${pagesRead.toInt().coerceIn(0, book.pageCount)} von ${book.pageCount} Seiten", color = Color(0xFF65716A))
            }
            if (book.amazonUrl.isNotBlank()) {
                Text(book.amazonUrl, style = MaterialTheme.typography.bodySmall, color = Leaf, maxLines = 2)
            }
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BookFormScreen(
    original: Book?,
    state: BooksUiState,
    viewModel: BookViewModel,
    onCancel: () -> Unit,
    onSave: (Book) -> Unit,
) {
    var lookupText by remember(original?.id) { mutableStateOf("") }
    var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
    var author by remember(original?.id) { mutableStateOf(original?.author.orEmpty()) }
    var pageCount by remember(original?.id) { mutableStateOf(original?.pageCount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var language by remember(original?.id) { mutableStateOf(original?.language.orEmpty()) }
    var cover by remember(original?.id) { mutableStateOf(original?.coverImageUrl.orEmpty()) }
    var amazonUrl by remember(original?.id) { mutableStateOf(original?.amazonUrl.orEmpty()) }
    var pagesRead by remember(original?.id) { mutableStateOf(original?.pagesRead?.toString().orEmpty()) }
    var rating by remember(original?.id) { mutableStateOf(original?.rating ?: 0) }
    var isRead by remember(original?.id) { mutableStateOf(original?.isRead ?: false) }

    LaunchedEffect(lookupText, original?.id) {
        if (original == null && lookupText.trim().length >= 4) {
            delay(650)
            viewModel.lookup(lookupText.trim())
        }
    }
    LaunchedEffect(state.lookupResult, lookupText) {
        state.lookupResult?.let { found ->
            title = found.title
            author = found.author
            if (found.pageCount > 0) pageCount = found.pageCount.toString()
            language = found.language
            cover = found.coverImageUrl
        }
        if (lookupText.isNotBlank() && (lookupText.contains("http", ignoreCase = true) || lookupText.contains("amazon.", ignoreCase = true) || lookupText.contains("amzn.", ignoreCase = true) || lookupText.contains("www.", ignoreCase = true))) {
            amazonUrl = lookupText.trim()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text(if (original == null) "Buch hinzufügen" else "Buch bearbeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (original == null) {
                item {
                    OutlinedTextField(
                        value = lookupText,
                        onValueChange = { lookupText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Titel, ISBN oder Amazon-Link") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.lookup(lookupText.trim()) }),
                    )
                    when {
                        state.lookupLoading -> Text("Google Books wird durchsucht …", color = Leaf)
                        state.lookupError != null -> Text(state.lookupError, color = MaterialTheme.colorScheme.error)
                        state.lookupResult != null -> Text("Buchdaten übernommen", color = Leaf)
                    }
                }
            }
            item { FormField("Titel", title, { title = it }) }
            item { FormField("Autor", author, { author = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField("Seiten", pageCount, { pageCount = it }, Modifier.weight(1f), KeyboardType.Number)
                    FormField("Sprache", language, { language = it }, Modifier.weight(1f))
                }
            }
            item { FormField("Cover-Bild-URL", cover, { cover = it }, keyboardType = KeyboardType.Uri) }
            item { FormField("Amazon-Link", amazonUrl, { amazonUrl = it }, keyboardType = KeyboardType.Uri) }
            item {
                Column {
                    Text("Bewertung", fontWeight = FontWeight.Medium)
                    RatingStars(rating, interactive = true, onRatingChange = { rating = it })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRead, onCheckedChange = { isRead = it })
                    Text("Gelesen")
                }
            }
            if (!isRead) item { FormField("Gelesene Seiten", pagesRead, { pagesRead = it }, keyboardType = KeyboardType.Number) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
                    Button(
                        onClick = {
                            val totalPages = pageCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            onSave(
                                Book(
                                    id = original?.id ?: java.util.UUID.randomUUID().toString(),
                                    title = title.trim(),
                                    author = author.trim(),
                                    pageCount = totalPages,
                                    language = language.trim(),
                                    rating = rating,
                                    isRead = isRead,
                                    coverImageUrl = cover.trim(),
                                    amazonUrl = amazonUrl.trim(),
                                    pagesRead = if (isRead) totalPages else (pagesRead.toIntOrNull() ?: 0).coerceIn(0, totalPages.takeIf { it > 0 } ?: Int.MAX_VALUE),
                                ),
                            )
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun RatingStars(rating: Int, interactive: Boolean, onRatingChange: (Int) -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { star ->
            if (interactive) {
                IconButton(onClick = { onRatingChange(if (rating == star) 0 else star) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$star von 5 Sternen",
                        tint = if (star <= rating) Gold else Color(0xFFB5BDB7),
                    )
                }
            } else {
                Icon(
                    if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (star <= rating) Gold else Color(0xFFD7DDD8),
                )
            }
        }
        if (interactive) Text("$rating/5", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BookCover(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(SoftGreen),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = "Buchcover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Leaf, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MessageBanner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (isError) Color(0xFFFBE8E5) else SoftGreen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), color = if (isError) Color(0xFF9F342B) else Leaf, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismiss) { Text("Schließen") }
    }
}

@Composable
private fun SettingsDialog(
    config: GitHubConfig,
    currentToken: String,
    onDismiss: () -> Unit,
    onSave: (GitHubConfig, String) -> Unit,
) {
    var owner by remember { mutableStateOf(config.owner) }
    var repository by remember { mutableStateOf(config.repository) }
    var branch by remember { mutableStateOf(config.branch) }
    var token by remember { mutableStateOf(currentToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(owner, { owner = it }, label = { Text("GitHub-Benutzer oder Organisation") }, singleLine = true)
                OutlinedTextField(repository, { repository = it }, label = { Text("Repository") }, singleLine = true)
                OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true)
                OutlinedTextField(
                    token,
                    { token = it },
                    label = { Text("Personal Access Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(GitHubConfig(owner.trim(), repository.trim(), branch.trim()), token) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun progress(book: Book): Int = when {
    book.isRead -> 100
    book.pageCount <= 0 -> 0
    else -> (book.pagesRead.coerceIn(0, book.pageCount) * 100) / book.pageCount
}

private fun progressLabel(book: Book): String = if (book.pageCount > 0) {
    "${book.pagesRead.coerceIn(0, book.pageCount)}/${book.pageCount} S."
} else {
    "Ungelesen"
}