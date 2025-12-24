package com.peanut.subtitleedit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.peanut.subtitleedit.FileChooser.onChooseFile
import com.peanut.subtitleedit.FileChooser.registerFileChooser
import com.peanut.subtitleedit.FileChooser.requestFile
import com.peanut.subtitleedit.PermissionUtil.permissionGranted

open class FileChooseActivity : ComponentActivity() {
    init {
        registerFileChooser()
    }

    fun fileChooser(vararg mimeType: String = arrayOf("application/*"), func: (Uri) -> Unit) {
        assert(onChooseFile == null) {
            "You cannot execute multiple file selection operations at once."
        }
        onChooseFile = func
        requestFile.launch(arrayOf(*mimeType))
    }

    // begin 相册权限
    private val requestReadMediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (permissionGranted(this, emptyArray()) or result.all { it.value }) {
                val it = onRequestMedias ?: return@registerForActivityResult
                onRequestMedias = null
                it()
            }
        }

    private var onRequestMedias: (() -> Unit)? = null

    fun requestMediaPermission(func: () -> Unit) {
        assert(onRequestMedias == null) {
            "You cannot execute multiple media selection operations at once."
        }
        onRequestMedias = func
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildList {
                add(element = Manifest.permission.READ_MEDIA_VIDEO)
                add(element = Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(element = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            }.toTypedArray()
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissionGranted(context = this, permissions = permissions)) {
            onRequestMedias = null
            func()
        } else {
            requestReadMediaPermissionLauncher.launch(permissions)
        }
    }
    // end 相册权限
}

object PermissionUtil {
    fun permissionGranted(context: Context, permissions: Array<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && permissionGranted(
                context = context,
                permission = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        ) {
            return true
        }
        if (permissions.isEmpty()) return false
        return permissions.all {
            permissionGranted(context = context, permission = it)
        }
    }

    private fun permissionGranted(context: Context, permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}