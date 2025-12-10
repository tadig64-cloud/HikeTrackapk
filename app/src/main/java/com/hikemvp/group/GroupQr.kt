package com.hikemvp.group

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Support QR "optionnel" SANS dépendances build.gradle.
 *
 * Principe : on tente d’appeler l’appli ZXing "Barcode Scanner" via Intent implicite.
 * - S’il est installé → scan direct
 * - Sinon → on propose d’ouvrir le Play Store, ou on retombe sur saisie manuelle.
 *
 * Avantage : ne casse pas la compilation (aucune import de classes externes).
 * Inconvénient : pas d’expérience "embarquée" si l’appli n’est pas installée.
 * Plus tard, on pourra intégrer l’embeddé (journeyapps) si tu veux.
 */
object GroupQr {

    private const val ZXING_SCAN_ACTION = "com.google.zxing.client.android.SCAN"

    fun launchExternalScanner(activity: Activity, requestCode: Int) {
        val intent = Intent(ZXING_SCAN_ACTION)
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE")
        try {
            activity.startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            // Pas d’appli de scan → propose l’installation
            promptInstallScanner(activity)
        }
    }

    fun fetchResultFromActivityResult(requestCode: Int, resultCode: Int, data: Intent?): String? {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val contents = data.getStringExtra("SCAN_RESULT")
            return GroupInviteCodes.extractCodeFromContent(contents)
        }
        return null
    }

    private fun promptInstallScanner(context: Context) {
        Toast.makeText(context, "Scanner QR non trouvé. Installation proposée…", Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.zxing.client.android"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                val intent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.zxing.client.android"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Throwable) {
                // Dernier recours
                Toast.makeText(context, "Impossible d’ouvrir le store. Saisis le code manuellement.", Toast.LENGTH_LONG).show()
            }
        }
    }
}