package com.peanut.subtitleedit

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.peanut.sdk.petlin.FileCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class SubtitleProcessor(private val context: Context) {
    
    suspend fun adjustSubtitleTime(
        activity: MainActivity,
        inputUri: Uri,
        offsetSeconds: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // 获取输入文件信息
            val inputFileName = getFileName(context, inputUri) ?: run {
                onError("无法获取文件名")
                return@withContext
            }
            
            val fileExtension = inputFileName.substringAfterLast('.', "").lowercase()
            if (fileExtension !in listOf("srt", "ass")) {
                onError("不支持的文件格式，仅支持SRT和ASS文件")
                return@withContext
            }
            
            // 创建临时输入文件
            val tempInputFile = File(context.cacheDir, "temp_input_$inputFileName")
            copyUriToFile(context, inputUri, tempInputFile)
            
            // 创建输出文件
            val outputFileName = String.format(Locale.CHINA, "%.1f", offsetSeconds)+"_"+inputFileName
            val outputFile = File(context.cacheDir, outputFileName)
            
            // 尝试使用FFmpeg处理，如果失败则使用直接解析方法
            val ffmpegSuccess = try {
                val offsetStr = formatOffset(offsetSeconds)
                
                val command = if (offsetSeconds != 0.0) {
                    // 对于字幕文件，使用-itsoffset调整时间轴
                    // 注意：对于纯字幕文件，可能需要创建虚拟视频流
                    // 这里先尝试直接处理字幕文件
                    buildList {
                        add("-itsoffset")
                        add(offsetStr)
                        add("-i")
                        add(tempInputFile.absolutePath)
                        add("-c:s")
                        add("copy")
                        add("-y")
                        add(outputFile.absolutePath)
                    }.toTypedArray()
                } else {
                    // 如果偏移量为0，直接复制文件
                    arrayOf(
                        "-i", tempInputFile.absolutePath,
                        "-c:s", "copy",
                        "-y",
                        outputFile.absolutePath
                    )
                }
                
                // 执行FFmpeg命令
                val session = FFmpegKit.execute(command.joinToString(" "))
                val returnCode = session.returnCode
                ReturnCode.isSuccess(returnCode)
            } catch (e: Exception) {
                false
            }
            
            // 如果FFmpeg失败，使用直接解析方法
            if (!ffmpegSuccess) {
                if (offsetSeconds == 0.0) {
                    // 如果偏移量为0，直接复制文件
                    tempInputFile.copyTo(outputFile, overwrite = true)
                } else {
                    // 直接解析字幕文件并调整时间戳
                    adjustSubtitleFileDirectly(tempInputFile, outputFile, offsetSeconds, fileExtension)
                }
            }
            
            // 清理临时文件
            tempInputFile.delete()
            
            // 检查输出文件是否存在
            if (outputFile.exists() && outputFile.length() > 0) {
                writeBack(outputFile, activity)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("处理失败：无法创建输出文件")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("处理过程中发生错误: ${e.message}")
            }
        }
    }

    suspend fun writeBack(path: File, context: Activity) {
        FileCompat.saveFileToPublicDownload(context, "ass2srt", path.name) {
            FileCompat.copyFileUseStream(path.inputStream(), it)
        }
        path.delete()
    }
    
    private fun formatOffset(offsetSeconds: Double): String {
        val totalSeconds = Math.abs(offsetSeconds)
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = totalSeconds % 60
        
        val sign = if (offsetSeconds >= 0) "" else "-"
        return String.format("%s%02d:%02d:%05.2f", sign, hours, minutes, seconds)
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    it.substring(cut + 1)
                } else {
                    it
                }
            }
        }
        return result
    }
    
    private fun copyUriToFile(context: Context, uri: Uri, file: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    private fun adjustSubtitleFileDirectly(
        inputFile: File,
        outputFile: File,
        offsetSeconds: Double,
        fileExtension: String
    ) {
        when (fileExtension) {
            "srt" -> adjustSrtFile(inputFile, outputFile, offsetSeconds)
            "ass" -> adjustAssFile(inputFile, outputFile, offsetSeconds)
            else -> throw IllegalArgumentException("不支持的文件格式: $fileExtension")
        }
    }
    
    private fun adjustSrtFile(inputFile: File, outputFile: File, offsetSeconds: Double) {
        val offsetMs = (offsetSeconds * 1000).toLong()
        outputFile.bufferedWriter().use { writer ->
            inputFile.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!
                    // 检查是否是时间轴行（格式：00:00:00,000 --> 00:00:00,000）
                    if (currentLine.contains("-->")) {
                        val adjustedLine = adjustSrtTimeLine(currentLine, offsetMs)
                        writer.write(adjustedLine)
                    } else {
                        writer.write(currentLine)
                    }
                    writer.newLine()
                }
            }
        }
    }
    
    private fun adjustSrtTimeLine(line: String, offsetMs: Long): String {
        // SRT时间格式：00:00:00,000 --> 00:00:00,000
        val regex = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
        return regex.replace(line) { matchResult ->
            val hours = matchResult.groupValues[1].toInt()
            val minutes = matchResult.groupValues[2].toInt()
            val seconds = matchResult.groupValues[3].toInt()
            val milliseconds = matchResult.groupValues[4].toInt()
            
            val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000L + milliseconds
            val adjustedMs = (totalMs + offsetMs).coerceAtLeast(0)
            
            val adjustedHours = (adjustedMs / 3600000).toInt()
            val adjustedMinutes = ((adjustedMs % 3600000) / 60000).toInt()
            val adjustedSeconds = ((adjustedMs % 60000) / 1000).toInt()
            val adjustedMilliseconds = (adjustedMs % 1000).toInt()
            
            String.format("%02d:%02d:%02d,%03d", adjustedHours, adjustedMinutes, adjustedSeconds, adjustedMilliseconds)
        }
    }
    
    private fun adjustAssFile(inputFile: File, outputFile: File, offsetSeconds: Double) {
        val offsetMs = (offsetSeconds * 1000).toLong()
        outputFile.bufferedWriter().use { writer ->
            inputFile.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!
                    // ASS格式：Dialogue: 0,0:00:00.00,0:00:00.00,...
                    if (currentLine.startsWith("Dialogue:")) {
                        val adjustedLine = adjustAssDialogueLine(currentLine, offsetMs)
                        writer.write(adjustedLine)
                    } else {
                        writer.write(currentLine)
                    }
                    writer.newLine()
                }
            }
        }
    }
    
    private fun adjustAssDialogueLine(line: String, offsetMs: Long): String {
        // ASS Dialogue格式：Dialogue: 0,0:00:00.00,0:00:00.00,Style,Name,0,0,0,,Text
        val parts = line.split(",", limit = 10)
        if (parts.size < 3) return line
        
        try {
            val startTime = parseAssTime(parts[1])
            val endTime = parseAssTime(parts[2])
            
            val adjustedStartTime = (startTime + offsetMs).coerceAtLeast(0)
            val adjustedEndTime = (endTime + offsetMs).coerceAtLeast(0)
            
            val adjustedStartStr = formatAssTime(adjustedStartTime)
            val adjustedEndStr = formatAssTime(adjustedEndTime)
            
            return buildString {
                append(parts[0]) // Dialogue:
                append(",")
                append(adjustedStartStr)
                append(",")
                append(adjustedEndStr)
                for (i in 3 until parts.size) {
                    append(",")
                    append(parts[i])
                }
            }
        } catch (e: Exception) {
            return line
        }
    }
    
    private fun parseAssTime(timeStr: String): Long {
        // ASS时间格式：0:00:00.00 (H:MM:SS.CC，其中CC是百分之一秒)
        val parts = timeStr.split(":")
        if (parts.size != 3) return 0
        
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        val secondsParts = parts[2].split(".")
        val seconds = secondsParts[0].toIntOrNull() ?: 0
        val centiseconds = if (secondsParts.size > 1) secondsParts[1].toIntOrNull() ?: 0 else 0
        
        return (hours * 3600 + minutes * 60 + seconds) * 1000L + centiseconds * 10
    }
    
    private fun formatAssTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        val centiseconds = ((timeMs % 1000) / 10).toInt()
        
        return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
    }
}

