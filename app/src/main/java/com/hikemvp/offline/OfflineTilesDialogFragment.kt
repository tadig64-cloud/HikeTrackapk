package com.hikemvp.offline

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.hikemvp.R
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView

class OfflineTilesDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val map = requireActivity().findViewById<MapView>(R.id.map)

        return AlertDialog.Builder(ctx)
            .setTitle(R.string.menu_download_tiles)
            .setMessage(R.string.offline_tiles_info)
            .setPositiveButton(R.string.save) { _, _ ->
                val cm = CacheManager(map)
                val bbox = map.boundingBox
                val minZoom = 6
                val maxZoom = 16
                cm.downloadAreaAsync(ctx, bbox, minZoom, maxZoom, object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        Toast.makeText(ctx, R.string.msg_download_ok, Toast.LENGTH_SHORT).show()
                    }
                    override fun onTaskFailed(errors: Int) {
                        Toast.makeText(ctx, R.string.msg_download_failed, Toast.LENGTH_SHORT).show()
                    }
                    override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(total: Int) {}
                })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
