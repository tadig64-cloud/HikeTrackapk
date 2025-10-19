package com.hikemvp.safety

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R

class SafetyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safety)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCall112).setOnClickListener {
            val uri = Uri.parse("tel:" + getString(R.string.ht_emergency_number))
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShareSos).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.ht_sos_share_text))
            }
            startActivity(Intent.createChooser(share, getString(R.string.menu_sos)))
        }
    }
}
