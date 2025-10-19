package com.hikemvp

import android.app.Application
import android.os.Environment
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Dossiers cache/app spécifiques (pas besoin de WRITE_EXTERNAL_STORAGE)
        val base = getExternalFilesDir(null) ?: filesDir
        val osmBase = File(base, "osmdroid").apply { mkdirs() }
        val osmTiles = File(osmBase, "tiles").apply { mkdirs() }

        val cfg = Configuration.getInstance()
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = osmBase
        cfg.osmdroidTileCache = osmTiles
    }
}