package com.example.photostocktake.ui

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// デザイナー視点でのカラーパレット定義（視認性・コントラスト重視）
private val KeepColor = Color(0xFF2E7D32)      // 安心感・保管を表すエメラルドグリーン
private val TomorrowColor = Color(0xFF1976D2)  // 保留・次回リマインドを表すコバルトブルー
private val CustomTimeColor = Color(0xFF00796B)// 計画・日時指定を表すディープティール
private val DeleteColor = Color(0xFFD32F2F)    // 注意・削除を表すクリムゾンレッド

@Composable
fun PhotoRow(
    photo: PhotoItem,
    onKeep: () -> Unit,
    onTomorrow: () -> Unit,
    onCustomTime: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateText = remember(photo.dateTaken) {
        SimpleDateFormat(
            "yyyy/MM/dd HH:mm",
            Locale.getDefault(),
        ).format(Date(photo.dateTaken))
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { image ->
                    image.setImageURI(photo.uri)
                },
            )

            // 写真下部に視認性の高いグラデーション風オーバレイ領域を配置
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.80f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onKeep,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KeepColor,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "保管する",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }

                    Button(
                        onClick = onTomorrow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TomorrowColor,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "明日再確認",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onCustomTime,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CustomTimeColor,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "日時を指定",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeleteColor,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "削除する",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        Text(
            text = photo.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = "撮影日時：$dateText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if ((photo.status == PhotoStatus.SNOOZED) && (photo.reviewAt > 0L)) {
            val reviewText = remember(photo.reviewAt) {
                SimpleDateFormat(
                    "MM/dd HH:mm",
                    Locale.getDefault(),
                ).format(Date(photo.reviewAt))
            }
            Text(
                text = "再確認：$reviewText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
