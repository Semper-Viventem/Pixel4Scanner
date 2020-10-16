package ru.semper_viventem.pixel4scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val DIRECTORY = "sharing_files/"

fun shareBitmapAsImage(activity: Activity, depthMap: Bitmap, photo: Bitmap) {
    val depthUri = saveAsFile(activity, depthMap)
    val photoUri = saveAsFile(activity, photo)
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_STREAM, arrayListOf(depthUri, photoUri))
        putExtra(Intent.EXTRA_TITLE, activity.getString(R.string.share_via))
        type = "*/*"
    }
    activity.startActivity(intent)
}

private fun saveAsFile(activity: Activity, bitmap: Bitmap): Uri {
    val cachePath = File(activity.externalCacheDir, DIRECTORY)
    cachePath.mkdir()

    val file = File(cachePath, "depth_map_${System.currentTimeMillis()}.png")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
    return FileProvider.getUriForFile(
        activity,
        activity.applicationContext.packageName + ".provider",
        file
    )
}