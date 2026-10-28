package com.example.photostocktake.domain

import com.example.photostocktake.model.PhotoItem

class BurstGroupingUseCase(
    private val thresholdMs: Long = 2000L,
) {
    /**
     * Groups photos by proximity in dateTaken (within [thresholdMs]).
     * Groups are sorted descending by max dateTaken.
     */
    fun execute(photos: List<PhotoItem>): List<List<PhotoItem>> {
        if (photos.isEmpty()) return emptyList()

        val sorted = photos.sortedBy { (_, _, dateTaken) -> dateTaken }
        val result = mutableListOf<List<PhotoItem>>()
        var currentGroup = mutableListOf<PhotoItem>()

        for (photo in sorted) {
            if (
                currentGroup.isNotEmpty() &&
                (photo.dateTaken - currentGroup.last().dateTaken > thresholdMs)
            ) {
                result.add(currentGroup)
                currentGroup = mutableListOf()
            }
            currentGroup.add(photo)
        }
        if (currentGroup.isNotEmpty()) {
            result.add(currentGroup)
        }

        return result.sortedByDescending { group -> group.maxOf { it.dateTaken } }
    }
}
