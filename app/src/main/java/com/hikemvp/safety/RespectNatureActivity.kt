package com.hikemvp.safety

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.databinding.ActivityRespectNatureBinding
import com.hikemvp.R

class RespectNatureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRespectNatureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRespectNatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.tvContent.text = getString(R.string.ht_respect_nature_text)
    }
}
