package com.hikemvp.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.hikemvp.R

class ProfileSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_profile, rootKey)

        val pseudoPref = findPreference<EditTextPreference>("profile_pseudo")
        // Provide summary programmatically (compat-safe)
        pseudoPref?.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        pseudoPref?.setOnBindEditTextListener { it.maxLines = 1 }
        pseudoPref?.setOnPreferenceChangeListener { _, newValue ->
            val s = (newValue as? String)?.trim().orEmpty()
            s.length in 1..40
        }

        val clearAvatar = findPreference<Preference>("profile_clear_avatar_pref")
        clearAvatar?.setOnPreferenceClickListener {
            ProfileUtils.setAvatarUri(requireContext(), null)
            true
        }

        // These are native switches with default values from XML
        val hud = findPreference<SwitchPreferenceCompat>("profile_hud_weather")
        val auto = findPreference<SwitchPreferenceCompat>("profile_auto_follow")
    }
}
