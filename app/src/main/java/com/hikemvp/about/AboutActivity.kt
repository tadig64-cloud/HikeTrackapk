package com.hikemvp.about

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- UI 100% en code : aucune ressource XML requise ---
        val sv = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * resources.displayMetrics.density).toInt())
        }
        fun title(txt: String) = TextView(this).apply {
            text = txt
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun label(txt: String) = TextView(this).apply {
            text = txt
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun value(txt: String) = TextView(this).apply { text = txt }

        val pm = packageManager
        val pkg = packageName
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
        }

        val appLabel = applicationInfo.loadLabel(pm).toString()
        val versionName = info.versionName ?: "0"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString()
                          else @Suppress("DEPRECATION") info.versionCode.toString()
        val buildType = runCatching {
            val cls = Class.forName("$pkg.BuildConfig")
            cls.getField("BUILD_TYPE").get(null) as? String ?: "unknown"
        }.getOrElse { "unknown" }
        val installed = DateFormat.format("yyyy-MM-dd  HH:mm", info.firstInstallTime).toString()
        val updated   = DateFormat.format("yyyy-MM-dd  HH:mm", info.lastUpdateTime).toString()

        root.addView(title("$appLabel — À propos"))
        root.addView(label("Version"));    root.addView(value("$versionName ($versionCode)"))
        root.addView(label("Type de build")); root.addView(value(buildType))
        root.addView(label("Package"));    root.addView(value(pkg))
        root.addView(label("Installée"));  root.addView(value(installed))
        root.addView(label("Mise à jour"));root.addView(value(updated))
        root.addView(label("Créateur"));   root.addView(value("HikeTrack — Bruno & co"))
        root.addView(label("Création"));   root.addView(value("2025"))

        sv.addView(root)
        setContentView(sv)

        supportActionBar?.title = "À propos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
