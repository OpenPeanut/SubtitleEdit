package com.peanut.subtitleedit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

object FileChooser {
    var onChooseFile: ((Uri) -> Unit)? = null
    lateinit var requestFile: ActivityResultLauncher<Array<String>>

    fun ComponentActivity.registerFileChooser() {
        requestFile = this.registerForActivityResult(FileChooseContracts()) { r ->
            r?.forEach { onChooseFile?.invoke(it) }
            onChooseFile = null
        }
    }
}

class FileChooseContracts : ActivityResultContract<Array<String>, List<Uri>?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .setType("*/*")
    }

    override fun getSynchronousResult(
        context: Context,
        input: Array<String>
    ): SynchronousResult<List<Uri>?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri>? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.let { int ->
            val clipData = int.clipData
            val data = int.data
            if (clipData != null) {
                MutableList(clipData.itemCount) { clipData.getItemAt(it).uri }
            } else {
                mutableListOf(data)
            }
        }?.filterNotNull()?.toList()
    }
}