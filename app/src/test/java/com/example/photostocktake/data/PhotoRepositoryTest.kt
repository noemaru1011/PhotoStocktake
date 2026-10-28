@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package com.example.photostocktake.data

import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import com.example.photostocktake.scheduler.NotificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotoRepositoryTest {

    private lateinit var fakeLocalDataSource: FakePhotoLocalDataSource
    private lateinit var fakePreferencesDataSource: FakePreferencesDataSource
    private lateinit var fakeScheduler: FakeNotificationScheduler
    private lateinit var repository: PhotoRepositoryImpl

    @Before
    fun setup() {
        fakeLocalDataSource = FakePhotoLocalDataSource()
        fakePreferencesDataSource = FakePreferencesDataSource()
        fakeScheduler = FakeNotificationScheduler()
        repository = PhotoRepositoryImpl(
            fakeLocalDataSource,
            fakePreferencesDataSource,
            fakeScheduler,
        )
    }

    @Test
    fun `未設定の場合にベースラインタイムスタンプを初期化保存する`() {
        assertEquals(-1L, fakePreferencesDataSource.getBaseline())
        repository.initializeBaselineIfNeeded()
        assertTrue(fakePreferencesDataSource.getBaseline() > 0L)
    }

    @Test
    fun `保管済みおよび削除済みの写真を一覧から除外する`() {
        fakePreferencesDataSource.setBaseline(100L)
        fakeLocalDataSource.photos = listOf(
            PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 200L, dateAdded = 200L),
            PhotoItem(id = 2L, name = "p2.jpg", dateTaken = 300L, dateAdded = 300L),
            PhotoItem(id = 3L, name = "p3.jpg", dateTaken = 400L, dateAdded = 400L),
        )

        fakePreferencesDataSource.setPhotoStatus(1L, PhotoStatus.KEPT)
        fakePreferencesDataSource.setPhotoStatus(2L, PhotoStatus.PENDING)
        fakePreferencesDataSource.setPhotoStatus(3L, PhotoStatus.DELETED)

        val result = repository.loadPhotos()

        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun `ベースライン未設定時は写真を一覧に含めない`() {
        fakeLocalDataSource.photos = listOf(
            PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 200L, dateAdded = 200L),
        )

        val result = repository.loadPhotos()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `残す操作を行った場合にステータスをKEPTに変更し通知予約を解除する`() {
        repository.keepPhoto(10L)

        assertEquals(PhotoStatus.KEPT, fakePreferencesDataSource.getPhotoStatus(10L))
        assertTrue(fakeScheduler.cancelledCustomIds.contains(10L))
    }

    @Test
    fun `削除承認後の複数写真をDELETEDに更新し再確認通知を解除する`() {
        fakePreferencesDataSource.setPhotoStatus(5L, PhotoStatus.SNOOZED)
        fakePreferencesDataSource.setReviewAt(5L, 99999L)
        fakePreferencesDataSource.setPhotoStatus(6L, PhotoStatus.PENDING)

        repository.deletePhotos(listOf(5L, 6L))

        assertEquals(PhotoStatus.DELETED, fakePreferencesDataSource.getPhotoStatus(5L))
        assertEquals(PhotoStatus.DELETED, fakePreferencesDataSource.getPhotoStatus(6L))
        assertEquals(0L, fakePreferencesDataSource.getReviewAt(5L))
        assertEquals(listOf(5L, 6L), fakeScheduler.cancelledCustomIds)
    }

    @Test
    fun `再確認操作を行った場合にステータスをSNOOZEDに変更し通知を予約する`() {
        val photo = PhotoItem(id = 5L, name = "snooze.jpg", dateTaken = 1000L, dateAdded = 1000L)
        val reviewAt = 99999L

        repository.snoozePhoto(photo, reviewAt)

        assertEquals(PhotoStatus.SNOOZED, fakePreferencesDataSource.getPhotoStatus(5L))
        assertEquals(reviewAt, fakePreferencesDataSource.getReviewAt(5L))
        assertEquals(1, fakeScheduler.scheduledCustoms.size)
        assertEquals(5L, fakeScheduler.scheduledCustoms[0].first)
    }
}

// テスト用フェイクデータソース
class FakePhotoLocalDataSource : PhotoLocalDataSource {
    var photos: List<PhotoItem> = emptyList()

    override fun getPhotosAddedAfter(baselineSeconds: Long): List<PhotoItem> {
        return photos.filter { (_, _, _, dateAdded) -> dateAdded > baselineSeconds }
    }
}

class FakePreferencesDataSource : PreferencesDataSource {
    private var baseline: Long = -1L
    private val statuses = mutableMapOf<Long, PhotoStatus>()
    private val reviewTimes = mutableMapOf<Long, Long>()
    private val names = mutableMapOf<Long, String>()

    override fun getBaseline() = baseline
    override fun setBaseline(baselineSeconds: Long) { baseline = baselineSeconds }

    override fun getPhotoStatus(photoId: Long) = statuses[photoId] ?: PhotoStatus.PENDING
    override fun setPhotoStatus(photoId: Long, status: PhotoStatus) { statuses[photoId] = status }

    override fun getReviewAt(photoId: Long) = reviewTimes[photoId] ?: 0L
    override fun setReviewAt(photoId: Long, reviewAtMillis: Long) { reviewTimes[photoId] = reviewAtMillis }
    override fun clearReviewAt(photoId: Long) { reviewTimes.remove(photoId) }

    override fun getPhotoName(photoId: Long) = names[photoId]
    override fun setPhotoName(photoId: Long, name: String) { names[photoId] = name }
}

class FakeNotificationScheduler : NotificationScheduler {
    var dailyScheduled = false
    val scheduledCustoms = mutableListOf<Triple<Long, String, Long>>()
    val cancelledCustomIds = mutableListOf<Long>()

    override fun scheduleDaily() { dailyScheduled = true }

    override fun scheduleCustom(photoId: Long, photoName: String, timeMillis: Long) {
        scheduledCustoms.add(Triple(photoId, photoName, timeMillis))
    }

    override fun cancelCustom(photoId: Long) {
        cancelledCustomIds.add(photoId)
    }
}
