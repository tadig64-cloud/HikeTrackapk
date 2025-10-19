package com.hikemvp.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.button.MaterialButton
import com.hikemvp.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var avatarButton: MaterialButton
    private lateinit var clearAvatarButton: MaterialButton
    private lateinit var avatarPreview: ImageView
    private lateinit var pseudoInput: TextInputEditText
    private lateinit var pseudoLayout: TextInputLayout
    private lateinit var prefHudWeather: MaterialSwitch
    private lateinit var prefAutoFollow: MaterialSwitch

    private var pickedAvatarUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // Persist permission to read later
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) { /* some providers don't need it */ }

                pickedAvatarUri = uri
                applyAvatarPreview(uri)
                Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.profile_avatar_selected),
                    Snackbar.LENGTH_SHORT
                ).show()
                clearAvatarButton.isVisible = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        avatarButton = findViewById(R.id.btnPickAvatar)
        clearAvatarButton = findViewById(R.id.btnClearAvatar)
        avatarPreview = findViewById(R.id.imageAvatar)
        pseudoInput = findViewById(R.id.inputPseudo)
        pseudoLayout = findViewById(R.id.layoutPseudo)
        prefHudWeather = findViewById(R.id.switchHudWeather)
        prefAutoFollow = findViewById(R.id.switchAutoFollow)

        loadPrefs()

        avatarButton.setOnClickListener { openImagePicker() }
        clearAvatarButton.setOnClickListener {
            pickedAvatarUri = null
            applyAvatarPreview(null)
            saveAvatarUri(null)
            Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.profile_avatar_cleared),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_profile, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save_profile -> { saveAll(); true }
            R.id.action_change_photo -> { openImagePicker(); true }
            R.id.action_remove_photo -> {
                pickedAvatarUri = null
                applyAvatarPreview(null)
                saveAvatarUri(null)
                Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.profile_avatar_cleared),
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_profile_history -> {
                startActivity(Intent(this, ProfileHistoryActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pickImage.launch(intent)
    }

    private fun loadPrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val pseudo = prefs.getString(ProfilePrefs.KEY_PSEUDO, "") ?: ""
        val avatar = prefs.getString(ProfilePrefs.KEY_AVATAR_URI, null)
        val hudWeather = prefs.getBoolean(ProfilePrefs.KEY_HUD_WEATHER, true)
        val autoFollow = prefs.getBoolean(ProfilePrefs.KEY_AUTO_FOLLOW, true)

        pseudoInput.setText(pseudo)
        pickedAvatarUri = avatar?.let { Uri.parse(it) }
        applyAvatarPreview(pickedAvatarUri)
        prefHudWeather.isChecked = hudWeather
        prefAutoFollow.isChecked = autoFollow
        clearAvatarButton.isVisible = (pickedAvatarUri != null)
    }

    private fun saveAll() {
        val pseudo = pseudoInput.text?.toString()?.trim().orEmpty()
        if (pseudo.isEmpty()) {
            pseudoLayout.error = getString(R.string.profile_pseudo_required)
            return
        } else {
            pseudoLayout.error = null
        }

        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(ProfilePrefs.KEY_PSEUDO, pseudo)
        editor.putBoolean(ProfilePrefs.KEY_HUD_WEATHER, prefHudWeather.isChecked)
        editor.putBoolean(ProfilePrefs.KEY_AUTO_FOLLOW, prefAutoFollow.isChecked)
        saveAvatarUri(pickedAvatarUri?.toString(), alreadyEditing = editor)
        editor.apply()

        Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun applyAvatarPreview(uri: Uri?) {
        if (uri == null) {
            avatarPreview.setImageResource(R.drawable.ic_profile_avatar_round)
        } else {
            avatarPreview.setImageURI(uri)
            // Some providers require this to load; ignore failures
            avatarPreview.invalidate()
        }
    }

    private fun saveAvatarUri(uri: String?, alreadyEditing: android.content.SharedPreferences.Editor? = null) {
        val editor = alreadyEditing ?: PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(ProfilePrefs.KEY_AVATAR_URI, uri)
        editor.apply()
        clearAvatarButton.isVisible = (uri != null)
    }
}
