package com.example.photostocktake.scheduler

import android.content.Context
import com.example.photostocktake.ReminderReceiver

interface NotificationScheduler {
    fun scheduleDaily()
    fun scheduleCustom(photoId: Long, photoName: String, timeMillis: Long)
    fun cancelCustom(photoId: Long)
}

class AlarmNotificationScheduler(
    private val context: Context
) : NotificationScheduler {

    override fun scheduleDaily() {
        ReminderReceiver.scheduleDaily(context)
    }

    override fun scheduleCustom(
        photoId: Long,
        photoName: String,
        timeMillis: Long
    ) {
        ReminderReceiver.scheduleCustom(context, photoId, photoName, timeMillis)
    }

    override fun cancelCustom(photoId: Long) {
        ReminderReceiver.cancelCustom(context, photoId)
    }
}
