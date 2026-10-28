package com.example.photostocktake.ui

import com.example.photostocktake.model.PhotoItem

data class PhotoUiState(
    val isLoading: Boolean = false,
    val photos: List<PhotoItem> = emptyList(),
    val photoGroups: List<List<PhotoItem>> = emptyList(),
    val pendingCount: Int = 0,
    val userMessage: String? = null,
)
