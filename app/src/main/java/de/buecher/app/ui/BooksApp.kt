package de.buecher.app.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.buecher.app.R
import de.buecher.app.data.Book
import de.buecher.app.data.GitHubConfig
import de.buecher.app.data.GoogleBook
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Leaf = Color(0xFF28745D)
private val Ink = Color(0xFF202923)
private val Paper = Color(0xFFF7F8F5)
private val SoftGreen = Color(0xFFE4EEE8)
private val Gold = Color(0xFFE0A52B)

private sealed interface AppScreen {
    data object Dashboard : AppScreen
    data object Library : AppScreen
    data object Statistics : AppScreen
    data object Archive : AppScreen
    data class Details(val book: Book) : AppScreen
    data class Form(val book: Book?, val formKey: String = java.util.UUID.randomUUID().toString()) : AppScreen
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BooksApp(viewModel: BookViewModel) {
    val state by viewModel.state.collectAsState()
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Dashboard) }
    val currentScreen = backStack.lastOrNull() ?: AppScreen.Dashboard
    var settingsOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    fun navigateTo(target: AppScreen) {
        if (backStack.lastOrNull() != target) {
            if (target is AppScreen.Dashboard) {
                backStack.clear()
                backStack.add(AppScreen.Dashboard)
            } else {
                backStack.add(target)
            }
        }
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    BackHandler(enabled = settingsOpen || deleteTarget != null || backStack.size > 1) {
        if (settingsOpen) {
            settingsOpen = false
        } else if (deleteTarget != null) {
            deleteTarget = null
        } else if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }
    val context = LocalContext.current

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            viewModel.showNotice("Import abgebrochen", isError = false)
            return@rememberLauncherForActivityResult
        }
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val csvText = inputStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (csvText.isBlank()) {
                viewModel.showNotice("Die ausgewählte CSV-Datei ist leer.", isError = true)
            } else {
                viewModel.importCsv(csvText)
            }
        } catch (e: Exception) {
            viewModel.showNotice("Fehler beim Lesen der Datei: ${e.localizedMessage ?: "Datei nicht lesbar"}", isError = true)
        }
    }

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
                            Text("Bücher", fontWeight = FontWeight.Bold)
                            Text("${state.books.size} Titel im System", style = MaterialTheme.typography.labelMedium, color = Leaf)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            csvImportLauncher.launch("*/*")
                        }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "CSV Importieren")
                        }
                        IconButton(onClick = {
                            try {
                                if (state.books.isEmpty()) {
                                    viewModel.showNotice("Keine Bücher zum Exportieren vorhanden", isError = true)
                                    return@IconButton
                                }
                                val csv = viewModel.exportCsv(state.books)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_SUBJECT, "Bücherregal Backup (CSV)")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    type = "text/csv"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "CSV Backup exportieren")
                                context.startActivity(shareIntent)
                                viewModel.showNotice("${state.books.size} Bücher als CSV exportiert", isError = false)
                            } catch (e: Exception) {
                                viewModel.showNotice("Fehler beim Exportieren: ${e.localizedMessage ?: "Unbekannter Fehler"}", isError = true)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "CSV Backup exportieren")
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Synchronisieren")
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "GitHub-Einstellungen")
                        }
                    },
                )
            },
            bottomBar = {
                if (currentScreen is AppScreen.Dashboard || currentScreen is AppScreen.Library || currentScreen is AppScreen.Statistics || currentScreen is AppScreen.Archive) {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") },
                            selected = currentScreen is AppScreen.Dashboard,
                            onClick = { navigateTo(AppScreen.Dashboard) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Leaf, selectedTextColor = Leaf),
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.MenuBook, contentDescription = "Regal") },
                            label = { Text("Regal") },
                            selected = currentScreen is AppScreen.Library,
                            onClick = { navigateTo(AppScreen.Library) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Leaf, selectedTextColor = Leaf),
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistik") },
                            label = { Text("Statistik") },
                            selected = currentScreen is AppScreen.Statistics,
                            onClick = { navigateTo(AppScreen.Statistics) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Leaf, selectedTextColor = Leaf),
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Archive, contentDescription = "Archiv") },
                            label = { Text("Archiv") },
                            selected = currentScreen is AppScreen.Archive,
                            onClick = { navigateTo(AppScreen.Archive) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Leaf, selectedTextColor = Leaf),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen == AppScreen.Library || currentScreen == AppScreen.Dashboard) {
                    FloatingActionButton(onClick = {
                        viewModel.clearLookup()
                        navigateTo(AppScreen.Form(null))
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

                when (val screen = currentScreen) {
                    AppScreen.Dashboard -> DashboardScreen(
                        books = state.books,
                        viewModel = viewModel,
                        onSelect = { navigateTo(AppScreen.Details(it)) },
                    )
                    AppScreen.Library -> LibraryScreen(
                        books = state.books,
                        onSelect = { navigateTo(AppScreen.Details(it)) },
                        onAdd = { navigateTo(AppScreen.Form(null)) },
                        onStatusChange = { updated -> viewModel.save(updated) },
                    )
                    AppScreen.Statistics -> StatisticsScreen(
                        books = state.books,
                        viewModel = viewModel,
                        onSelect = { navigateTo(AppScreen.Details(it)) },
                    )
                    AppScreen.Archive -> ArchiveScreen(
                        books = state.books,
                        onSelect = { navigateTo(AppScreen.Details(it)) },
                        onStatusChange = { updated -> viewModel.save(updated) },
                    )
                    is AppScreen.Details -> BookDetailsScreen(
                        book = screen.book,
                        onBack = { goBack() },
                        onEdit = { navigateTo(AppScreen.Form(screen.book)) },
                        onDelete = { deleteTarget = screen.book },
                        onProgress = { updated ->
                            viewModel.save(updated)
                            if (backStack.isNotEmpty()) {
                                backStack[backStack.size - 1] = AppScreen.Details(updated)
                            }
                        },
                    )
                    is AppScreen.Form -> BookFormScreen(
                        original = screen.book,
                        formKey = screen.formKey,
                        state = state,
                        viewModel = viewModel,
                        onCancel = { goBack() },
                        onSave = { updated ->
                            viewModel.save(updated)
                            if (backStack.isNotEmpty()) {
                                backStack[backStack.size - 1] = AppScreen.Details(updated)
                            }
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
                text = { Text("„${book.title}“ wird aus dem Regal entfernt.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(book)
                        deleteTarget = null
                        goBack()
                    }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") } },
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    books: List<Book>,
    viewModel: BookViewModel,
    onSelect: (Book) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var selectedYear by remember { mutableStateOf<Int?>(currentYear) }
    var selectedActiveLangFilter by remember { mutableStateOf("Alle") }
    var showCompletedList by remember { mutableStateOf(false) }

    val availableYears = remember(books) {
        (books.mapNotNull { it.finishYear } + currentYear).distinct().sortedDescending()
    }

    val filteredBooksForStats = remember(books, selectedYear) {
        if (selectedYear == null) books else books.filter {
            ((it.isRead || it.status == "Gelesen") && (it.finishYear == selectedYear)) || (it.status == "Wird gelesen")
        }
    }

    val goalProgress = remember(books, state.yearlyGoal) { viewModel.getYearlyGoalProgress(books, state.yearlyGoal) }
    val pagesByLang = remember(books, selectedYear) { viewModel.getPagesReadByLanguage(books, selectedYear) }
    val allForecasts = remember(books) { viewModel.getActiveForecasts(books) }
    val filteredForecasts = remember(allForecasts, selectedActiveLangFilter) {
        if (selectedActiveLangFilter == "Alle") allForecasts
        else allForecasts.filter { it.book.language.equals(selectedActiveLangFilter, ignoreCase = true) }
    }

    val completedBooks = remember(filteredBooksForStats) { filteredBooksForStats.filter { it.isRead || it.status == "Gelesen" } }
    val completedCount = completedBooks.size
    val totalPagesRead = remember(filteredBooksForStats) {
        filteredBooksForStats.sumOf { book ->
            if (book.isRead || book.status == "Gelesen") {
                if (book.pageCount > 0) book.pageCount else book.pagesRead
            } else {
                book.pagesRead
            }
        }
    }
    var editGoalOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Year Filter Chips
        item {
            Column {
                Text("Jahresfilter", style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedYear == null) Leaf else SoftGreen,
                        modifier = Modifier.clickable { selectedYear = null },
                    ) {
                        Text(
                            text = "Alle Jahre",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedYear == null) Color.White else Leaf,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    availableYears.take(4).forEach { yr ->
                        val selected = selectedYear == yr
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Leaf else SoftGreen,
                            modifier = Modifier.clickable { selectedYear = yr },
                        ) {
                            Text(
                                text = "$yr",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else Leaf,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // 2. Summary Stat Cards (Tapping toggles viewing completed books list)
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Gelesene Bücher",
                    value = completedCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = Leaf,
                    onClick = { showCompletedList = !showCompletedList },
                )
                StatCard(
                    title = "Gelesene Seiten",
                    value = totalPagesRead.toString(),
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFFB85C45),
                    onClick = { showCompletedList = !showCompletedList },
                )
            }
        }

        // 3. Pages Read by Language
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gelesene Seiten nach Sprache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        pagesByLang.forEach { (lang, pages) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(lang, style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                                Text("$pages S.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Leaf)
                            }
                        }
                    }
                }
            }
        }

        // 4. Show completed books list if stat card was tapped
        if (showCompletedList) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Gelesene Bücher im Filter (${completedBooks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCompletedList = false }) { Text("Ausblenden", color = Leaf) }
                }
            }
            if (completedBooks.isEmpty()) {
                item { Text("Keine gelesenen Bücher für diesen Filter.", color = Color(0xFF65716A), style = MaterialTheme.typography.bodySmall) }
            } else {
                items(completedBooks, key = { it.id }) { book ->
                    BookRow(book = book, onClick = { onSelect(book) }, onStatusChange = {})
                }
            }
        }

        // 5. Yearly Goal Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Leseziel ${goalProgress.currentYear}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { editGoalOpen = true }) {
                            Text("Ziel anpassen", color = Leaf)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${goalProgress.booksReadThisYear} von ${goalProgress.yearlyGoal} Büchern (${goalProgress.percent}%)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Leaf,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (goalProgress.percent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Leaf,
                        trackColor = SoftGreen,
                    )
                }
            }
        }

        // 6. Active Reading Section with Language Filter
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktive Lektüre & Prognose", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Alle", "Deutsch", "Englisch", "Französisch").forEach { langOpt ->
                        val selected = selectedActiveLangFilter == langOpt
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Leaf else SoftGreen,
                            modifier = Modifier.clickable { selectedActiveLangFilter = langOpt },
                        ) {
                            Text(
                                text = langOpt,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else Leaf,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        if (filteredForecasts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Keine aktiven Bücher in dieser Ansicht", fontWeight = FontWeight.SemiBold)
                        Text("Bücher mit Status „Wird gelesen“ erscheinen hier mit einer intelligenten Leseprognose.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF65716A), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        } else {
            items(filteredForecasts, key = { it.book.id }) { forecast ->
                ActiveBookCard(forecast = forecast, onClick = { onSelect(forecast.book) })
            }
        }
    }

    if (editGoalOpen) {
        var goalText by remember { mutableStateOf(state.yearlyGoal.toString()) }
        AlertDialog(
            onDismissRequest = { editGoalOpen = false },
            title = { Text("Jahres-Leseziel festlegen") },
            text = {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    label = { Text("Anzahl Bücher für ${goalProgress.currentYear}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goalText.toIntOrNull()?.let { viewModel.setYearlyGoal(it) }
                    editGoalOpen = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editGoalOpen = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, accent: Color, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun ActiveBookCard(forecast: ReadingForecast, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(forecast.book.coverImageUrl, forecast.book.amazonUrl, Modifier.width(60.dp).height(86.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(forecast.book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(forecast.book.author.ifBlank { "Unbekannter Autor" }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF65716A), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
                    Text(forecast.book.language.ifBlank { "Deutsch" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Leaf)
                    if (forecast.book.pageCount > 0) {
                        Text("•", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
                        Text("${forecast.book.pageCount} S.", style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Begonnen am: ${forecast.startDateFormatted}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF65716A))
                Text(String.format(Locale.getDefault(), "Lesetempo: Ø %.1f Seiten/Tag", forecast.dailySpeed), style = MaterialTheme.typography.labelSmall, color = Color(0xFF65716A))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fortschritt: ${forecast.progressPercent}%", style = MaterialTheme.typography.labelMedium, color = Leaf, fontWeight = FontWeight.Bold)
                    val daysText = if (forecast.estimatedDaysRemaining != null && forecast.estimatedCompletionDateFormatted != null) {
                        "~${forecast.estimatedDaysRemaining} Tage (bis ${forecast.estimatedCompletionDateFormatted})"
                    } else {
                        "Prognose läuft..."
                    }
                    Text(daysText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFB85C45))
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { forecast.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Leaf,
                    trackColor = SoftGreen,
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(books: List<Book>, onSelect: (Book) -> Unit, onAdd: () -> Unit, onStatusChange: (Book) -> Unit) {
    var selectedStatusFilter by remember { mutableStateOf("Alle") }
    var selectedLangFilter by remember { mutableStateOf("Alle") }

    val activeBooks = remember(books) { books.filter { it.status != "Gelesen" && !it.isRead } }
    val filteredBooks = remember(activeBooks, selectedStatusFilter, selectedLangFilter) {
        activeBooks.filter { book ->
            val matchStatus = selectedStatusFilter == "Alle" || book.status.equals(selectedStatusFilter, ignoreCase = true)
            val matchLang = selectedLangFilter == "Alle" || book.language.equals(selectedLangFilter, ignoreCase = true)
            matchStatus && matchLang
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Status filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Alle", "Ungelesen", "Wird gelesen", "Pausiert").forEach { status ->
                val selected = selectedStatusFilter == status
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Leaf else SoftGreen,
                    modifier = Modifier.clickable { selectedStatusFilter = status },
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else Leaf,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Language filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Alle", "Deutsch", "Englisch", "Französisch").forEach { lang ->
                val selected = selectedLangFilter == lang
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Leaf else SoftGreen,
                    modifier = Modifier.clickable { selectedLangFilter = lang },
                ) {
                    Text(
                        text = lang,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else Leaf,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (filteredBooks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
                Text("Keine Bücher in dieser Ansicht", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Gelesene Bücher befinden sich im Archiv.", color = Color(0xFF65716A), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onAdd) { Text("Buch hinzufügen") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredBooks.sortedBy { it.title.lowercase() }, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        onClick = { onSelect(book) },
                        onStatusChange = { updated -> onStatusChange(updated) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsScreen(books: List<Book>, viewModel: BookViewModel, onSelect: (Book) -> Unit) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var selectedLanguageFilter by remember { mutableStateOf<String?>(null) }
    var selectedYearFilter by remember { mutableStateOf<Int?>(currentYear) }
    var selectedMonthFilter by remember { mutableStateOf<Int?>(null) }

    val availableYears = remember(books) {
        (books.mapNotNull { effectiveFinishYear(it) } + currentYear).distinct().sortedDescending()
    }

    val yearFilteredBooks = remember(books, selectedYearFilter) {
        if (selectedYearFilter == null) books else books.filter { effectiveFinishYear(it) == selectedYearFilter }
    }

    val langStats = remember(yearFilteredBooks) { viewModel.getLanguageStats(yearFilteredBooks) }
    val yearlyStats = remember(books) { viewModel.getYearlyStats(books) }
    val monthlyStats = remember(books, selectedYearFilter, selectedLanguageFilter) {
        viewModel.getMonthlyPagesStats(books, selectedYearFilter, selectedLanguageFilter)
    }
    val maxMonthlyPages = remember(monthlyStats) {
        monthlyStats.maxOfOrNull { it.pages }?.coerceAtLeast(1) ?: 1
    }

    val langFilteredBooks = remember(yearFilteredBooks, selectedLanguageFilter) {
        if (selectedLanguageFilter == null) yearFilteredBooks else yearFilteredBooks.filter { it.language.equals(selectedLanguageFilter, ignoreCase = true) }
    }

    val filteredBooks = remember(langFilteredBooks, selectedMonthFilter, selectedYearFilter) {
        if (selectedMonthFilter == null) langFilteredBooks
        else langFilteredBooks.filter { book ->
            val bookMonth = when {
                book.finishDate != null && book.finishDate > 0 -> {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = book.finishDate
                    cal.get(Calendar.MONTH)
                }
                book.startDate > 0 -> {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = book.startDate
                    cal.get(Calendar.MONTH)
                }
                else -> 0
            }
            bookMonth == selectedMonthFilter
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Statistik & Filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Year filter chips
            Column {
                Text("Jahr-Filter", style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedYearFilter == null) Leaf else SoftGreen,
                        modifier = Modifier.clickable { selectedYearFilter = null },
                    ) {
                        Text(
                            text = "Alle Jahre",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedYearFilter == null) Color.White else Leaf,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    availableYears.take(4).forEach { yr ->
                        val selected = selectedYearFilter == yr
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Leaf else SoftGreen,
                            modifier = Modifier.clickable { selectedYearFilter = if (selected) null else yr },
                        ) {
                            Text(
                                text = "$yr",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else Leaf,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Language filter chips
            Column {
                Text("Sprach-Filter", style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedLanguageFilter == null) Leaf else SoftGreen,
                        modifier = Modifier.clickable { selectedLanguageFilter = null },
                    ) {
                        Text(
                            text = "Alle Sprachen",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedLanguageFilter == null) Color.White else Leaf,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    listOf("Deutsch", "Englisch", "Französisch").forEach { lang ->
                        val selected = selectedLanguageFilter == lang
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Leaf else SoftGreen,
                            modifier = Modifier.clickable { selectedLanguageFilter = if (selected) null else lang },
                        ) {
                            Text(
                                text = lang,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else Leaf,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Monthly Pages Bar Chart
        item {
            Spacer(Modifier.height(8.dp))
            Text("Seitenzahl pro Monat (Grafik)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (selectedYearFilter != null) "Jahr ${selectedYearFilter}${if (selectedMonthFilter != null) " • Monat: ${listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")[selectedMonthFilter!!]}" else ""}" else "Alle Jahre", style = MaterialTheme.typography.bodySmall, color = Color(0xFF65716A))
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    monthlyStats.forEach { stat ->
                        val isSelectedMonth = selectedMonthFilter == stat.monthIndex
                        val fraction = (stat.pages.toFloat() / maxMonthlyPages.toFloat()).coerceIn(0f, 1f)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedMonthFilter = if (isSelectedMonth) null else stat.monthIndex
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelectedMonth) SoftGreen else Color.Transparent,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stat.monthName,
                                    modifier = Modifier.width(36.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelectedMonth) Leaf else Color(0xFF65716A),
                                )
                                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(SoftGreen)) {
                                    if (stat.pages > 0) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(8.dp)).background(if (isSelectedMonth) Color(0xFF1E5644) else Leaf))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${stat.pages} S.${if (stat.booksCount > 0) " (${stat.booksCount})" else ""}",
                                    modifier = Modifier.width(90.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }

        items(langStats) { stat ->
            val isSelected = selectedLanguageFilter == stat.language
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedLanguageFilter = if (isSelected) null else stat.language
                },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) SoftGreen else Color.White,
                shadowElevation = 1.dp,
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stat.language, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Leaf)
                        Spacer(Modifier.height(2.dp))
                        Text("${stat.booksReadCount} Bücher gelesen", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF65716A))
                    }
                    Text("${stat.pagesReadCount} Seiten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Jahresübersicht", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(yearlyStats) { yearStat ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Jahr ${yearStat.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${yearStat.totalBooks} Bücher", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Leaf)
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider(color = SoftGreen)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        yearStat.languageBreakdown.forEach { (lang, count) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(lang, style = MaterialTheme.typography.labelSmall, color = Color(0xFF65716A))
                                Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Gefilterte Bücher (${filteredBooks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (selectedLanguageFilter != null || selectedYearFilter != null || selectedMonthFilter != null) {
                    TextButton(onClick = {
                        selectedLanguageFilter = null
                        selectedYearFilter = null
                        selectedMonthFilter = null
                    }) { Text("Filter zurücksetzen", color = Leaf) }
                }
            }
        }
        items(filteredBooks, key = { it.id }) { book ->
            BookRow(book = book, onClick = { onSelect(book) }, onStatusChange = {})
        }
    }
}

@Composable
private fun ArchiveScreen(books: List<Book>, onSelect: (Book) -> Unit, onStatusChange: (Book) -> Unit) {
    var selectedLangFilter by remember { mutableStateOf("Alle") }
    var selectedYearFilter by remember { mutableStateOf<Int?>(null) }
    var selectedRatingFilter by remember { mutableStateOf<Int?>(null) }

    val archived = remember(books) {
        books.filter { it.isRead || it.status == "Gelesen" }
    }

    val availableYears = remember(archived) {
        archived.map { effectiveFinishYear(it) }.distinct().sortedDescending()
    }

    val filtered = remember(archived, selectedLangFilter, selectedYearFilter, selectedRatingFilter) {
        archived.filter { book ->
            val matchLang = selectedLangFilter == "Alle" || book.language.equals(selectedLangFilter, ignoreCase = true)
            val matchYear = selectedYearFilter == null || effectiveFinishYear(book) == selectedYearFilter
            val matchRating = selectedRatingFilter == null || book.rating == selectedRatingFilter
            matchLang && matchYear && matchRating
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Filter Row 1: Language
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Alle", "Deutsch", "Englisch", "Französisch").forEach { lang ->
                val selected = selectedLangFilter == lang
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Leaf else SoftGreen,
                    modifier = Modifier.clickable { selectedLangFilter = lang },
                ) {
                    Text(
                        text = lang,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else Leaf,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Filter Row 2: Rating Stars
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selectedRatingFilter == null) Leaf else SoftGreen,
                modifier = Modifier.clickable { selectedRatingFilter = null },
            ) {
                Text(
                    text = "Alle Sterne",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedRatingFilter == null) Color.White else Leaf,
                    fontWeight = FontWeight.Medium,
                )
            }
            (5 downTo 1).forEach { star ->
                val selected = selectedRatingFilter == star
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Leaf else SoftGreen,
                    modifier = Modifier.clickable { selectedRatingFilter = if (selected) null else star },
                ) {
                    Text(
                        text = "$star ★",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else Leaf,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Filter Row 3: Years
        if (availableYears.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedYearFilter == null) Leaf else SoftGreen,
                    modifier = Modifier.clickable { selectedYearFilter = null },
                ) {
                    Text(
                        text = "Alle Jahre",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedYearFilter == null) Color.White else Leaf,
                        fontWeight = FontWeight.Medium,
                    )
                }
                availableYears.forEach { yr ->
                    val selected = selectedYearFilter == yr
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) Leaf else SoftGreen,
                        modifier = Modifier.clickable { selectedYearFilter = if (selected) null else yr },
                    ) {
                        Text(
                            text = "$yr",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else Leaf,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = Leaf, modifier = Modifier.size(50.dp))
                Spacer(Modifier.height(16.dp))
                Text("Keine Bücher in dieser Ansicht", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Gelesene Bücher erscheinen hier nach Sprach-, Sterne- und Jahresfilter.", color = Color(0xFF65716A), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Gelesene Bücher (${filtered.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(filtered.sortedByDescending { it.finishDate ?: it.startDate }, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        onClick = { onSelect(book) },
                        onStatusChange = { updated -> onStatusChange(updated) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onStatusChange: (Book) -> Unit) {
    var statusMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable {
            if (book.amazonUrl.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.amazonUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    onClick()
                }
            } else {
                onClick()
            }
        },
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverImageUrl, book.amazonUrl, Modifier.width(58.dp).height(82.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.author.ifBlank { "Unbekannter Autor" }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF65716A), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Text("•", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
                    Text(book.language.ifBlank { "Deutsch" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Leaf)
                    if (book.pageCount > 0) {
                        Text("•", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
                        Text("${book.pageCount} S.", style = MaterialTheme.typography.labelMedium, color = Color(0xFF65716A))
                    }
                }

                if (book.isRead || book.status == "Gelesen") {
                    Spacer(Modifier.height(3.dp))
                    val startStr = dateFormat.format(Date(book.startDate))
                    val finishStr = if (book.finishDate != null) dateFormat.format(Date(book.finishDate)) else ""
                    val durationDays = if (book.finishDate != null) maxOf(1L, (book.finishDate - book.startDate) / (1000 * 60 * 60 * 24)) else null
                    val totalPages = if (book.pageCount > 0) book.pageCount else book.pagesRead
                    val speed = if (durationDays != null && totalPages > 0) totalPages.toFloat() / durationDays.toFloat() else null
                    val speedText = if (speed != null) String.format(Locale.getDefault(), " • Ø %.1f S./Tag", speed) else ""
                    val durationText = if (durationDays != null) " ($durationDays ${if (durationDays == 1L) "Tag" else "Tage"}$speedText)" else ""
                    val lineText = if (finishStr.isNotBlank()) "$startStr – $finishStr$durationText" else "Begonnen am $startStr"
                    Text(lineText, style = MaterialTheme.typography.labelSmall, color = Color(0xFF65716A))
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingStars(book.rating, interactive = false)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SoftGreen,
                            modifier = Modifier.clickable { statusMenuExpanded = true },
                        ) {
                            Text(
                                text = book.status.ifBlank { if (book.isRead) "Gelesen" else "Ungelesen" },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Leaf,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        DropdownMenu(
                            expanded = statusMenuExpanded,
                            onDismissRequest = { statusMenuExpanded = false },
                        ) {
                            listOf("Ungelesen", "Wird gelesen", "Pausiert", "Gelesen").forEach { statusOpt ->
                                DropdownMenuItem(
                                    text = { Text(statusOpt) },
                                    onClick = {
                                        val isCompleted = statusOpt == "Gelesen"
                                        val now = System.currentTimeMillis()
                                        val updatedStart = if (statusOpt == "Wird gelesen" && book.status != "Wird gelesen") now else book.startDate
                                        val updatedFinish = if (isCompleted) (book.finishDate ?: now) else book.finishDate
                                        onStatusChange(book.copy(status = statusOpt, isRead = isCompleted, startDate = updatedStart, finishDate = updatedFinish))
                                        statusMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress(book) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                    color = Leaf,
                    trackColor = SoftGreen,
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, contentDescription = "Details & Bearbeiten", tint = Color(0xFF65716A))
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
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

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
            BookCover(book.coverImageUrl, book.amazonUrl, Modifier.width(142.dp).height(205.dp))
            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(book.author, style = MaterialTheme.typography.titleMedium, color = Color(0xFF65716A))
            RatingStars(book.rating, interactive = false)
            Divider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailValue("Seiten", book.pageCount.takeIf { it > 0 }?.toString() ?: "–")
                DetailValue("Sprache", book.language.ifBlank { "–" })
                DetailValue("Status", book.status.ifBlank { if (book.isRead) "Gelesen" else "Offen" })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailValue("Begonnen", dateFormat.format(Date(book.startDate)))
                if (book.finishDate != null) {
                    DetailValue("Beendet", dateFormat.format(Date(book.finishDate)))
                    val daysTaken = maxOf(1L, (book.finishDate - book.startDate) / (1000 * 60 * 60 * 24))
                    DetailValue("Lesedauer", "$daysTaken ${if (daysTaken == 1L) "Tag" else "Tage"}")
                } else if (book.finishYear != null) {
                    DetailValue("Beendigungsjahr", "${book.finishYear}")
                }
            }
            if (!book.isRead && book.status != "Gelesen" && book.pageCount > 0) {
                val currentProgress = (pagesRead.toInt().coerceIn(0, book.pageCount) * 100) / book.pageCount
                Text("Fortschritt: $currentProgress %", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Medium)
                Slider(
                    value = pagesRead.coerceIn(0f, book.pageCount.toFloat()),
                    onValueChange = { pagesRead = it },
                    valueRange = 0f..book.pageCount.toFloat(),
                    onValueChangeFinished = {
                        val newPagesRead = pagesRead.toInt()
                        val isNowRead = newPagesRead >= book.pageCount
                        val now = System.currentTimeMillis()
                        val cal = Calendar.getInstance().apply { timeInMillis = now }
                        onProgress(
                            book.copy(
                                pagesRead = newPagesRead,
                                isRead = isNowRead,
                                status = if (isNowRead) "Gelesen" else book.status,
                                finishYear = if (isNowRead) cal.get(Calendar.YEAR) else book.finishYear,
                                finishDate = if (isNowRead) now else book.finishDate,
                            ),
                        )
                    },
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
    formKey: String,
    state: BooksUiState,
    viewModel: BookViewModel,
    onCancel: () -> Unit,
    onSave: (Book) -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val newBookId = remember(formKey) { original?.id ?: java.util.UUID.randomUUID().toString() }

    var lookupText by remember(formKey) { mutableStateOf("") }
    var title by remember(formKey) { mutableStateOf(original?.title.orEmpty()) }
    var author by remember(formKey) { mutableStateOf(original?.author.orEmpty()) }
    var pageCount by remember(formKey) { mutableStateOf(original?.pageCount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var language by remember(formKey) { mutableStateOf(original?.language?.takeIf { it.isNotBlank() } ?: "Deutsch") }
    var cover by remember(formKey) { mutableStateOf(original?.coverImageUrl.orEmpty()) }
    var amazonUrl by remember(formKey) { mutableStateOf(original?.amazonUrl.orEmpty()) }
    var pagesRead by remember(formKey) { mutableStateOf(original?.pagesRead?.toString().orEmpty()) }
    var rating by remember(formKey) { mutableStateOf(original?.rating ?: 0) }
    var isRead by remember(formKey) { mutableStateOf(original?.isRead ?: false) }
    var status by remember(formKey) { mutableStateOf(original?.status.takeIf { !it.isNullOrBlank() } ?: "Ungelesen") }
    var finishYear by remember(formKey) { mutableStateOf(original?.finishYear?.toString().orEmpty()) }
    var startDateText by remember(formKey) { mutableStateOf(dateFormat.format(Date(original?.startDate ?: System.currentTimeMillis()))) }
    var finishDateText by remember(formKey) { mutableStateOf(original?.finishDate?.let { dateFormat.format(Date(it)) }.orEmpty()) }

    LaunchedEffect(lookupText, formKey) {
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
            if (found.language.isNotBlank()) {
                language = when {
                    found.language.contains("en", ignoreCase = true) -> "Englisch"
                    found.language.contains("fr", ignoreCase = true) -> "Französisch"
                    else -> "Deutsch"
                }
            }
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
                        state.lookupLoading -> Text("Suche läuft …", color = Leaf)
                        state.lookupError != null -> Text(state.lookupError, color = MaterialTheme.colorScheme.error)
                        state.lookupResults.isNotEmpty() -> Text("${state.lookupResults.size} Treffer gefunden", color = Leaf)
                    }
                }
                if (state.lookupResults.size > 1) {
                    item {
                        BookDropdownSelector(
                            results = state.lookupResults,
                            selected = state.lookupResult,
                            onSelected = { viewModel.selectLookupResult(it) },
                        )
                    }
                }
            }
            item { FormField("Titel", title, { title = it }) }
            item { FormField("Autor", author, { author = it }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField("Seiten", pageCount, { pageCount = it }, Modifier.weight(1f), KeyboardType.Number)
                    Box(Modifier.weight(1f)) {
                        DropdownField("Sprache", listOf("Deutsch", "Englisch", "Französisch"), language) { language = it }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        DropdownField("Status", listOf("Ungelesen", "Wird gelesen", "Pausiert", "Gelesen"), status) { newStatus ->
                            status = newStatus
                            val now = System.currentTimeMillis()
                            if (newStatus == "Wird gelesen") {
                                startDateText = dateFormat.format(Date(now))
                            } else if (newStatus == "Gelesen") {
                                isRead = true
                                if (finishDateText.isBlank()) {
                                    finishDateText = dateFormat.format(Date(now))
                                }
                            }
                        }
                    }
                    FormField("Beendigungsjahr", finishYear, { finishYear = it }, Modifier.weight(1f), KeyboardType.Number)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "Begonnen am",
                        value = startDateText,
                        onValueChange = { startDateText = it },
                        onPickDate = {
                            val timestamp = try { dateFormat.parse(startDateText.trim())?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                            showDatePicker(context, timestamp) { startDateText = it }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (status == "Gelesen" || isRead) {
                        DateField(
                            label = "Beendet am",
                            value = finishDateText,
                            onValueChange = { finishDateText = it },
                            onPickDate = {
                                val timestamp = try { if (finishDateText.isNotBlank()) dateFormat.parse(finishDateText.trim())?.time ?: System.currentTimeMillis() else System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                                showDatePicker(context, timestamp) { finishDateText = it }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                    Checkbox(checked = isRead, onCheckedChange = {
                        isRead = it
                        if (it) {
                            status = "Gelesen"
                            if (finishDateText.isBlank()) {
                                finishDateText = dateFormat.format(Date(System.currentTimeMillis()))
                            }
                        }
                    })
                    Text("Gelesen")
                }
            }
            if (!isRead && status != "Gelesen") item { FormField("Gelesene Seiten", pagesRead, { pagesRead = it }, keyboardType = KeyboardType.Number) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
                    Button(
                        onClick = {
                            val totalPages = pageCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val year = finishYear.toIntOrNull()
                            val isCompleted = status == "Gelesen" || isRead
                            val now = System.currentTimeMillis()
                            val cal = Calendar.getInstance().apply { timeInMillis = now }

                            val parsedStart = try { dateFormat.parse(startDateText.trim())?.time ?: (original?.startDate ?: now) } catch (_: Exception) { original?.startDate ?: now }
                            val parsedFinish = try { if (finishDateText.isNotBlank()) dateFormat.parse(finishDateText.trim())?.time else null } catch (_: Exception) { null }

                            val derivedFinishYear = if (parsedFinish != null) {
                                val finishCal = Calendar.getInstance().apply { timeInMillis = parsedFinish }
                                finishCal.get(Calendar.YEAR)
                            } else {
                                year ?: cal.get(Calendar.YEAR)
                            }

                            onSave(
                                Book(
                                    id = newBookId,
                                    title = title.trim(),
                                    author = author.trim(),
                                    pageCount = totalPages,
                                    language = language.trim(),
                                    rating = rating,
                                    isRead = isCompleted,
                                    coverImageUrl = cover.trim(),
                                    amazonUrl = amazonUrl.trim(),
                                    pagesRead = if (isCompleted) totalPages else (pagesRead.toIntOrNull() ?: 0).coerceIn(0, totalPages.takeIf { it > 0 } ?: Int.MAX_VALUE),
                                    status = if (isCompleted) "Gelesen" else status,
                                    finishYear = if (isCompleted) derivedFinishYear else year,
                                    startDate = parsedStart,
                                    finishDate = if (isCompleted) (parsedFinish ?: now) else parsedFinish,
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
private fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(onClick = onPickDate) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Datum auswählen", tint = Leaf)
            }
        },
    )
}

private fun showDatePicker(
    context: android.content.Context,
    initialTimestamp: Long,
    onDateSelected: (String) -> Unit,
) {
    val cal = Calendar.getInstance()
    cal.timeInMillis = if (initialTimestamp > 0) initialTimestamp else System.currentTimeMillis()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(context, { _, y, m, d ->
        val selectedCal = Calendar.getInstance()
        selectedCal.set(y, m, d)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        onDateSelected(dateFormat.format(selectedCal.time))
    }, year, month, day).show()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DropdownField(label: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.ifBlank { options.first() },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
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
private fun BookCover(url: String, amazonUrl: String = "", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier.clip(RoundedCornerShape(6.dp))
            .background(SoftGreen)
            .clickable(enabled = amazonUrl.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(amazonUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
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
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text("Abbrechen") } },
    )
}

private fun progress(book: Book): Int = when {
    book.isRead || book.status == "Gelesen" -> 100
    book.pageCount <= 0 -> 0
    else -> (book.pagesRead.coerceIn(0, book.pageCount) * 100) / book.pageCount
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookDropdownSelector(
    results: List<GoogleBook>,
    selected: GoogleBook?,
    onSelected: (GoogleBook) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (results.size > 1) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selected?.let { "${it.title} – ${it.author.ifBlank { "Unbekannt" }} (${it.pageCount} S.)" } ?: "Ergebnis wählen...",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text("Suchergebnis wählen (${results.size} Treffer)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                results.forEach { book ->
                    DropdownMenuItem(
                        text = { Text("${book.title} – ${book.author.ifBlank { "Unbekannter Autor" }} (${book.pageCount} Seiten)") },
                        onClick = {
                            onSelected(book)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
