package com.example.photostocktake.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.photostocktake.data.PhotoRepository
import com.example.photostocktake.domain.BurstGroupingUseCase
import com.example.photostocktake.domain.SnoozeCalculator
import com.example.photostocktake.model.PhotoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PhotoViewModel(
    private val repository: PhotoRepository,
    private val burstGroupingUseCase: BurstGroupingUseCase = BurstGroupingUseCase(),
    private val snoozeCalculator: SnoozeCalculator = SnoozeCalculator()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    init {
        repository.initializeBaselineIfNeeded()
    }

    fun loadPhotos() {
        _uiState.update { it.copy(isLoading = true) }
        val photoList = repository.loadPhotos()
        val groups = burstGroupingUseCase.execute(photoList)
        val pending = photoList.count { photo ->
            snoozeCalculator.isDueForReview(photo)
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                photos = photoList,
                photoGroups = groups,
                pendingCount = pending
            )
        }
    }

    fun keepPhoto(photo: PhotoItem) {
        repository.keepPhoto(photo.id)
        loadPhotos()
    }

    fun snoozeTomorrow(photo: PhotoItem) {
        val targetTime = snoozeCalculator.getTomorrowTargetTime()
        repository.snoozePhoto(photo, targetTime)
        _uiState.update { it.copy(userMessage = "再確認を設定しました") }
        loadPhotos()
    }

    fun snoozeCustom(photo: PhotoItem, timeMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        if (timeMillis <= nowMillis) {
            _uiState.update { it.copy(userMessage = "未来の日時を指定してください") }
            return
        }
        repository.snoozePhoto(photo, timeMillis)
        _uiState.update { it.copy(userMessage = "再確認を設定しました") }
        loadPhotos()
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    class Factory(
        private val repository: PhotoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhotoViewModel(repository) as T
        }
    }
}
