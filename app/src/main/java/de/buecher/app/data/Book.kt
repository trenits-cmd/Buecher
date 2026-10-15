package de.buecher.app.data

import java.util.UUID

data class Book(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val author: String = "",
    val pageCount: Int = 0,
    val language: String = "Deutsch", // Deutsch, Englisch, Französisch
    val rating: Int = 0,
    val isRead: Boolean = false,
    val coverImageUrl: String = "",
    val amazonUrl: String = "",
    val pagesRead: Int = 0,
    val status: String = "Ungelesen", // "Gelesen", "Wird gelesen", "Pausiert", "Ungelesen"
    val finishYear: Int? = null,
    val startDate: Long = System.currentTimeMillis(),
    val finishDate: Long? = null,
)

data class GitHubConfig(
    val owner: String = "",
    val repository: String = "",
    val branch: String = "main",
)

data class GoogleBook(
    val title: String,
    val author: String,
    val pageCount: Int,
    val language: String,
    val coverImageUrl: String,
)
