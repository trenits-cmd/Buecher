package de.buecher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import de.buecher.app.data.BookRepository
import de.buecher.app.ui.BookViewModel
import de.buecher.app.ui.BooksApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = BookRepository(applicationContext)
        setContent {
            val bookViewModel: BookViewModel = viewModel(factory = BookViewModel.Factory(repository))
            BooksApp(viewModel = bookViewModel)
        }
    }
}