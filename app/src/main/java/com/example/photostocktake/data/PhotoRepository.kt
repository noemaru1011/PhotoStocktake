package com.example.photostocktake.data

import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import com.example.photostocktake.scheduler.NotificationScheduler

interface PhotoRepository {
    fun initializeBaselineIfNeeded()
    fun loadPhotos(): List<PhotoItem>
    fun keepPhoto(photoId: Long)
    fun snoozePhoto(photo: PhotoItem, reviewAtMillis: Long)
    fun deletePhotos(photoIds: Collection<Long>)
}

class PhotoRepositoryImpl(
    private val localDataSource: PhotoLocalDataSource,
    private val preferencesDataSource: PreferencesDataSource,
    private val notificationScheduler: NotificationScheduler,
) : PhotoRepository {

    override fun initializeBaselineIfNeeded() {
        if (preferencesDataSource.getBaseline() < 0L) {
            val nowSeconds = System.currentTimeMillis() / 1000L
            preferencesDataSource.setBaseline(nowSeconds)
        }
    }

    override fun loadPhotos(): List<PhotoItem> {
        val baseline = preferencesDataSource.getBaseline()
        if (baseline < 0L) return emptyList()

        val rawPhotos = localDataSource.getPhotosAddedAfter(baseline)

        return rawPhotos.mapNotNull { raw ->
            val status = preferencesDataSource.getPhotoStatus(raw.id)
            if ((status == PhotoStatus.KEPT) || (status == PhotoStatus.DELETED)) {
                return@mapNotNull null
            }

            val reviewAt = preferencesDataSource.getReviewAt(raw.id)
            if (preferencesDataSource.getPhotoName(raw.id) == null) {
                preferencesDataSource.setPhotoName(raw.id, raw.name)
            }

            raw.copy(
                status = status,
                reviewAt = reviewAt,
            )
        }
    }

    override fun keepPhoto(photoId: Long) {
        preferencesDataSource.setPhotoStatus(photoId, PhotoStatus.KEPT)
        preferencesDataSource.clearReviewAt(photoId)
        notificationScheduler.cancelCustom(photoId)
    }

    override fun snoozePhoto(photo: PhotoItem, reviewAtMillis: Long) {
        preferencesDataSource.setPhotoStatus(photo.id, PhotoStatus.SNOOZED)
        preferencesDataSource.setReviewAt(photo.id, reviewAtMillis)
        preferencesDataSource.setPhotoName(photo.id, photo.name)
        notificationScheduler.scheduleCustom(photo.id, photo.name, reviewAtMillis)
    }

    override fun deletePhotos(photoIds: Collection<Long>) {
        photoIds.forEach { photoId ->
            preferencesDataSource.setPhotoStatus(photoId, PhotoStatus.DELETED)
            preferencesDataSource.clearReviewAt(photoId)
            notificationScheduler.cancelCustom(photoId)
        }
    }
}
