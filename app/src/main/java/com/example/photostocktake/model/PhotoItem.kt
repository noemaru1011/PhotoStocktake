package com.example.photostocktake.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class PhotoItem(
    val id: Long,
    val name: String,
    val dateTaken: Long,
    val dateAdded: Long,
    val status: PhotoStatus = PhotoStatus.PENDING,
    val reviewAt: Long = 0L,
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )
}
