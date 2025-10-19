package com.hikemvp.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.hikemvp.R

class SettingsFragmentLite : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        // Source de la carte
        findPreference<ListPreference>(getString(R.string.pref_key_map_source))?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Zoom par défaut
        findPreference<SeekBarPreference>(getString(R.string.pref_key_map_default_zoom))?.apply {
            // bornes sûres
            min = 2
            max = 20
            showSeekBarValue = true
            // optionnel : afficher la valeur en résumé
            summary = value.toString()
            setOnPreferenceChangeListener { pref, newValue ->
                (pref as SeekBarPreference).summary = (newValue as? Int)?.toString() ?: ""
                true
            }
        }

        // Import GPX (info)
        findPreference<Preference>(getString(R.string.pref_key_import_gpx))?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), getString(R.string.msg_import_ready), Toast.LENGTH_SHORT).show()
            true
        }

        // Export GPX (info)
        findPreference<Preference>(getString(R.string.pref_key_export_gpx))?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), getString(R.string.msg_export_ready), Toast.LENGTH_SHORT).show()
            true
        }
    }
}
