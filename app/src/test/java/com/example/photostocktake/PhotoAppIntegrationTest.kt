@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package com.example.photostocktake

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import com.example.photostocktake.data.PhotoLocalDataSource
import com.example.photostocktake.data.PhotoRepositoryImpl
import com.example.photostocktake.data.PreferencesDataSource
import com.example.photostocktake.data.SharedPreferencesDataSourceImpl
import com.example.photostocktake.domain.BurstGroupingUseCase
import com.example.photostocktake.domain.SnoozeCalculator
import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import com.example.photostocktake.scheduler.NotificationScheduler
import com.example.photostocktake.ui.PhotoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoAppIntegrationTest {

    private lateinit var context: Context
    private lateinit var preferences: PreferencesDataSource
    private lateinit var localDataSource: FakePhotoLocalDataSource
    private lateinit var scheduler: RecordingScheduler
    private lateinit var repository: PhotoRepositoryImpl

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        preferences = SharedPreferencesDataSourceImpl(context)
        localDataSource = FakePhotoLocalDataSource()
        scheduler = RecordingScheduler()
        repository = PhotoRepositoryImpl(localDataSource, preferences, scheduler)
    }

    @Test
    fun `1 初回起動で基準日時以降の写真だけを初期一覧に保存して読込できる`() {
        repository.initializeBaselineIfNeeded()
        val baseline = preferences.getBaseline()
        val oldPhoto = photo(1, dateAdded = baseline - 1)
        val newPhoto = photo(2, dateAdded = baseline + 1)
        localDataSource.photos = listOf(oldPhoto, newPhoto)

        val loaded = repository.loadPhotos()

        assertTrue(baseline > 0)
        assertEquals(listOf(2L), loaded.map { it.id })
    }

    @Test
    fun `2 保管操作は状態と再確認予約を解除して一覧から除外する`() {
        givenBaselineAndPhoto()
        val photo = repository.loadPhotos().single()
        preferences.setReviewAt(photo.id, 12345L)

        val viewModel = createViewModel()
        viewModel.keepPhoto(photo)

        assertEquals(PhotoStatus.KEPT, preferences.getPhotoStatus(photo.id))
        assertEquals(0L, preferences.getReviewAt(photo.id))
        assertEquals(listOf(photo.id), scheduler.cancelledIds)
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    @Test
    fun `3 明日再確認は翌日22時に保存予約し期限まで一覧件数から除外する`() {
        givenBaselineAndPhoto()
        val photo = repository.loadPhotos().single()
        val now = fixedCalendar(2026, Calendar.MARCH, 1, 10).timeInMillis
        val viewModel = createViewModel(now)

        viewModel.snoozeTomorrow(photo)

        val expected = fixedCalendar(2026, Calendar.MARCH, 2, 22).timeInMillis
        assertEquals(PhotoStatus.SNOOZED, preferences.getPhotoStatus(photo.id))
        assertEquals(expected, preferences.getReviewAt(photo.id))
        assertEquals(expected, scheduler.customReservations.single().timeMillis)
        assertEquals(0, viewModel.uiState.value.pendingCount)
    }

    @Test
    fun `4 日時指定は未来時刻だけ保存し過去時刻は保存しない`() {
        givenBaselineAndPhoto()
        val photo = repository.loadPhotos().single()
        val viewModel = createViewModel()
        val now = 1_000_000L
        val future = now + 60_000L

        viewModel.snoozeCustom(photo, future, now)
        assertEquals(future, preferences.getReviewAt(photo.id))
        assertEquals(1, scheduler.customReservations.size)

        viewModel.snoozeCustom(photo, now - 1L, now)
        assertEquals("未来の日時を指定してください", viewModel.uiState.value.userMessage)
        assertEquals(future, preferences.getReviewAt(photo.id))
        assertEquals(1, scheduler.customReservations.size)
    }

    @Test
    fun `5 削除承認後だけDELETEDと通知解除を保存する`() {
        givenBaselineAndPhoto()
        val photo = repository.loadPhotos().single()
        preferences.setReviewAt(photo.id, 12345L)
        val viewModel = createViewModel()

        viewModel.deletePhotos(listOf(photo))

        assertEquals(PhotoStatus.DELETED, preferences.getPhotoStatus(photo.id))
        assertEquals(0L, preferences.getReviewAt(photo.id))
        assertEquals(listOf(photo.id), scheduler.cancelledIds)
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    @Test
    fun `6 連写グループ全体の承認削除を個別保管状態を維持して処理する`() {
        val viewModel = createViewModel()
        val photos = listOf(
            photo(1, dateTaken = 1000),
            photo(2, dateTaken = 3000),
        )
        localDataSource.photos = photos
        preferences.setBaseline(0)

        preferences.setPhotoStatus(1, PhotoStatus.KEPT)
        val remaining = repository.loadPhotos()
        assertEquals(listOf(2L), remaining.map { it.id })

        viewModel.deletePhotos(remaining)
        val groups = BurstGroupingUseCase(thresholdMs = 2000).execute(viewModel.uiState.value.photos)
        assertTrue(groups.isEmpty())
        assertEquals(PhotoStatus.KEPT, preferences.getPhotoStatus(1))
        assertEquals(PhotoStatus.DELETED, preferences.getPhotoStatus(2))
    }

    @Test
    fun `7 日次通知対象は未整理と期限到来だけで0件では通知しない`() {
        preferences.setBaseline(0)
        localDataSource.photos = listOf(
            photo(1),
            photo(2, status = PhotoStatus.SNOOZED, reviewAt = 1L),
            photo(3, status = PhotoStatus.KEPT),
            photo(4, status = PhotoStatus.DELETED),
        )
        preferences.setPhotoStatus(2, PhotoStatus.SNOOZED)
        preferences.setReviewAt(2, 1L)
        preferences.setPhotoStatus(3, PhotoStatus.KEPT)
        preferences.setPhotoStatus(4, PhotoStatus.DELETED)

        val due = repository.loadPhotos().count {
            SnoozeCalculator { 100_000L }.isDueForReview(it)
        }
        assertEquals(2, due)

        localDataSource.photos = localDataSource.photos.map { it.copy(status = PhotoStatus.KEPT) }
        localDataSource.photos.forEach { preferences.setPhotoStatus(it.id, PhotoStatus.KEPT) }
        assertEquals(0, repository.loadPhotos().size)
    }

    @Test
    fun `8 個別再確認通知は期限到来時にSNOOZEDの写真だけ予約する`() {
        val photo = photo(8, status = PhotoStatus.SNOOZED)
        repository.snoozePhoto(photo, 200_000L)

        assertEquals(1, scheduler.customReservations.size)
        assertEquals(photo.name, scheduler.customReservations.single().photoName)

        repository.keepPhoto(photo.id)
        assertFalse(scheduler.customReservations.single().photoName.isEmpty())
        assertEquals(PhotoStatus.KEPT, preferences.getPhotoStatus(photo.id))
    }

    @Test
    fun `9 再起動時は未到来の個別予約と日次予約だけ再スケジュールする`() {
        val now = System.currentTimeMillis()
        preferences.setBaseline(0)
        preferences.setPhotoStatus(1, PhotoStatus.SNOOZED)
        preferences.setReviewAt(1, now + 60_000)
        preferences.setPhotoName(1, "future.jpg")
        preferences.setPhotoStatus(2, PhotoStatus.SNOOZED)
        preferences.setReviewAt(2, now - 60_000)
        preferences.setPhotoName(2, "past.jpg")
        preferences.setPhotoStatus(3, PhotoStatus.KEPT)
        preferences.setReviewAt(3, now + 120_000)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val alarmShadow = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(2, alarmShadow.scheduledAlarms.size)
    }

    @Test
    fun `10 写真権限がない状態でも読込を安全に空結果として扱える`() {
        preferences.setBaseline(0)
        localDataSource.isDenied = true

        assertTrue(repository.loadPhotos().isEmpty())
        assertEquals(-1, context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES))
    }

    @Test
    fun `11 再確認状態と基準日時を別Repositoryで再生成しても維持する`() {
        repository.initializeBaselineIfNeeded()
        val baseline = preferences.getBaseline()
        repository.snoozePhoto(photo(11), 777_777L)

        val recreated = PhotoRepositoryImpl(localDataSource, SharedPreferencesDataSourceImpl(context), scheduler)
        recreated.keepPhoto(11)

        assertEquals(baseline, recreated.loadPhotos().let { preferences.getBaseline() })
        assertEquals(PhotoStatus.KEPT, preferences.getPhotoStatus(11))
        assertEquals(0L, preferences.getReviewAt(11))
    }

    @Test
    fun `12 保管削除再確認の複合操作が最新状態に収束する`() {
        givenBaselineAndPhoto()
        val photo = repository.loadPhotos().single()
        val viewModel = createViewModel()

        viewModel.keepPhoto(photo)
        viewModel.snoozeCustom(photo, 200_000L, 100_000L)
        assertEquals(PhotoStatus.SNOOZED, preferences.getPhotoStatus(photo.id))

        viewModel.deletePhotos(listOf(photo))
        assertEquals(PhotoStatus.DELETED, preferences.getPhotoStatus(photo.id))
        assertEquals(0L, preferences.getReviewAt(photo.id))
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    private fun givenBaselineAndPhoto() {
        preferences.setBaseline(100)
        localDataSource.photos = listOf(photo(1, dateAdded = 101))
    }

    private fun createViewModel(nowMillis: Long = 100_000L): PhotoViewModel {
        val viewModel = PhotoViewModel(
            repository,
            BurstGroupingUseCase(thresholdMs = 2000),
            SnoozeCalculator { nowMillis },
        )
        viewModel.loadPhotos()
        return viewModel
    }

    private fun photo(
        id: Long,
        dateTaken: Long = id * 1000,
        dateAdded: Long = id * 1000,
        status: PhotoStatus = PhotoStatus.PENDING,
        reviewAt: Long = 0,
    ) = PhotoItem(
        id = id,
        name = "photo-$id.jpg",
        dateTaken = dateTaken,
        dateAdded = dateAdded,
        status = status,
        reviewAt = reviewAt,
    )

    private fun fixedCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ) = Calendar.getInstance().apply {
        set(year, month, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        private const val PREFS_NAME = "photo_stocktake_settings"
    }
}

private class FakePhotoLocalDataSource : PhotoLocalDataSource {
    var photos: List<PhotoItem> = emptyList()
    var isDenied = false

    override fun getPhotosAddedAfter(baselineSeconds: Long): List<PhotoItem> {
        if (isDenied) return emptyList()
        return photos.filter { it.dateAdded > baselineSeconds }
    }
}

private data class CustomReservation(
    val photoName: String,
    val timeMillis: Long,
)

private class RecordingScheduler : NotificationScheduler {
    val customReservations = mutableListOf<CustomReservation>()
    val cancelledIds = mutableListOf<Long>()

    override fun scheduleDaily() = Unit

    override fun scheduleCustom(photoId: Long, photoName: String, timeMillis: Long) {
        customReservations.add(CustomReservation(photoName, timeMillis))
    }

    override fun cancelCustom(photoId: Long) {
        cancelledIds.add(photoId)
    }
}
