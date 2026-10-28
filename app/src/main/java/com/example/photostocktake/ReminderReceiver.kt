
package com.example.photostocktake

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

private const val PREFS_NAME = "photo_stocktake_settings"
private const val KEY_BASELINE = "first_launch_baseline_seconds"
private const val CHANNEL_ID = "photo_stocktake_reminders"

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DAILY = "com.example.photostocktake.DAILY"
        const val ACTION_CUSTOM = "com.example.photostocktake.CUSTOM"
        const val EXTRA_PHOTO_ID = "photo_id"
        const val EXTRA_PHOTO_NAME = "photo_name"

        fun scheduleDaily(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DAILY
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                2200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.timeInMillis,
                pendingIntent,
            )
        }

        fun scheduleCustom(
            context: Context,
            photoId: Long,
            photoName: String,
            timeMillis: Long,
        ) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_CUSTOM
                putExtra(EXTRA_PHOTO_ID, photoId)
                putExtra(EXTRA_PHOTO_NAME, photoName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (photoId xor (photoId ushr 32)).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeMillis,
                pendingIntent,
            )
        }

        fun cancelCustom(context: Context, photoId: Long) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_CUSTOM
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (photoId xor (photoId ushr 32)).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        createChannel(context)

        when (intent.action) {
            ACTION_DAILY -> {
                val count = countDuePhotos(context)
                if (count > 0) {
                    showNotification(
                        context,
                        2200,
                        "写真の棚卸し",
                        "未整理の写真が $count 枚あります。",
                    )
                }
                scheduleDaily(context)
            }

            ACTION_CUSTOM -> {
                val id = intent.getLongExtra(EXTRA_PHOTO_ID, -1L)
                val name = intent.getStringExtra(EXTRA_PHOTO_NAME) ?: "写真"

                if (id >= 0L) {
                    val prefs = context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )
                    val status = prefs.getString("status_$id", "PENDING")

                    if (status == "SNOOZED") {
                        showNotification(
                            context,
                            (id xor (id ushr 32)).toInt(),
                            "写真を再確認",
                            "$name を確認してください。",
                        )
                    }
                }
            }
        }
    }

    private fun countDuePhotos(context: Context): Int {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val baseline = prefs.getLong(KEY_BASELINE, -1L)
        if (baseline < 0L) return 0

        val nowSeconds = System.currentTimeMillis() / 1000L
        var count = 0

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATE_ADDED} > ?",
                arrayOf(baseline.toString()),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media._ID,
                )
                val addedColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_ADDED,
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val added = cursor.getLong(addedColumn)
                    val status = prefs.getString("status_$id", "PENDING")
                    val reviewAt = prefs.getLong("review_$id", 0L)

                    if ((status == "KEPT") || (status == "DELETED")) continue

                    if (status == "SNOOZED") {
                        if ((reviewAt > 0L) && (reviewAt / 1000L <= nowSeconds)) {
                            count++
                        }
                    } else if (added > baseline) {
                        count++
                    }
                }
            }
        } catch (_: SecurityException) {
            return 0
        }

        return count
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "写真の棚卸し",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(
        context: Context,
        id: Int,
        title: String,
        message: String
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderReceiver.scheduleDaily(context)

            val prefs = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            for ((key, value) in prefs.all) {
                if (!key.startsWith("review_")) continue

                val id = key.removePrefix("review_").toLongOrNull() ?: continue
                val reviewAt = value as? Long ?: continue
                if (reviewAt <= System.currentTimeMillis()) continue

                if (prefs.getString("status_$id", "") == "SNOOZED") {
                    val name = prefs.getString("name_$id", "写真") ?: "写真"
                    ReminderReceiver.scheduleCustom(
                        context,
                        id,
                        name,
                        reviewAt
                    )
                }
            }
        }
    }
}
