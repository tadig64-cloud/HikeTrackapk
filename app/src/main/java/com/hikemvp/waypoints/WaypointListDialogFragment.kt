package com.hikemvp.waypoints

import android.Manifest
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import com.hikemvp.R
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Bottom sheet des Waypoints â€” sans modification dâ€™UI. */
class WaypointListDialogFragment : BottomSheetDialogFragment() {

    // --- DonnÃ©es & Ã©tat ---
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var groupToRestore: String? = null

    private lateinit var adapter: WaypointAdapter
    private val items: MutableList<Row> = mutableListOf()
    private var sortMode: Int = 0 // 0=name, else=distance

    // Filtres
    private var filterQuery: String = ""
    private var selectedGroup: String? = null
    private var showHiddenGroups: Boolean = false
    private val hiddenGroupsKey = "waypoints_hidden_groups"

    // SÃ©lection multiple
    private var selectionMode: Boolean = false
    private val selectedIds: MutableSet<Long> = mutableSetOf()

    // RÃ©fs UI
    private var recycler: RecyclerView? = null
    private var emptyText: TextView? = null
    private var spinnerGroups: Spinner? = null
    private var switchHidden: MaterialSwitch? = null
    private var groupLegend: LinearLayout? = null
    private var selectionBar: View? = null

    // Export en attente (pour exporter uniquement la sÃ©lection)
    private var pendingExport: List<Row>? = null

