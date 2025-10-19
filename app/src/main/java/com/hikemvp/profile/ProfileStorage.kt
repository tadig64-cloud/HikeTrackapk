package com.hikemvp.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ProfileStorage {
    fun savePhoto(context: Context, bmp: Bitmap): String {
        val dir = File(context.filesDir, "profile")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "avatar.jpg")
        FileOutputStream(f).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return f.absolutePath
    }

    fun loadPhoto(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }
}
