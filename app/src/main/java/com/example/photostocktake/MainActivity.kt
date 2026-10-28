package com.example.photostocktake

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.photostocktake.data.MediaStorePhotoDataSourceImpl
import com.example.photostocktake.data.PhotoRepositoryImpl
import com.example.photostocktake.data.SharedPreferencesDataSourceImpl
import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.scheduler.AlarmNotificationScheduler
import com.example.photostocktake.ui.PhotoScreen
import com.example.photostocktake.ui.PhotoViewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val viewModel: PhotoViewModel by viewModels {
        val prefsSource = SharedPreferencesDataSourceImpl(applicationContext)
        val localSource = MediaStorePhotoDataSourceImpl(contentResolver)
        val scheduler = AlarmNotificationScheduler(applicationContext)
        val repo = PhotoRepositoryImpl(localSource, prefsSource, scheduler)
        PhotoViewModel.Factory(repo)
    }

    private val photoPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.loadPhotos()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            ReminderReceiver.scheduleDaily(this)
        }

    private val deleteLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) {
            viewModel.loadPhotos()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.userMessage) {
                uiState.userMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearUserMessage()
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhotoScreen(
                        uiState = uiState,
                        onRefresh = { requestPhotoPermission() },
                        onKeep = { photo -> viewModel.keepPhoto(photo) },
                        onTomorrow = { photo -> viewModel.snoozeTomorrow(photo) },
                        onCustomTime = { photo -> showDateTimePicker(photo) },
                        onDelete = { photo -> confirmDelete(listOf(photo)) },
                        onDeleteGroup = ::confirmDelete,
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (
            (Build.VERSION.SDK_INT >= 33) &&
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            ReminderReceiver.scheduleDaily(this)
        }
    }

    private fun requestPhotoPermission() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        }

        val hasAccess = permissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

        if (hasAccess) {
            viewModel.loadPhotos()
        } else {
            photoPermissionLauncher.launch(permissions)
        }
    }

    private fun showDateTimePicker(photo: PhotoItem) {
        val now = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val selected = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        viewModel.snoozeCustom(photo, selected.timeInMillis)
                    },
                    now[Calendar.HOUR_OF_DAY],
                    now[Calendar.MINUTE],
                    true,
                ).show()
            },
            now[Calendar.YEAR],
            now[Calendar.MONTH],
            now[Calendar.DAY_OF_MONTH],
        ).show()
    }

    private fun confirmDelete(photos: List<PhotoItem>) {
        if (photos.isEmpty()) return

        val uris = photos.map { it.uri }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val request = MediaStore.createDeleteRequest(
                    contentResolver,
                    uris,
                )
                val intentSender = request.intentSender
                deleteLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build(),
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "削除確認を開けませんでした: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                this,
                "このAndroidバージョンでの削除対応は未実装です",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
