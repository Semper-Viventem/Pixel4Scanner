package ru.semper_viventem.pixel4scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val DIRECTORY = "sharing_files/"

fun shareBitmapAsImage(activity: Activity, depthMap: Bitmap, photo: Bitmap, json : String) {
    val cachePath = File(activity.externalCacheDir, DIRECTORY)
    val jsonUri = saveAsFile(activity,json);
    val depthUri = saveAsFile(activity, scaleDepthBitmap(depthMap))
    val photoUri = saveAsFile(activity, photo)
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_STREAM, arrayListOf(depthUri, photoUri,jsonUri))
        putExtra(Intent.EXTRA_TITLE, activity.getString(R.string.share_via))
        type = "*/*"
    }
    activity.startActivity(intent)
}

private fun scaleDepthBitmap(depthMap: Bitmap): Bitmap {
    val (min, max) = depthMap.findMinMaxColorsValues()
    val newMap = depthMap.copy(Bitmap.Config.ARGB_8888, true)
    for (x in 0 until depthMap.width) {
        for (y in 0 until depthMap.height) {
            if (depthMap.getPixel(x, y) == 0) break
            val channelValue = depthMap.getColor(x, y).blue()
            val newValue = (channelValue - min) / (max - min)
            newMap.setPixel(x, y, Color.rgb(newValue, newValue, newValue))
        }
    }
    return newMap
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

private fun saveAsFile(activity: Activity, text: String): Uri {
    val cachePath = File(activity.externalCacheDir, DIRECTORY)
    cachePath.mkdir()

    val file = File(cachePath, "depth_map_${System.currentTimeMillis()}.xyz")
    file.writeText(text);
    
    return FileProvider.getUriForFile(
        activity,
        activity.applicationContext.packageName + ".provider",
        file
    )
}

private fun Bitmap.findMinMaxColorsValues(): Pair<Float, Float> {
    var min = Float.MAX_VALUE
    var max = 0f
    for (x in 0 until width) {
        for (y in 0 until height) {
            val channelValue = getColor(x, y).blue()
            if (channelValue > max) max = channelValue
            if (channelValue < min) min = channelValue
        }
    }

    return min to max
}