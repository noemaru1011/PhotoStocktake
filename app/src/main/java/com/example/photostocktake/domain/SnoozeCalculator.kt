package com.example.photostocktake.domain

import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import java.util.Calendar

class SnoozeCalculator(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Calculates the time for tomorrow at 22:00.
     */
    fun getTomorrowTargetTime(): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeProvider()
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * Checks if a photo is due for review based on current status and review timestamp.
     */
    fun isDueForReview(photo: PhotoItem): Boolean {
        val now = timeProvider()
        return when (photo.status) {
            PhotoStatus.PENDING -> true
            PhotoStatus.SNOOZED -> photo.reviewAt <= now
            PhotoStatus.KEPT, PhotoStatus.DELETED -> false
        }
    }
}
