package com.hikemvp.group

import android.app.AlertDialog
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.LayoutRes
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hikemvp.R

class GroupAdminSheet : BottomSheetDialogFragment() {

    private val mapView get() = GroupBridge.mapView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.bs_group_admin, container, false)

        val btnNew = v.findViewById<Button>(R.id.btnNew)
        val btnNewFrom = v.findViewById<Button>(R.id.btnNewFromOverlay)
        val btnSnapshot = v.findViewById<Button>(R.id.btnSnapshot)
        val btnInvite = v.findViewById<Button>(R.id.btnInvite)
        val btnJoin = v.findViewById<Button>(R.id.btnJoin)
        val btnRename = v.findViewById<Button>(R.id.btnRename)
        val btnSwitch = v.findViewById<Button>(R.id.btnSwitch)
        val btnDelete = v.findViewById<Button>(R.id.btnDelete)
        val btnExport = v.findViewById<Button>(R.id.btnExport)
        val btnImportMerge = v.findViewById<Button>(R.id.btnImportMerge)
        val btnImportReplace = v.findViewById<Button>(R.id.btnImportReplace)

        val txtStatus = v.findViewById<TextView>(R.id.txtStatus)
        val txtRole = v.findViewById<TextView>(R.id.txtRole)

        fun updateStatus() {
            val cur = GroupRepo.currentGroupId(requireContext())
            val label = if (cur == null) "Aucun groupe sélectionné" else "Groupe actuel: $cur"
            txtStatus.text = label
            txtRole.text = "rôle: " + GroupRepo.roleLabel(requireContext(), cur)

            val isOwner = cur != null && GroupRepo.isOwner(requireContext(), cur)
            val isAdmin = cur != null && GroupRepo.isAdmin(requireContext(), cur)
            btnDelete.isEnabled = isOwner
            btnRename.isEnabled = cur != null && (isOwner || isAdmin)
            btnSnapshot.isEnabled = cur != null && (isOwner || isAdmin)
            btnExport.isEnabled = cur != null
            btnImportMerge.isEnabled = cur != null && (isOwner || isAdmin)
            btnImportReplace.isEnabled = cur != null && (isOwner || isAdmin)
        }
        updateStatus()

        btnNew.setOnClickListener {
            askText("Nom du groupe") { name ->
                val mv = mapView ?: return@askText
                GroupRepo.createGroup(requireContext(), name, mv, fromOverlay = false)
                updateStatus()
                toast("Groupe créé")
            }
        }
        btnNewFrom.setOnClickListener {
            askText("Nom du groupe (depuis la carte)") { name ->
                val mv = mapView ?: return@askText
                GroupRepo.createGroup(requireContext(), name, mv, fromOverlay = true)
                updateStatus()
                toast("Groupe créé depuis la carte")
            }
        }
        btnSnapshot.setOnClickListener {
            val cur = GroupRepo.currentGroupId(requireContext()) ?: return@setOnClickListener toast("Aucun groupe")
            GroupRepo.snapshotOverlayIntoGroup(requireContext(), cur)
            toast("Groupe mis à jour depuis la carte")
            updateStatus()
        }
        btnInvite.setOnClickListener {
            val pair = GroupRepo.createInvite(requireContext())
            if (pair == null) {
                toast("Aucun groupe sélectionné")
            } else {
                val (code, shareText) = pair
                val deeplink = "hiketrack://group/join?code=$code"
                val content = layoutInflater.inflate(R.layout.dialog_qr, null, false)
                val img = content.findViewById<ImageView>(R.id.imgQr)
                val txtCode = content.findViewById<TextView>(R.id.txtCode)
                val txtLink = content.findViewById<TextView>(R.id.txtLink)
                val bmp = QrUtils.make(deeplink, 1024)
                img.setImageDrawable(BitmapDrawable(resources, bmp))
                txtCode.text = "Code: $code"
                txtLink.text = deeplink
                AlertDialog.Builder(requireContext())
                    .setTitle("Invitation (QR + lien)")
                    .setView(content)
                    .setPositiveButton("Partager") { _, _ -> GroupRepo.shareText(requireContext(), shareText) }
                    .setNegativeButton("Fermer", null)
                    .show()
            }
        }
        btnJoin.setOnClickListener {
            askText("Code d'invitation") { code ->
                val mv = mapView ?: return@askText
                val ok = GroupRepo.joinByCode(requireContext(), code.trim(), mv)
                if (!ok) toast("Code inconnu sur cet appareil") else toast("Groupe rejoint")
                updateStatus()
            }
        }
        btnRename.setOnClickListener {
            val cur = GroupRepo.currentGroupId(requireContext()) ?: return@setOnClickListener toast("Aucun groupe")
            askText("Nouveau nom") { newName ->
                GroupRepo.renameGroup(requireContext(), cur, newName)
                updateStatus()
                toast("Nom mis à jour")
            }
        }
        btnSwitch.setOnClickListener {
            val list = GroupRepo.listGroups(requireContext())
            if (list.isEmpty()) { toast("Aucun groupe"); return@setOnClickListener }
            val names = list.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Changer de groupe")
                .setItems(names) { _, which ->
                    val id = list[which].id
                    GroupRepo.setCurrentGroup(requireContext(), id)
                    val mv = mapView ?: return@setItems
                    GroupRepo.applyGroupToOverlay(requireContext(), id, mv)
                    updateStatus()
                }.show()
        }
        btnDelete.setOnClickListener {
            val cur = GroupRepo.currentGroupId(requireContext()) ?: return@setOnClickListener toast("Aucun groupe")
            AlertDialog.Builder(requireContext())
                .setTitle("Supprimer le groupe ?")
                .setMessage("Cette action est définitive.")
                .setPositiveButton("Supprimer") { _, _ ->
                    GroupRepo.deleteGroup(requireContext(), cur)
                    updateStatus()
                    toast("Groupe supprimé")
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
        btnExport.setOnClickListener {
            val cur = GroupRepo.currentGroupId(requireContext()) ?: return@setOnClickListener toast("Aucun groupe")
            val json = GroupRepo.exportGroupMembers(requireContext(), cur)
            showText("JSON (membres)", json, positive = "Partager") {
                GroupRepo.shareText(requireContext(), json)
            }
        }
        btnImportMerge.setOnClickListener {
            val mv = mapView ?: return@setOnClickListener toast("Carte indisponible")
            askMultiline("Coller le JSON (fusion)") { json ->
                GroupRepo.importIntoCurrent(requireContext(), json, mv, replace = false)
                toast("Import fusion OK")
                updateStatus()
            }
        }
        btnImportReplace.setOnClickListener {
            val mv = mapView ?: return@setOnClickListener toast("Carte indisponible")
            askMultiline("Coller le JSON (remplace)") { json ->
                GroupRepo.importIntoCurrent(requireContext(), json, mv, replace = true)
                toast("Import remplacement OK")
                updateStatus()
            }
        }

        return v
    }

    // ---- Helpers (déclarés ici pour éviter les "Unresolved reference") ----
    private fun askText(title: String, onOk: (String) -> Unit) {
        val input = EditText(requireContext())
        input.hint = title
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val t = input.text?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) onOk(t) else toast("Champ vide")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    private fun askMultiline(title: String, onOk: (String) -> Unit) {
        val input = EditText(requireContext())
        input.hint = title
        input.minLines = 5
        input.setLines(8)
        input.setHorizontallyScrolling(false)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val t = input.text?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) onOk(t) else toast("Champ vide")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    private fun showText(title: String, text: String, positive: String? = null, onPositive: (() -> Unit)? = null) {
        val v = TextView(requireContext())
        v.text = text
        v.setPadding(32, 24, 32, 24)
        val b = AlertDialog.Builder(requireContext()).setTitle(title).setView(v).setNegativeButton("Fermer", null)
        if (positive != null && onPositive != null) {
            b.setPositiveButton(positive) { _, _ -> onPositive() }
        }
        b.show()
    }
    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
