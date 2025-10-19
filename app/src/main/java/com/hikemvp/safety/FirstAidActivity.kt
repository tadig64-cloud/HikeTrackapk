package com.hikemvp.safety

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.databinding.ActivityFirstAidBinding
import com.hikemvp.R

class FirstAidActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFirstAidBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFirstAidBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.tvContent.text = getString(R.string.ht_first_aid_text)
    }
}
