@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package com.example.photostocktake.ui

import com.example.photostocktake.data.PhotoRepository
import com.example.photostocktake.domain.BurstGroupingUseCase
import com.example.photostocktake.domain.SnoozeCalculator
import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotoViewModelTest {

    private lateinit var repository: FakePhotoRepository
    private lateinit var viewModel: PhotoViewModel

    @Before
    fun setup() {
        repository = FakePhotoRepository()
        viewModel = PhotoViewModel(
            repository,
            BurstGroupingUseCase(thresholdMs = 2000L),
            SnoozeCalculator { 100_000L },
        )
    }

    @Test
    fun `写真読込時に期限到来枚数と連写グループを状態に反映する`() {
        repository.photos = listOf(
            PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 1000L, dateAdded = 1000L),
            PhotoItem(id = 2L, name = "p2.jpg", dateTaken = 2000L, dateAdded = 2000L,
                status = PhotoStatus.SNOOZED, reviewAt = 200_000L),
            PhotoItem(id = 3L, name = "p3.jpg", dateTaken = 5000L, dateAdded = 5000L),
        )

        viewModel.loadPhotos()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.pendingCount)
        assertEquals(2, state.photoGroups.size)
        assertEquals(listOf(1, 2), state.photoGroups.map { it.size })
    }

    @Test
    fun `過去日時の再確認指定は保存せずエラーメッセージを表示する`() {
        val photo = PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 1000L, dateAdded = 1000L)

        viewModel.snoozeCustom(photo, timeMillis = 99_999L, nowMillis = 100_000L)

        assertTrue(repository.snoozedIds.isEmpty())
        assertEquals("未来の日時を指定してください", viewModel.uiState.value.userMessage)
    }

    @Test
    fun `削除承認済み写真を削除済みに更新して一覧を再読込する`() {
        val photo = PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 1000L, dateAdded = 1000L)
        repository.photos = listOf(photo)

        viewModel.deletePhotos(listOf(photo))

        assertEquals(listOf(1L), repository.deletedIds)
        assertTrue(repository.photos.isEmpty())
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }
}

private class FakePhotoRepository : PhotoRepository {
    var photos: List<PhotoItem> = emptyList()

    val snoozedIds = mutableListOf<Long>()
    val deletedIds = mutableListOf<Long>()

    override fun initializeBaselineIfNeeded() = Unit

    override fun loadPhotos(): List<PhotoItem> = photos

    override fun keepPhoto(photoId: Long) {
        photos = photos.filterNot { it.id == photoId }
    }

    override fun snoozePhoto(photo: PhotoItem, reviewAtMillis: Long) {
        snoozedIds.add(photo.id)
    }

    override fun deletePhotos(photoIds: Collection<Long>) {
        deletedIds.addAll(photoIds)
        photos = photos.filterNot { it.id in photoIds }
    }
}