    // SAF
    private val openDocLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importFromUri(uri)
    }
    private val createJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) exportJsonToUri(uri) else Toast.makeText(requireContext(), "Export JSON annulÃ©.", Toast.LENGTH_SHORT).show()
    }
    private val createGpxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) exportGpxToUri(uri) else Toast.makeText(requireContext(), "Export GPX annulÃ©.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root: View = inflater.inflate(R.layout.dialog_waypoint_list, container, false)

        // RÃ©cup vues
        recycler = root.findViewById(R.id.recycler_waypoints)
        emptyText = root.findViewById(R.id.emptyText)
        val spinnerSort: Spinner? = root.findViewById(R.id.spinner_sort)

        // Ensure sort spinner has options
        val sortOptions = listOf("Nom", "Distance")
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSort?.adapter = sortAdapter
        // Restore previous selection if available
        if (sortMode in 0..(sortOptions.size - 1)) {
            spinnerSort?.setSelection(sortMode)
        }
        val closeBtn: MaterialButton? = root.findViewById(R.id.btn_close)

        val inputSearch: EditText? = root.findViewById(R.id.input_search)
        spinnerGroups = root.findViewById(R.id.spinner_groups)
        switchHidden = root.findViewById(R.id.switch_hidden)
        groupLegend = root.findViewById(R.id.groupLegend)
        val btnToggleGroupVis: MaterialButton? = root.findViewById(R.id.btn_toggle_group_visibility)
        val btnClearFilters: MaterialButton? = root.findViewById(R.id.btn_clear_filters)

        val btnToggleSelection: MaterialButton? = root.findViewById(R.id.btn_toggle_selection)
        selectionBar = root.findViewById(R.id.selectionBar)
        val btnSelectAll: MaterialButton? = root.findViewById(R.id.btn_select_all)
        val btnExportSel: MaterialButton? = root.findViewById(R.id.btn_export_sel)
        val btnDeleteSel: MaterialButton? = root.findViewById(R.id.btn_delete_sel)
        val btnCancelSel: MaterialButton? = root.findViewById(R.id.btn_cancel_sel)

        val btnImport: MaterialButton? = root.findViewById(R.id.btn_import)
        val btnExport: MaterialButton? = root.findViewById(R.id.btn_export)
        val btnExportJson: MaterialButton? = root.findViewById(R.id.btn_export_json)

        // --- Restaurer prÃ©fÃ©rences ---
        run {
            val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
            sortMode = sp.getInt(PREF_SORT, 0)
            spinnerSort?.setSelection(sortMode, false)

            filterQuery = sp.getString(PREF_QUERY, "") ?: ""
            inputSearch?.setText(filterQuery)

            showHiddenGroups = sp.getBoolean(PREF_SHOW_HIDDEN, false)
            switchHidden?.isChecked = showHiddenGroups

            groupToRestore = sp.getString(PREF_GROUP, null)
        }

        // Adapter
        adapter = WaypointAdapter(
            onView = { row -> if (selectionMode) toggleSelect(row.id) else focusOnMap(row) },
            onRename = { row -> if (!selectionMode) promptRename(row) else toggleSelect(row.id) },
            onDelete = { row -> if (!selectionMode) promptDelete(row) else toggleSelect(row.id) },
            onNavigate = { row -> if (!selectionMode) openExternalNavigation(row) else toggleSelect(row.id) },
            isSelected = { id -> selectedIds.contains(id) },
            askSelection = { selectionMode }
        )
        recycler?.apply {
            adapter = this@WaypointListDialogFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        // Tri via spinner_sort (0 = alpha, sinon distance)
        spinnerSort?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortMode = position
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putInt(PREF_SORT, sortMode)
                }
                refresh()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Recherche texte/coord (debounce 250ms)
        inputSearch?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val txt = s?.toString()?.trim().orEmpty()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    filterQuery = txt
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                        putString(PREF_QUERY, filterQuery)
                    }
                    refresh()
                }
                searchHandler.postDelayed(searchRunnable!!, 250)
            }
        })

        // Spinner groupes
        spinnerGroups?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = parent?.adapter?.getItem(position)?.toString()
                selectedGroup = if (value == null || value == ALL_GROUPS_LABEL) null else value
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    if (selectedGroup == null) remove(PREF_GROUP) else putString(PREF_GROUP, selectedGroup)
                }
                refresh()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Affichage groupes masquÃ©s
        switchHidden?.setOnCheckedChangeListener { _, isChecked ->
            showHiddenGroups = isChecked
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putBoolean(PREF_SHOW_HIDDEN, showHiddenGroups)
            }
            refresh()
        }

        // Toggle visibilitÃ© groupe courant
        btnToggleGroupVis?.setOnClickListener {
            val grp = selectedGroup
            if (grp.isNullOrBlank()) {
                Toast.makeText(requireContext(), "SÃ©lectionne d'abord un groupe.", Toast.LENGTH_SHORT).show()
            } else {
                toggleGroupHidden(grp)
                refresh()
                Toast.makeText(requireContext(), "Groupe Â« $grp Â» ${if (isGroupHidden(grp)) "masquÃ©" else "visible"}", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear filtres
        btnClearFilters?.setOnClickListener {
            filterQuery = ""
            inputSearch?.setText("")
            selectedGroup = null
            spinnerGroups?.setSelection(0, true)
            showHiddenGroups = false
            switchHidden?.isChecked = false
            refresh()
        }

        // SÃ©lection multiple
        btnToggleSelection?.setOnClickListener {
            selectionMode = !selectionMode
            selectedIds.clear()
            selectionBar?.visibility = if (selectionMode) View.VISIBLE else View.GONE
            refresh()
        }
        btnSelectAll?.setOnClickListener {
            if (!selectionMode) return@setOnClickListener
            val current = currentList()
            selectedIds.clear()
            for (r in current) selectedIds.add(r.id)
            adapter.notifyDataSetChanged()
            updateSelectionBarState()
        }
        btnSelectAll?.setOnLongClickListener {
            if (!selectionMode) return@setOnLongClickListener true
            val current = currentList()
            val currentIds = current.map { it.id }.toSet()
            for (id in currentIds) {
                if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
            }
            adapter.notifyDataSetChanged()
            updateSelectionBarState()
            Toast.makeText(requireContext(), "SÃ©lection inversÃ©e", Toast.LENGTH_SHORT).show()
            true
        }
        btnExportSel?.setOnClickListener {
            val sel = selectedIds.toList()
            if (!selectionMode || sel.isEmpty()) {
                Toast.makeText(requireContext(), "Aucun Ã©lÃ©ment sÃ©lectionnÃ©.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val byId = items.associateBy { it.id }
            val chosen = mutableListOf<Row>()
            for (id in sel) byId[id]?.let { chosen.add(it) }
            if (chosen.isEmpty()) {
                Toast.makeText(requireContext(), "SÃ©lection introuvable.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val formats = arrayOf("GPX", "JSON")
            AlertDialog.Builder(requireContext())
                .setTitle("Exporter la sÃ©lection")
                .setItems(formats) { _, which ->
                    pendingExport = chosen
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    try {
                        if (which == 0) {
                            createGpxLauncher.launch("waypoints_selection_$ts.gpx")
                        } else {
                            createJsonLauncher.launch("waypoints_selection_$ts.json")
                        }
                    } catch (_: Throwable) {
                        pendingExport = null
                        Toast.makeText(requireContext(), "Impossible d'ouvrir l'enregistrement.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        btnDeleteSel?.setOnClickListener {
            if (!selectionMode || selectedIds.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Supprimer sÃ©lection")
                .setMessage("Supprimer ${selectedIds.size} waypoint(s) ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    val ids = selectedIds.toList()
                    var okCount = 0
                    for (id in ids) {
                        if (StorageCompat.delete(requireContext(), id)) {
                            items.removeAll { it.id == id }
                            okCount++
                        }
                    }
                    selectedIds.clear()
                    Toast.makeText(requireContext(), "SupprimÃ©: $okCount", Toast.LENGTH_SHORT).show()
                    refresh()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        btnCancelSel?.setOnClickListener {
            if (!selectionMode) return@setOnClickListener
            selectionMode = false
            selectedIds.clear()
            selectionBar?.visibility = View.GONE
            refresh()
        }

        // Import / Export
        btnImport?.setOnClickListener {
            try {
                openDocLauncher.launch(arrayOf("application/json","application/geo+json","text/csv","application/gpx+xml","application/xml","text/xml","text/plain"))
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "Impossible d'ouvrir le sÃ©lecteur de fichiers.", Toast.LENGTH_LONG).show()
            }
        }
        btnExport?.setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            try {
                createGpxLauncher.launch("waypoints_$ts.gpx")
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "Impossible d'ouvrir la boÃ®te d'enregistrement GPX.", Toast.LENGTH_LONG).show()
            }
        }
        btnExportJson?.setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            try {
                createJsonLauncher.launch("waypoints_$ts.json")
            } catch (_: Throwable) {
                Toast.makeText(requireContext(), "Impossible d'ouvrir la boÃ®te d'enregistrement JSON.", Toast.LENGTH_LONG).show()
            }
        }

        closeBtn?.setOnClickListener { dismissAllowingStateLoss() }

        // DonnÃ©es + UI
        loadData()
        setupGroups()
        // Restaurer groupe choisi
        groupToRestore?.let { g ->
            val ad = spinnerGroups?.adapter
            if (ad != null) {
                for (i in 0 until ad.count) {
                    if (ad.getItem(i)?.toString() == g) {
                        spinnerGroups?.setSelection(i, false)
                        break
                    }
                }
            }
        }
        refreshLegend()
        refresh()

        return root
    }
    override fun onStart() {
        super.onStart()
        val d = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return

        // Force full-height sheet so the bottom buttons & the Close button are always reachable.
        val sheet = d.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet) as? android.view.ViewGroup
        sheet?.let {
            it.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            it.requestLayout()
        }

        d.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            isDraggable = true
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }


    // --- Import / Export ---
    private fun importFromUri(uri: Uri) {
        val ctx = requireContext()
        val text = readTextFromUri(ctx, uri) ?: run {
            Toast.makeText(ctx, "Fichier illisible.", Toast.LENGTH_LONG).show(); return
        }
        val lower = text.trim().lowercase(Locale.getDefault())
        val incoming: List<Row> = when {
            lower.startsWith("{") || lower.startsWith("[") -> parseJsonToRows(text)
            lower.contains("<gpx") -> parseGpxToRows(text)
            else -> parseCsvToRows(text)
        }
        if (incoming.isEmpty()) {
            Toast.makeText(ctx, "Aucun waypoint dÃ©tectÃ©.", Toast.LENGTH_LONG).show()
            return
        }
        val added = mergeRows(items, incoming)
        val saved = StorageCompat.saveAllViaPrefs(ctx, items)
        if (saved) {
            setupGroups()
            refresh()
            Toast.makeText(ctx, "Import rÃ©ussi : +$added waypoint(s).", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(ctx, "Import lu mais non sauvegardÃ©.", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportJsonToUri(uri: Uri) {
        val ctx = requireContext()
        val list = pendingExport ?: currentList()
        val arr = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("lat", r.lat)
            o.put("lon", r.lon)
            if (r.note != null) o.put("note", r.note)
            o.put("createdAt", r.createdAt)
            if (r.group != null) o.put("group", r.group)
            arr.put(o)
        }
        val ok = writeTextToUri(ctx, uri, arr.toString(2))
        Toast.makeText(ctx, if (ok) "Export JSON effectuÃ©." else "Ã‰chec de l'export JSON.", Toast.LENGTH_LONG).show()
        pendingExport = null
    }

    private fun exportGpxToUri(uri: Uri) {
        val ctx = requireContext()
        val list = pendingExport ?: currentList()
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="HikeTrack" xmlns="http://www.topografix.com/GPX/1/1">
""".trimIndent())
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (r in list) {
            sb.append("""  <wpt lat="${r.lat}" lon="${r.lon}">
    <name>${xmlEscape(r.name.ifBlank { "Waypoint ${r.id}" })}</name>
""")
            if (!r.note.isNullOrBlank()) sb.append("    <desc>${xmlEscape(r.note!!)}</desc>\n")
            sb.append("    <time>${iso.format(Date(r.createdAt))}</time>\n")
            if (!r.group.isNullOrBlank()) sb.append("    <type>${xmlEscape(r.group!!)}</type>\n")
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        val ok = writeTextToUri(ctx, uri, sb.toString())
        Toast.makeText(ctx, if (ok) "Export GPX effectuÃ©." else "Ã‰chec de l'export GPX.", Toast.LENGTH_LONG).show()
        pendingExport = null
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun readTextFromUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8)).readText()
                .let { if (it.startsWith("\ufeff")) it.substring(1) else it } // strip BOM
        }
    } catch (_: Throwable) { null }

    private fun writeTextToUri(ctx: Context, uri: Uri, text: String): Boolean = try {
        ctx.contentResolver.openOutputStream(uri)?.use { outs ->
            OutputStreamWriter(outs, StandardCharsets.UTF_8).use { it.write(text) }
        }
        true
    } catch (_: Throwable) { false }

    private fun mergeRows(base: MutableList<Row>, incoming: List<Row>): Int {
        var added = 0
        val existingIds = base.map { it.id }.toMutableSet()
        var nextId = (existingIds.maxOrNull() ?: 0L) + 1L

        fun isDup(a: Row, b: Row): Boolean {
            val sameCoords = distanceBetween(a.lat, a.lon, b.lat, b.lon) < 2f
            val sameName = a.name.trim().equals(b.name.trim(), ignoreCase = true)
            return (a.id == b.id) || (sameCoords && sameName)
        }

        for (r in incoming) {
            var row = r.copy()
            if (row.id <= 0L || existingIds.contains(row.id)) {
                while (existingIds.contains(nextId)) nextId++
                row = row.copy(id = nextId++)
            }
            if (base.any { isDup(it, row) }) continue
            base.add(row)
            existingIds.add(row.id)
            added++
        }
        return added
    }

    // --- Parseurs d'import ---
    private fun parseJsonToRows(raw: String): List<Row> {
        try {
            val test = JSONObject(raw)
            val t = test.optString("type", "")
            if (t.equals("FeatureCollection", true) || t.equals("Feature", true)) {
                return parseGeoJsonToRows(test)
            }
        } catch (_: Throwable) { /* not an object or not GeoJSON */ }

        try {
            // Tableau direct
            try {
                val arr = JSONArray(raw)
                return parseJsonArrayRows(arr)
            } catch (_: Throwable) { /* not an array */ }

            // Objet racine
            val obj = JSONObject(raw)
            obj.optJSONArray("items")?.let { return parseJsonArrayRows(it) }
            obj.optJSONArray("list")?.let { return parseJsonArrayRows(it) }

            // Map id -> objet
            val out = mutableListOf<Row>()
            val it = obj.keys()
            var idx = 0L
            while (it.hasNext()) {
                val k = it.next()
                val o = obj.optJSONObject(k) ?: continue
                out.add(parseJsonOneRow(o, fallbackId = idx++))
            }
            return out
        } catch (_: Throwable) { }
        return emptyList()
    }

    private fun parseJsonArrayRows(arr: JSONArray): List<Row> {
        val out = mutableListOf<Row>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(parseJsonOneRow(o, fallbackId = i.toLong()))
        }
        return out
    }

    private fun parseJsonOneRow(o: JSONObject, fallbackId: Long): Row {
        val id = o.optLong("id", fallbackId)
        val lat = when {
            o.has("lat") -> o.optDouble("lat", 0.0)
            o.has("latitude") -> o.optDouble("latitude", 0.0)
            else -> 0.0
        }
        val lon = when {
            o.has("lon") -> o.optDouble("lon", 0.0)
            o.has("longitude") -> o.optDouble("longitude", 0.0)
            else -> 0.0
        }
        val name = when {
            o.has("name") -> o.optString("name", "")
            o.has("title") -> o.optString("title", "")
            else -> ""
        }
        val note: String? = if (o.has("note")) o.optString("note") else null
        val createdAt: Long = when {
            o.has("createdAt") -> o.optLong("createdAt", System.currentTimeMillis())
            o.has("time") -> o.optLong("time", System.currentTimeMillis())
            else -> System.currentTimeMillis()
        }
        val group: String? = when {
            o.has("group") -> o.optString("group")
            o.has("grp") -> o.optString("grp")
            o.has("folder") -> o.optString("folder")
            else -> null
        }
        return Row(id, lat, lon, name, note, createdAt, group)
    }

    private fun parseGeoJsonToRows(obj: JSONObject): List<Row> {
        fun oneFeatureToRow(f: JSONObject, idx: Long): Row? {
            val geom = f.optJSONObject("geometry") ?: return null
            val gtype = geom.optString("type", "Point")
            if (!gtype.equals("Point", true)) return null
            val coords = geom.optJSONArray("coordinates") ?: return null
            if (coords.length() < 2) return null
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            val props = f.optJSONObject("properties")
            val name = props?.optString("name")
                ?: props?.optString("title")
                ?: f.optString("id", "Waypoint $idx")
            val note = props?.optString("note") ?: props?.optString("desc") ?: props?.optString("description")
            val group = props?.optString("group") ?: props?.optString("folder") ?: props?.optString("category")
            return Row(idx, lat, lon, name ?: "Waypoint $idx", note, System.currentTimeMillis(), group)
        }
        val type = obj.optString("type", "")
        return if (type.equals("FeatureCollection", true)) {
            val feats = obj.optJSONArray("features") ?: return emptyList()
            val out = mutableListOf<Row>()
            var idx = 1L
            for (i in 0 until feats.length()) {
                val f = feats.optJSONObject(i) ?: continue
                oneFeatureToRow(f, idx++)?.let { out.add(it) }
            }
            out
        } else if (type.equals("Feature", true)) {
            oneFeatureToRow(obj, 1L)?.let { listOf(it) } ?: emptyList()
        } else emptyList()
    }

    private fun parseCsvToRows(raw: String): List<Row> {
        val text = raw.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (text.isEmpty()) return emptyList()
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        fun detectDelimiter(sample: List<String>): Char {
            var best = ','
            var bestCount = -1
            for (d in charArrayOf('\t',';',',')) {
                var c = 0
                for (s in sample) c += s.count { it == d }
                if (c > bestCount) { bestCount = c; best = d }
            }
            return best
        }

        fun splitLine(line: String, delim: Char): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' -> {
                        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"'); i++
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    ch == delim && !inQuotes -> {
                        out.add(sb.toString()); sb.setLength(0)
                    }
                    else -> sb.append(ch)
                }
                i++
            }
            out.add(sb.toString())
            return out.map { it.trim().trim('"') }
        }

        fun asDouble(s: String?): Double? {
            if (s == null) return null
            val cleaned = s.replace("Â°","").replace(" ", "").replace(",", ".")
            return cleaned.toDoubleOrNull()
        }

        val delim = detectDelimiter(lines.take(10))

        val first = splitLine(lines[0], delim)
        fun findIdx(keys: List<String>): Int {
            for (i in first.indices) {
                val k = first[i].lowercase(Locale.getDefault())
                if (keys.any { k == it }) return i
            }
            return -1
        }

        val idxName = findIdx(listOf("name","nom","label","title"))
        val idxLat  = findIdx(listOf("lat","latitude","y"))
        val idxLon  = findIdx(listOf("lon","lng","longitude","x"))
        val idxNote = findIdx(listOf("note","desc","description","comment","cmt"))
        val idxGrp  = findIdx(listOf("group","grp","folder","categorie","category"))

        val hasHeader = idxLat >= 0 && idxLon >= 0
        val startIdx = if (hasHeader) 1 else 0

        val out = mutableListOf<Row>()
        var idCounter = 1L
        for (li in startIdx until lines.size) {
            val cols = splitLine(lines[li], delim)
            if (!hasHeader) {
                if (cols.size < 3) continue
                val name = cols[0]
                val lat = asDouble(cols[1]) ?: continue
                val lon = asDouble(cols[2]) ?: continue
                val note = cols.getOrNull(3)
                val group = cols.getOrNull(4)
                out.add(Row(idCounter++, lat, lon, name, note, System.currentTimeMillis(), group))
            } else {
                val name = cols.getOrNull(idxName) ?: ""
                val lat = asDouble(cols.getOrNull(idxLat)) ?: continue
                val lon = asDouble(cols.getOrNull(idxLon)) ?: continue
                val note = if (idxNote >= 0) cols.getOrNull(idxNote) else null
                val group = if (idxGrp >= 0) cols.getOrNull(idxGrp) else null
                out.add(Row(idCounter++, lat, lon, name, note, System.currentTimeMillis(), group))
            }
        }
        return out
    }

    private fun parseGpxToRows(raw: String): List<Row> {
        val out = mutableListOf<Row>()

        fun parseTimeIso8601(s: String): Long {
            val cand = s.trim()
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX"
            )
            for (p in patterns) {
                try {
                    val sdf = SimpleDateFormat(p, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    return sdf.parse(cand)?.time ?: continue
                } catch (_: Throwable) { }
            }
            return System.currentTimeMillis()
        }

        try {
            val parser = Xml.newPullParser()
            parser.setInput(raw.reader())
            var event = parser.eventType

            var inWpt = false
            var lat = 0.0
            var lon = 0.0
            var name: String? = null
            var desc: String? = null
            var time: Long = System.currentTimeMillis()
            var type: String? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "wpt" -> {
                                inWpt = true
                                lat = parser.getAttributeValue(null, "lat")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                lon = parser.getAttributeValue(null, "lon")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                                name = null; desc = null; type = null; time = System.currentTimeMillis()
                            }
                            "name" -> if (inWpt) name = parser.nextText()
                            "desc", "cmt" -> if (inWpt) desc = parser.nextText()
                            "time" -> if (inWpt) time = parseTimeIso8601(parser.nextText())
                            "type" -> if (inWpt) type = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "wpt" && inWpt) {
                            out.add(Row(0L, lat, lon, name ?: "", desc, time, type))
                            inWpt = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Throwable) { }
        return out
    }

    // --- Listes / tri / filtres ---
    private fun updateSelectionBarState() {
        val visible = currentList().map { it.id }.toSet()
        val count = selectedIds.count { it in visible }
        val tv = selectionBar?.findViewById<TextView?>(R.id.title)
        tv?.text = "$count sÃ©lectionnÃ©(s)"
    }

    private fun toggleSelect(id: Long) {
        if (!selectionMode) return
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        adapter.notifyDataSetChanged()
        updateSelectionBarState()
    }

    private fun currentList(): List<Row> {
        val list = items.toMutableList()
        val hidden = getHiddenGroups()
        val afterHidden = list.filter { row -> showHiddenGroups || row.group?.let { !hidden.contains(it) } ?: true }
        val afterGroup = afterHidden.filter { row -> selectedGroup == null || row.group == selectedGroup }

        val q = filterQuery
        val filtered = if (q.isBlank()) {
            afterGroup
        } else {
            val coords = parseLatLon(q)
            if (coords != null) {
                val (qlat, qlon) = coords
                afterGroup.filter { row -> distanceBetween(qlat, qlon, row.lat, row.lon) <= 50f }
            } else {
                afterGroup.filter { row -> row.name.contains(q, ignoreCase = true) }
            }
        }

        val loc: Location? = if (sortMode == 0) null else getLastKnownLocation()
        val sorted = filtered.toMutableList()
        if (sortMode == 0) {
            sorted.sortBy { it.name.lowercase(Locale.getDefault()) }
        } else {
            sorted.sortBy { row -> if (loc == null) Float.POSITIVE_INFINITY else distanceBetween(loc.latitude, loc.longitude, row.lat, row.lon) }
        }
        return sorted
    }

    private fun parseLatLon(s: String): Pair<Double, Double>? {
        val t = s.replace(";", ",").replace("  ", " ").trim()
        val parts = t.split(",")
        if (parts.size == 2) {
            val la = parts[0].trim().replace("Â°", "")
            val lo = parts[1].trim().replace("Â°", "")
            val lat = la.toDoubleOrNull()
            val lon = lo.toDoubleOrNull()
            if (lat != null && lon != null && abs(lat) <= 90 && abs(lon) <= 180) return Pair(lat, lon)
        }
        return null
    }

    private fun refresh() {
        val list = currentList()
        adapter.submit(list)
        val isEmpty = list.isEmpty()
        emptyText?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            emptyText?.text = "Aucun waypoint dÃ©tectÃ©.\nAstuce : un appui long sur la carte pour en ajouter."
        }
        refreshLegend()
    }

    private fun setupGroups() {
        val groups: MutableList<String> = items.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }
            .distinct()
            .sorted()
            .toMutableList()
        groups.add(0, ALL_GROUPS_LABEL)
        spinnerGroups?.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, groups)
    }

    private fun refreshLegend() {
        val cont = groupLegend ?: return
        cont.removeAllViews()
        val ctx = cont.context
        val groups: List<String> = items.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct().sorted()
        val hidden = getHiddenGroups()

        for (g in groups) {
            val tv = TextView(ctx).apply {
                text = if (hidden.contains(g)) "[$g]" else g
                setPadding(16, 8, 16, 8)
                alpha = if (hidden.contains(g)) 0.5f else 1f
                background = ContextCompat.getDrawable(ctx, android.R.drawable.btn_default_small)
                setOnClickListener {
                    val ad = spinnerGroups?.adapter ?: return@setOnClickListener
                    for (i in 0 until ad.count) {
                        if (ad.getItem(i)?.toString() == g) {
                            spinnerGroups?.setSelection(i, true)
                            break
                        }
                    }
                }
                setOnLongClickListener {
                    toggleGroupHidden(g)
                    refresh()
                    true
                }
            }
            cont.addView(tv)
        }
    }

    private fun toggleGroupHidden(group: String) {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val set = getHiddenGroups().toMutableSet()
        if (set.contains(group)) set.remove(group) else set.add(group)
        sp.edit { putStringSet(hiddenGroupsKey, set) }
    }

    private fun isGroupHidden(group: String): Boolean = getHiddenGroups().contains(group)

    private fun getHiddenGroups(): Set<String> {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return sp.getStringSet(hiddenGroupsKey, emptySet()) ?: emptySet()
    }

    private fun loadData() {
        items.clear()
        val ctx: Context = requireContext().applicationContext
        val fromStorage: List<Row> = StorageCompat.loadViaStorage(ctx)
        if (fromStorage.isNotEmpty()) {
            items.addAll(fromStorage)
        } else {
            val fromPrefs: List<Row> = StorageCompat.loadViaPrefsSmart(ctx)
            items.addAll(fromPrefs)
        }
    }

    private fun promptRename(row: Row) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(row.name)
            setSelection(row.name.length.coerceAtLeast(0))
        }
        val til = TextInputLayout(requireContext()).apply {
            hint = "Nom du waypoint"
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Renommer le waypoint")
            .setView(til)
            .setPositiveButton("Renommer") { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Nom vide", Toast.LENGTH_SHORT).show()
                } else {
                    val ok: Boolean = StorageCompat.rename(requireContext(), row.id, newName)
                    if (ok) {
                        row.name = newName
                        refresh()
                    } else {
                        Toast.makeText(requireContext(), "Impossible de renommer ce waypoint.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptDelete(row: Row) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer le waypoint")
            .setMessage("Supprimer Â« ${row.name} Â» ?")
            .setPositiveButton("Supprimer") { _, _ ->
                val ok: Boolean = StorageCompat.delete(requireContext(), row.id)
                if (ok) {
                    items.removeAll { it.id == row.id }
                    refresh()
                    Toast.makeText(requireContext(), "SupprimÃ©", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Impossible de supprimer ce waypoint.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun focusOnMap(row: Row) {
        val act = activity ?: return

        // 1) map: MapView
        try {
            val f = act::class.java.getDeclaredField("map")
            f.isAccessible = true
            val map = f.get(act) as? MapView
            map?.controller?.animateTo(GeoPoint(row.lat, row.lon))
            dismissAllowingStateLoss()
            return
        } catch (_: Throwable) { }

        // 2) MÃ©thodes possibles
        val names: List<String> = listOf("centerOnWaypoint", "centerOn", "focusOn", "goToLatLon")
        for (name in names) {
            try {
                val m = act::class.java.getMethod(name, java.lang.Double.TYPE, java.lang.Double.TYPE)
                m.invoke(act, row.lat, row.lon)
                dismissAllowingStateLoss()
                return
            } catch (_: Throwable) { }
        }

        // 3) Fallback : copie coords
        try {
            val clip = android.content.ClipData.newPlainText("waypoint", "${row.lat},${row.lon}")
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "CoordonnÃ©es copiÃ©es : %.5f, %.5f".format(row.lat, row.lon), Toast.LENGTH_LONG).show()
        } catch (_: Throwable) { }
        dismissAllowingStateLoss()
    }

    /** Navigation : toujours afficher un chooser, avec prioritÃ© : Maps â†’ Waze â†’ OsmAnd â†’ Organic â†’ geo â†’ web. */
    private fun openExternalNavigation(row: Row) {
        val ctx = requireContext()
        val lat = row.lat
        val lon = row.lon
        val pm = ctx.packageManager

        fun resolves(i: Intent): Boolean = try { i.resolveActivity(pm) != null } catch (_: Throwable) { false }

        val intents = mutableListOf<Intent>()

        // 1) Google Maps
        val gmm = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon&mode=d")).apply {
            `package` = "com.google.android.apps.maps"
        }
        if (resolves(gmm)) intents.add(gmm)

        // 2) Waze
        val waze = Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes"))
        if (resolves(waze)) intents.add(waze)

        // 3) OsmAnd (free + plus)
        val osmandFree = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")).apply { `package` = "net.osmand" }
        if (resolves(osmandFree)) intents.add(osmandFree)
        val osmandPlus = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")).apply { `package` = "net.osmand.plus" }
        if (resolves(osmandPlus)) intents.add(osmandPlus)

        // 4) Organic Maps
        val organic = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")).apply { `package` = "app.organicmaps" }
        if (resolves(organic)) intents.add(organic)

        // Base intent gÃ©nÃ©rique
        val genericGeo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
        val baseIntent = if (resolves(genericGeo)) genericGeo else intents.firstOrNull()

        if (baseIntent != null) {
            val chooser = Intent.createChooser(baseIntent, getString(R.string.app_name)).apply {
                if (intents.isNotEmpty()) {
                    val rest = intents.filter { it.`package` != baseIntent.`package` || it.data != baseIntent.data }
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, rest.toTypedArray())
                }
            }
            try {
                startActivity(chooser)
                return
            } catch (_: Throwable) { }
        }

        // Fallback web
        val webUri = Uri.parse("https://maps.google.com/?daddr=$lat,$lon")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        try {
            startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Aucune application de navigation trouvÃ©e.", Toast.LENGTH_LONG).show()
        }
    }

    // --- Adapter / ViewHolder ---
    private class WaypointAdapter(
        val onView: (Row) -> Unit,
        val onRename: (Row) -> Unit,
        val onDelete: (Row) -> Unit,
        val onNavigate: (Row) -> Unit,
        val isSelected: (Long) -> Boolean,
        val askSelection: () -> Boolean
    ) : RecyclerView.Adapter<WaypointVH>() {

        private val data: MutableList<Row> = mutableListOf()

        fun submit(list: List<Row>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointVH {
            val v: View = LayoutInflater.from(parent.context).inflate(R.layout.row_waypoint, parent, false)
            return WaypointVH(v)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: WaypointVH, position: Int) {
            val row: Row = data[position]
            holder.bind(row, onView, onRename, onDelete, onNavigate, askSelection(), isSelected(row.id))
        }
    }

    private class WaypointVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.title)
        private val subtitle: TextView = v.findViewById(R.id.subtitle)
        private val more: ImageButton = v.findViewById(R.id.more)
        private val checkbox: CheckBox? = v.findViewById(R.id.checkbox)

        fun bind(
            row: Row,
            onView: (Row) -> Unit,
            onRename: (Row) -> Unit,
            onDelete: (Row) -> Unit,
            onNavigate: (Row) -> Unit,
            selectionMode: Boolean,
            isChecked: Boolean
        ) {
            title.text = if (row.name.isBlank()) "Waypoint ${row.id}" else row.name
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(row.createdAt))
            val grp = row.group?.takeIf { it.isNotBlank() }?.let { " â€¢ $it" } ?: ""
            subtitle.text = "%.5f, %.5f â€¢ %s%s".format(row.lat, row.lon, date, grp)

            checkbox?.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkbox?.isChecked = isChecked
            checkbox?.setOnClickListener { itemView.performClick() }

            itemView.setOnClickListener { onView(row) }

            // Menu "â‹®" avec IDs numÃ©riques
            more.setOnClickListener {
                val pm = PopupMenu(itemView.context, it)
                pm.menu.add(0, 1, 0, "Voir sur la carte")
                pm.menu.add(0, 2, 1, "Renommer")
                pm.menu.add(0, 3, 2, "Supprimer")
                pm.menu.add(0, 4, 3, "Naviguer")
                pm.menu.add(0, 5, 4, "Copier coordonnÃ©es")
                pm.menu.add(0, 6, 5, "Partager (Google Maps)")
                pm.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> onView(row)
                        2 -> onRename(row)
                        3 -> onDelete(row)
                        4 -> onNavigate(row)
                        5 -> { // Copier coordonnÃ©es
                            try {
                                val latStr = String.format(Locale.US, "%.5f", row.lat)
                                val lonStr = String.format(Locale.US, "%.5f", row.lon)
                                val text = "$latStr,$lonStr"
                                val clip = android.content.ClipData.newPlainText("coords", text)
                                val cm = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(clip)
                                Toast.makeText(itemView.context, "CoordonnÃ©es copiÃ©es : $text", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) { }
                        }
                        6 -> { // Partager lien Google Maps
                            val latStr = String.format(Locale.US, "%.6f", row.lat)
                            val lonStr = String.format(Locale.US, "%.6f", row.lon)
                            val label = if (row.name.isBlank()) "Waypoint ${row.id}" else row.name
                            val url = "https://maps.google.com/?q=$latStr,$lonStr ($label)"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                                putExtra(Intent.EXTRA_SUBJECT, label)
                            }
                            try {
                                itemView.context.startActivity(Intent.createChooser(send, "Partager"))
                            } catch (_: Throwable) {
                                Toast.makeText(itemView.context, "Aucune app pour partager.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }
                pm.show()
            }
        }
    }

    // --- ModÃ¨le ---
    data class Row(
        val id: Long,
        val lat: Double,
        val lon: Double,
        var name: String,
        val note: String?,
        val createdAt: Long,
        val group: String?
    )

    // --- Storage compat ---
    private object StorageCompat {
        private const val DEFAULT_PREF_KEY: String = "waypoints"

        fun loadViaStorage(ctx: Context): List<Row> {
            val out: MutableList<Row> = mutableListOf()
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                val methods = listOf(
                    try { cls.getMethod("list", Context::class.java) } catch (_: Throwable) { null },
                    try { cls.getMethod("getAll", Context::class.java) } catch (_: Throwable) { null },
                    try { cls.getMethod("all", Context::class.java) } catch (_: Throwable) { null },
                    try { cls.getMethod("snapshot") } catch (_: Throwable) { null },
                    try { cls.getMethod("toJson", Context::class.java) } catch (_: Throwable) { null },
                    try { cls.getMethod("exportJson", Context::class.java) } catch (_: Throwable) { null },
                ).filterNotNull()
                for (m in methods) {
                    val res: Any? = if (m.parameterTypes.isEmpty()) m.invoke(null) else m.invoke(null, ctx)
                    if (res is String) {
                        val rows = parseAnyJson(res)
                        if (rows.isNotEmpty()) return rows
                    } else if (res is List<*>) {
                        val rows = mapList(res)
                        if (rows.isNotEmpty()) return rows
                    }
                }
            } catch (_: Throwable) { }
            return out
        }

        fun loadViaPrefsSmart(ctx: Context): List<Row> {
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            val out: MutableList<Row> = mutableListOf()

            // 1) Candidates
            val candidates: MutableSet<String> = LinkedHashSet<String>().apply { add(DEFAULT_PREF_KEY) }

            // 2) Chercher clÃ©s WaypointStorage + heuristiques
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                for (fieldName in listOf("PREF_KEY", "KEY", "WAYPOINTS_KEY", "STORE_KEY")) {
                    try {
                        val f = cls.getDeclaredField(fieldName)
                        f.isAccessible = true
                        val v = f.get(null)?.toString()
                        if (!v.isNullOrBlank()) candidates.add(v)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            candidates.addAll(listOf("waypoints_json", "wpts", "WPT_STORE", "wp_list", "waypoints_store"))
            try {
                val all: Map<String, *> = sp.all
                for (k in all.keys) {
                    if (k.contains("waypoint", ignoreCase = true)) candidates.add(k)
                }
            } catch (_: Throwable) { }

            // 3) Lire les clÃ©s trouvÃ©es â€” ne garder que les String
            val allMap: Map<String, *> = try { sp.all } catch (_: Throwable) { emptyMap<String, Any?>() }
            for (key in candidates) {
                val value = allMap[key]
                val raw = (value as? String) ?: continue
                if (raw.isEmpty()) continue
                val rows: List<Row> = parseAnyJson(raw)
                if (rows.isNotEmpty()) out.addAll(rows)
            }
            return out
        }

        fun saveAllViaPrefs(ctx: Context, rows: List<Row>): Boolean {
            return try {
                val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
                val arr = JSONArray()
                for (r in rows) {
                    val o = JSONObject()
                    o.put("id", r.id)
                    o.put("lat", r.lat)
                    o.put("lon", r.lon)
                    o.put("name", r.name)
                    if (r.note != null) o.put("note", r.note)
                    o.put("createdAt", r.createdAt)
                    if (r.group != null) o.put("group", r.group)
                    arr.put(o)
                }
                sp.edit { putString(DEFAULT_PREF_KEY, arr.toString()) }
                true
            } catch (_: Throwable) { false }
        }

        fun rename(ctx: Context, id: Long, newName: String): Boolean {
            // 1) Tenter via WaypointStorage
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                val candidates = listOf(
                    arrayOf("rename", Context::class.java, java.lang.Long.TYPE, String::class.java),
                    arrayOf("rename", java.lang.Long.TYPE, String::class.java),
                    arrayOf("updateName", Context::class.java, java.lang.Long.TYPE, String::class.java),
                    arrayOf("setName", Context::class.java, java.lang.Long.TYPE, String::class.java),
                )
                for (sig in candidates) {
                    try {
                        val m = if (sig.size == 4)
                            cls.getMethod(sig[0] as String, sig[1] as Class<*>, sig[2] as Class<*>, sig[3] as Class<*>)
                        else
                            cls.getMethod(sig[0] as String, sig[1] as Class<*>, sig[2] as Class<*>)
                        val ok = if (sig.size == 4) m.invoke(null, ctx, id, newName) else m.invoke(null, id, newName)
                        if (ok is Boolean && ok) return true
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            // 2) Fallback prefs
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            val candidates = LinkedHashSet<String>().apply { add(DEFAULT_PREF_KEY) }
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                for (fieldName in listOf("PREF_KEY", "KEY", "WAYPOINTS_KEY", "STORE_KEY")) {
                    try {
                        val f = cls.getDeclaredField(fieldName)
                        f.isAccessible = true
                        val v = f.get(null)?.toString()
                        if (!v.isNullOrBlank()) candidates.add(v)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            candidates.addAll(listOf("waypoints_json", "wpts", "WPT_STORE", "wp_list", "waypoints_store"))
            val allMap: Map<String, *> = try { sp.all } catch (_: Throwable) { emptyMap<String, Any?>() }
            for (key in candidates) {
                val raw = (allMap[key] as? String) ?: continue
                val updated = renameInJson(raw, id, newName) ?: continue
                sp.edit { putString(key, updated) }
                return true
            }
            return false
        }

        fun delete(ctx: Context, id: Long): Boolean {
            // 1) Tenter via WaypointStorage
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                val candidates = listOf(
                    arrayOf("delete", Context::class.java, java.lang.Long.TYPE),
                    arrayOf("remove", Context::class.java, java.lang.Long.TYPE),
                    arrayOf("delete", java.lang.Long.TYPE),
                    arrayOf("remove", java.lang.Long.TYPE),
                )
                for (sig in candidates) {
                    try {
                        val m = if (sig.size == 3)
                            cls.getMethod(sig[0] as String, sig[1] as Class<*>, sig[2] as Class<*>)
                        else
                            cls.getMethod(sig[0] as String, sig[1] as Class<*>)
                        val ok = if (sig.size == 3) m.invoke(null, ctx, id) else m.invoke(null, id)
                        if (ok is Boolean && ok) return true
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            // 2) Fallback prefs
            val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
            val candidates = LinkedHashSet<String>().apply { add(DEFAULT_PREF_KEY) }
            try {
                val cls = Class.forName("com.hikemvp.waypoints.WaypointStorage")
                for (fieldName in listOf("PREF_KEY", "KEY", "WAYPOINTS_KEY", "STORE_KEY")) {
                    try {
                        val f = cls.getDeclaredField(fieldName)
                        f.isAccessible = true
                        val v = f.get(null)?.toString()
                        if (!v.isNullOrBlank()) candidates.add(v)
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
            candidates.addAll(listOf("waypoints_json", "wpts", "WPT_STORE", "wp_list", "waypoints_store"))
            val allMap: Map<String, *> = try { sp.all } catch (_: Throwable) { emptyMap<String, Any?>() }
            for (key in candidates) {
                val raw = (allMap[key] as? String) ?: continue
                val updated = deleteInJson(raw, id) ?: continue
                sp.edit { putString(key, updated) }
                return true
            }
            return false
        }

        // --- JSON helpers ---
        private fun renameInJson(raw: String, id: Long, newName: String): String? {
            parseJsonArrayOrNull(raw)?.let { arr ->
                var changed = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optLong("id", -1L) == id) {
                        o.put("name", newName)
                        changed = true
                        break
                    }
                }
                if (changed) return arr.toString()
                return null
            }
            parseJsonObjectOrNull(raw)?.let { obj ->
                obj.optJSONArray("items")?.let { arr ->
                    var changed = false
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optLong("id", -1L) == id) { o.put("name", newName); changed = true; break }
                    }
                    if (changed) { obj.put("items", arr); return obj.toString() }
                    return null
                }
                obj.optJSONArray("list")?.let { arr ->
                    var changed = false
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optLong("id", -1L) == id) { o.put("name", newName); changed = true; break }
                    }
                    if (changed) { obj.put("list", arr); return obj.toString() }
                    return null
                }
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val o = obj.optJSONObject(k) ?: continue
                    val kid = o.optLong("id", try { k.toLong() } catch (_: Throwable) { -1L })
                    if (kid == id) { o.put("name", newName); obj.put(k, o); return obj.toString() }
                }
                return null
            }
            return null
        }

        private fun deleteInJson(raw: String, id: Long): String? {
            parseJsonArrayOrNull(raw)?.let { arr ->
                val out = JSONArray()
                var removed = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optLong("id", -1L) == id) { removed = true; continue }
                    out.put(o)
                }
                return if (removed) out.toString() else null
            }
            parseJsonObjectOrNull(raw)?.let { obj ->
                obj.optJSONArray("items")?.let { arr ->
                    val out = JSONArray()
                    var removed = false
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optLong("id", -1L) == id) { removed = true; continue }
                        out.put(o)
                    }
                    if (removed) { obj.put("items", out); return obj.toString() }
                    return null
                }
                obj.optJSONArray("list")?.let { arr ->
                    val out = JSONArray()
                    var removed = false
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optLong("id", -1L) == id) { removed = true; continue }
                        out.put(o)
                    }
                    if (removed) { obj.put("list", out); return obj.toString() }
                    return null
                }
                val keysToRemove = mutableListOf<String>()
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val o = obj.optJSONObject(k) ?: continue
                    val kid = o.optLong("id", try { k.toLong() } catch (_: Throwable) { -1L })
                    if (kid == id) keysToRemove.add(k)
                }
                if (keysToRemove.isNotEmpty()) {
                    for (k in keysToRemove) obj.remove(k)
                    return obj.toString()
                }
                return null
            }
            return null
        }

        private fun parseAnyJson(raw: String): List<Row> {
            parseJsonArrayOrNull(raw)?.let { return parseArray(it) }
            parseJsonObjectOrNull(raw)?.let { obj ->
                obj.optJSONArray("items")?.let { return parseArray(it) }
                obj.optJSONArray("list")?.let { return parseArray(it) }
                val list: MutableList<Row> = mutableListOf()
                val it = obj.keys()
                var idx = 0L
                while (it.hasNext()) {
                    val key = it.next()
                    val o = obj.optJSONObject(key) ?: continue
                    list.add(parseOne(o, fallbackId = idx++))
                }
                return list
            }
            return emptyList()
        }

        private fun parseJsonArrayOrNull(raw: String): JSONArray? = try { JSONArray(raw) } catch (_: Throwable) { null }
        private fun parseJsonObjectOrNull(raw: String): JSONObject? = try { JSONObject(raw) } catch (_: Throwable) { null }

        private fun parseArray(arr: JSONArray): List<Row> {
            val out: MutableList<Row> = mutableListOf()
            val n: Int = arr.length()
            for (i in 0 until n) {
                val o: JSONObject = arr.optJSONObject(i) ?: continue
                out.add(parseOne(o, fallbackId = i.toLong()))
            }
            return out
        }

        private fun parseOne(o: JSONObject, fallbackId: Long): Row {
            val id: Long = o.optLong("id", fallbackId)
            val lat: Double = when {
                o.has("lat") -> o.optDouble("lat", 0.0)
                o.has("latitude") -> o.optDouble("latitude", 0.0)
                else -> 0.0
            }
            val lon: Double = when {
                o.has("lon") -> o.optDouble("lon", 0.0)
                o.has("longitude") -> o.optDouble("longitude", 0.0)
                else -> 0.0
            }
            val name: String = when {
                o.has("name") -> o.optString("name", "")
                o.has("title") -> o.optString("title", "")
                else -> ""
            }
            val note: String? = if (o.has("note")) o.optString("note") else null
            val createdAt: Long = when {
                o.has("createdAt") -> o.optLong("createdAt", System.currentTimeMillis())
                o.has("time") -> o.optLong("time", System.currentTimeMillis())
                else -> System.currentTimeMillis()
            }
            val group: String? = when {
                o.has("group") -> o.optString("group")
                o.has("grp") -> o.optString("grp")
                o.has("folder") -> o.optString("folder")
                else -> null
            }
            return Row(id, lat, lon, name, note, createdAt, group)
        }

        private fun mapList(list: List<*>): List<Row> {
            val out: MutableList<Row> = mutableListOf()
            for (it in list) {
                if (it == null) continue
                try {
                    val c: Class<*> = it::class.java
                    val id: Long = (try { c.getDeclaredField("id").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toLong()
                        ?: 0L
                    val lat: Double =
                        (try { c.getDeclaredField("lat").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toDouble()
                            ?: (try { c.getDeclaredField("latitude").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toDouble()
                            ?: 0.0
                    val lon: Double =
                        (try { c.getDeclaredField("lon").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toDouble()
                            ?: (try { c.getDeclaredField("longitude").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toDouble()
                            ?: 0.0
                    val name: String =
                        (try { c.getDeclaredField("name").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null })
                            ?: (try { c.getDeclaredField("title").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null })
                            ?: ""
                    val note: String? = try { c.getDeclaredField("note").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null }
                    val createdAt: Long =
                        (try { c.getDeclaredField("createdAt").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toLong()
                            ?: (try { c.getDeclaredField("time").apply { isAccessible = true }.get(it) as? Number } catch (_: Throwable) { null })?.toLong()
                            ?: System.currentTimeMillis()
                    val group: String? =
                        (try { c.getDeclaredField("group").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null })
                            ?: (try { c.getDeclaredField("grp").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null })
                            ?: (try { c.getDeclaredField("folder").apply { isAccessible = true }.get(it)?.toString() } catch (_: Throwable) { null })

                    out.add(Row(id, lat, lon, name, note, createdAt, group))
                } catch (_: Throwable) { }
            }
            return out
        }
    }

    // --- Position ---
    private fun getLastKnownLocation(): Location? {
        val act = activity
        if (act != null) {
            val fieldNames = listOf("lastKnownLocation", "currentLocation", "location", "mLastLocation")
            for (fn in fieldNames) {
                try {
                    val f = act::class.java.getDeclaredField(fn)
                    f.isAccessible = true
                    val v = f.get(act) as? Location
                    if (v != null) return v
                } catch (_: Throwable) { }
            }
            val methodNames = listOf("getLastKnownLocation", "getCurrentLocation")
            for (mn in methodNames) {
                try {
                    val m = act::class.java.getMethod(mn)
                    val v = m.invoke(act) as? Location
                    if (v != null) return v
                } catch (_: Throwable) { }
            }
        }

        val ctx = context ?: return null
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (p in providers) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || (l.time > (best?.time ?: 0L))) best = l
            }
            best
        } catch (_: Throwable) { null }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }

    companion object {
        private const val ALL_GROUPS_LABEL = "Tous"
        private const val PREF_SORT = "waypoints_pref_sort"
        private const val PREF_QUERY = "waypoints_pref_query"
        private const val PREF_GROUP = "waypoints_pref_group"
        private const val PREF_SHOW_HIDDEN = "waypoints_pref_show_hidden"
    }
}

// --- Helpers ---
private fun Double.format(n: Int): String = "%.${n}f".format(Locale.US, this)