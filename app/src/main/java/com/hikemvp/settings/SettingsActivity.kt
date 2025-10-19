package com.hikemvp.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.hikemvp.GpxUtils
import com.hikemvp.MapActivity
import com.hikemvp.R
import com.hikemvp.TrackStore
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
        title = getString(R.string.title_settings)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    // === Import GPX ===
    private val importGpx =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(requireContext(), R.string.gpx_import_fail, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* best effort */ }

            try {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val poly = GpxUtils.parseToPolyline(input)
                    TrackStore.setAll(poly.actualPoints)
                }
                Toast.makeText(requireContext(), R.string.gpx_import_ok, Toast.LENGTH_SHORT).show()
                // Ouvre la carte pour visualiser la trace
                startActivity(Intent(requireContext(), MapActivity::class.java))
            } catch (t: Throwable) {
                t.printStackTrace()
                Toast.makeText(requireContext(), R.string.gpx_import_fail, Toast.LENGTH_LONG).show()
            }
        }

    // === Export GPX ===
    private var pendingExportFileName: String? = null
    private val exportGpxCreate =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            if (uri == null) return@registerForActivityResult
            val pts = TrackStore.snapshot()
            if (pts.isEmpty()) {
                Toast.makeText(requireContext(), R.string.err_nothing_to_export, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            try {
                val poly = Polyline().apply { setPoints(pts) }
                val gpx = GpxUtils.polylineToGpx(poly, pendingExportFileName)
                requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                    GpxUtils.saveToFile(gpx, os)
                }
                Toast.makeText(requireContext(), R.string.msg_export_ready, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                t.printStackTrace()
                Toast.makeText(requireContext(), R.string.err_export_failed, Toast.LENGTH_LONG).show()
            } finally {
                pendingExportFileName = null
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        

        // === Changement de langue ===
        runCatching {
            val langPref: ListPreference? = findPreference("pref_app_language")
            langPref?.setOnPreferenceChangeListener { _, newValue ->
                val value = (newValue as? String) ?: "auto"
                val locales = when (value) {
                    "auto" -> LocaleListCompat.getEmptyLocaleList()
                    "fr" -> LocaleListCompat.forLanguageTags("fr")
                    "en" -> LocaleListCompat.forLanguageTags("en")
                    "es" -> LocaleListCompat.forLanguageTags("es")
                    "de" -> LocaleListCompat.forLanguageTags("de")
                    "it" -> LocaleListCompat.forLanguageTags("it")
                    "pt" -> LocaleListCompat.forLanguageTags("pt")
                    "pt-BR" -> LocaleListCompat.forLanguageTags("pt-BR")
                    "nl" -> LocaleListCompat.forLanguageTags("nl")
                    "pl" -> LocaleListCompat.forLanguageTags("pl")
                    "ru" -> LocaleListCompat.forLanguageTags("ru")
                    "ar" -> LocaleListCompat.forLanguageTags("ar")
                    "zh-CN" -> LocaleListCompat.forLanguageTags("zh-CN")
                    "ja" -> LocaleListCompat.forLanguageTags("ja")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(locales)
                activity?.recreate()
                true
            }
        }// Source de carte (résumé auto)
        findPreference<ListPreference>(getString(R.string.pref_key_map_source))?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }

        // Zoom par défaut (affiche la valeur)
        findPreference<SeekBarPreference>(getString(R.string.pref_key_map_default_zoom))?.apply {
            summary = value.toString()
            setOnPreferenceChangeListener { pref, newValue ->
                (pref as SeekBarPreference).summary = newValue.toString()
                true
            }
        }

        // Import GPX – déclenche le sélecteur
        // Export GPX – propose un nom puis CreateDocument
        // ====== AJOUTS ======

        // Vitesse de planification (km/h)
        findPreference<EditTextPreference>("pref_key_plan_speed_kmh")?.apply {
            // saisie numérique décimale
            setOnBindEditTextListener { edit ->
                edit.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            // résumé dynamique
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            if (text.isNullOrBlank()) text = "4.0"
        }

        // Éco-batterie (GPS adaptatif) – utilisé par TrackRecordingService
        findPreference<SwitchPreferenceCompat>("pref_key_battery_saver")?.apply {
            // Optionnel : ajoute un summary dynamique si tu veux
            // summary = if (isChecked) "Activé" else "Désactivé"
        }
    }

    private fun promptExportName() {
        val pts = TrackStore.snapshot()
        if (pts.isEmpty()) {
            Toast.makeText(requireContext(), R.string.err_nothing_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        val def = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.export_gpx_name_hint)
            setText("HikeTrack_$def")
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.export_gpx_name_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var base = input.text.toString().trim()
                if (base.isEmpty()) base = "HikeTrack_$def"
                base = base.replace(Regex("[^A-Za-z0-9 _-]"), "_").replace(' ', '_')
                val fileName = if (base.endsWith(".gpx", true)) base else "$base.gpx"
                pendingExportFileName = base
                exportGpxCreate.launch(fileName)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
