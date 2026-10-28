package com.example.photostocktake.data

import android.content.ContentResolver
import android.provider.MediaStore
import com.example.photostocktake.model.PhotoItem

interface PhotoLocalDataSource {
    fun getPhotosAddedAfter(baselineSeconds: Long): List<PhotoItem>
}

class MediaStorePhotoDataSourceImpl(
    private val contentResolver: ContentResolver
) : PhotoLocalDataSource {

    override fun getPhotosAddedAfter(baselineSeconds: Long): List<PhotoItem> {
        val result = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATE_ADDED} > ?",
                arrayOf(baselineSeconds.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "名前なし"
                    val taken = cursor.getLong(takenCol)
                    val added = cursor.getLong(addedCol)

                    result.add(
                        PhotoItem(
                            id = id,
                            name = name,
                            dateTaken = if (taken > 0L) taken else added * 1000L,
                            dateAdded = added
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted or query denied
        }

        return result
    }
}
