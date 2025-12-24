package com.peanut.subtitleedit

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.peanut.subtitleedit.ui.theme.SubtitleEditTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.widget.Toast
import java.util.Locale

class MainActivity : FileChooseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubtitleEditTheme {
                var selectedFileState by remember { mutableStateOf<Uri?>(null) }
                val context = this@MainActivity
                val processor = remember { SubtitleProcessor(context) }
                val scope = rememberCoroutineScope()
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SubtitleEditScreen(
                        modifier = Modifier.padding(innerPadding),
                        selectedFileUri = selectedFileState,
                        onFileChoose = {
                            fileChooser("*/*") { uri ->
                                selectedFileState = uri
                            }
                        },
                        onClearFile = {
                            selectedFileState = null
                        },
                        onConvert = { file, offset ->
                            if (file == null) {
                                Toast.makeText(context, "请先选择字幕文件", Toast.LENGTH_SHORT).show()
                                return@SubtitleEditScreen
                            }
                            
                            scope.launch {
                                processor.adjustSubtitleTime(
                                    activity = this@MainActivity,
                                    inputUri = file,
                                    offsetSeconds = offset,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "处理成功！文件已保存",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { error ->
                                        Toast.makeText(
                                            context,
                                            "处理失败: $error",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleEditScreen(
    modifier: Modifier = Modifier,
    selectedFileUri: Uri?,
    onFileChoose: () -> Unit,
    onClearFile: () -> Unit,
    onConvert: (Uri?, offset: Double) -> Unit,
) {
    var timeOffset by remember { mutableDoubleStateOf(0.0) } // 偏移量，单位：秒
    var isLongPressingMinus by remember { mutableStateOf(false) }
    var isLongPressingPlus by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    // 从URI获取文件路径
    val selectedFilePath = remember(selectedFileUri) {
        selectedFileUri?.let { uri ->
            // 尝试从URI获取文件名或路径
            val displayName = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        uri.lastPathSegment ?: uri.toString()
                    }
                } ?: (uri.lastPathSegment ?: uri.toString())
            } catch (e: Exception) {
                uri.lastPathSegment ?: uri.toString()
            }
            displayName
        }
    }

    // 长按减号按钮的处理
    LaunchedEffect(isLongPressingMinus) {
        if (isLongPressingMinus) {
            while (isActive && isLongPressingMinus) {
                timeOffset -= 0.1
                delay(200) // 每0.2秒触发一次
            }
        }
    }

    // 长按加号按钮的处理
    LaunchedEffect(isLongPressingPlus) {
        if (isLongPressingPlus) {
            while (isActive && isLongPressingPlus) {
                timeOffset += 0.1
                delay(200) // 每0.2秒触发一次
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 文件选择区域
        FileSelectorCard(
            filePath = selectedFilePath,
            onSelectFile = onFileChoose,
            onClearFile = onClearFile
        )

        // 时间轴调整工具
        TimeOffsetAdjuster(
            offset = timeOffset,
            onMinusClick = {
                timeOffset -= 0.1
            },
            onPlusClick = {
                timeOffset += 0.1
            }
        )

        // 开始处理按钮
        Button(
            onClick = {
                onConvert(selectedFileUri, timeOffset)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("开始处理")
        }
    }
}

@Composable
fun FileSelectorCard(
    filePath: String?,
    onSelectFile: () -> Unit,
    onClearFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelectFile)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = filePath ?: "点击选择文件",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (filePath != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (filePath != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onClearFile),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TimeOffsetAdjuster(
    offset: Double,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 减号按钮
            Box(
                modifier = Modifier
                    .size(56.dp)
            ) {
                Button(
                    onClick = onMinusClick,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 偏移量显示
            Text(
                text = String.format(Locale.CHINA, "%.1f", offset),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 加号按钮
            Box(
                modifier = Modifier
                    .size(56.dp)
            ) {
                Button(
                    onClick = onPlusClick,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SubtitleEditScreenPreview() {
    SubtitleEditTheme {
        SubtitleEditScreen(
            selectedFileUri = null,
            onFileChoose = {},
            onClearFile = {},
            onConvert = { _, _ -> }
        )
    }
}