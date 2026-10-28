package com.example.photostocktake.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.photostocktake.model.PhotoItem

@Composable
fun PhotoScreen(
    uiState: PhotoUiState,
    onRefresh: () -> Unit,
    onKeep: (PhotoItem) -> Unit,
    onTomorrow: (PhotoItem) -> Unit,
    onCustomTime: (PhotoItem) -> Unit,
    onDelete: (PhotoItem) -> Unit,
    onDeleteGroup: (List<PhotoItem>) -> Unit
) {
    var groupToDelete by remember { mutableStateOf<List<PhotoItem>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Photo Stocktake",
            style = MaterialTheme.typography.headlineMedium
        )
        Text("未整理：${uiState.pendingCount} 枚")
        Text("毎日22:00に未整理の写真のを通知します。")

        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("写真を更新")
        }

        if (!uiState.isLoading && uiState.photos.isEmpty()) {
            Text("新しい写真はありません。")
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.photoGroups, key = { group -> group.first().id }) { group ->
                val isBurst = group.size > 1

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isBurst) {
                                "連写候補：${group.size} 枚"
                            } else {
                                "写真"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (isBurst) {
                            Text("代表写真を参考に、各写真を個別に残すこともできます。")
                            OutlinedButton(
                                onClick = { groupToDelete = group },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("グループ全体を削除")
                            }
                        }

                        group.forEach { photo ->
                            PhotoRow(
                                photo = photo,
                                onKeep = { onKeep(photo) },
                                onTomorrow = { onTomorrow(photo) },
                                onCustomTime = { onCustomTime(photo) },
                                onDelete = { onDelete(photo) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (groupToDelete != null) {
        val group = groupToDelete!!

        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("グループ全体を削除しますか？") },
            text = {
                Column {
                    Text("対象写真：${group.size} 枚")
                    group.forEach { Text("・${it.name}") }
                    Text("次に表示されるAndroidの確認画面で削除を承認してください。")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup(group)
                        groupToDelete = null
                    }
                ) {
                    Text("削除へ進む")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { groupToDelete = null }
                ) {
                    Text("キャンセル")
                }
            }
        )
    }
}
