package com.hikemvp

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * Dialog minimal pour afficher des infos de trace.
 * Titre: R.string.track_detail_title (clé manquante corrigée dans strings.xml)
 */
class TrackDetailDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.track_detail_title)
            .setMessage("Détails de la trace à implémenter.")
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }
}
