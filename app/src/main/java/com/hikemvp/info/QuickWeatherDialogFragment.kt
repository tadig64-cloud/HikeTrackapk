package com.hikemvp.info

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.hikemvp.R

class QuickWeatherDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_quick_weather, null, false)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.qw_title)
            .setView(view)
            .setPositiveButton(R.string.qw_close, null)
            .create()
    }

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager) {
            QuickWeatherDialogFragment().show(fm, "QuickWeatherDialog")
        }
    }
}
