package com.example.photostocktake.data

import android.content.Context
import android.content.SharedPreferences
import com.example.photostocktake.model.PhotoStatus

interface PreferencesDataSource {
    fun getBaseline(): Long
    fun setBaseline(baselineSeconds: Long)
    fun getPhotoStatus(photoId: Long): PhotoStatus
    fun setPhotoStatus(photoId: Long, status: PhotoStatus)
    fun getReviewAt(photoId: Long): Long
    fun setReviewAt(photoId: Long, reviewAtMillis: Long)
    fun clearReviewAt(photoId: Long)
    fun getPhotoName(photoId: Long): String?
    fun setPhotoName(photoId: Long, name: String)
}

class SharedPreferencesDataSourceImpl(
    context: Context,
) : PreferencesDataSource {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "photo_stocktake_settings"
        private const val KEY_BASELINE = "first_launch_baseline_seconds"
    }

    override fun getBaseline(): Long {
        return prefs.getLong(KEY_BASELINE, -1L)
    }

    override fun setBaseline(baselineSeconds: Long) {
        prefs.edit().putLong(KEY_BASELINE, baselineSeconds).apply()
    }

    override fun getPhotoStatus(photoId: Long): PhotoStatus {
        val raw = prefs.getString("status_$photoId", PhotoStatus.PENDING.name)
        return try {
            PhotoStatus.valueOf(raw ?: PhotoStatus.PENDING.name)
        } catch (_: IllegalArgumentException) {
            PhotoStatus.PENDING
        }
    }

    override fun setPhotoStatus(photoId: Long, status: PhotoStatus) {
        prefs.edit().putString("status_$photoId", status.name).apply()
    }

    override fun getReviewAt(photoId: Long): Long {
        return prefs.getLong("review_$photoId", 0L)
    }

    override fun setReviewAt(photoId: Long, reviewAtMillis: Long) {
        prefs.edit().putLong("review_$photoId", reviewAtMillis).apply()
    }

    override fun clearReviewAt(photoId: Long) {
        prefs.edit().remove("review_$photoId").apply()
    }

    override fun getPhotoName(photoId: Long): String? {
        return prefs.getString("name_$photoId", null)
    }

    override fun setPhotoName(photoId: Long, name: String) {
        prefs.edit().putString("name_$photoId", name).apply()
    }
}
