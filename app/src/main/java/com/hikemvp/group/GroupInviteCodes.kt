package com.hikemvp.group

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.abs

/**
 * Utils d'invitation – SANS dépendance externe.
 * - Génère un code humain lisible (ex: HK-7Q3D-Z9LM-42AB) sans caractères ambigus (O/0, I/1).
 * - Sérialise/désérialise un petit payload (JSON encodé en Base64 URL-safe).
 * - Reconnaît des codes DEMO ("DEMO-TOULOUSE", "DEMO-ALPES") pour charger un roster de démonstration.
 *
 * Intégration minimale :
 *  val code = GroupInviteCodes.generateHumanCode()
 *  val payload = GroupInviteCodes.newPayloadForDevice(context, codeHint = code)
 *  val encoded = GroupInviteCodes.encode(payload)  // à partager / stocker
 *  val parsed = GroupInviteCodes.decode(encoded)   // côté réception
 *
 * Pour la démo sans 2e téléphone :
 *  if (GroupInviteCodes.isDemoCode(input)) {
 *     val demo = GroupInviteCodes.demoRoster(input) // Map<String, GeoPointLike>
 *     // à toi d'injecter ce roster dans l'overlay du groupe si besoin.
 *  }
 */
object GroupInviteCodes {

    // Alphabet sans O/0 ni I/1 pour éviter les confusions visuelles.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val rng = SecureRandom()

    data class Payload(
        val code: String,
        val deviceName: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val version: Int = 1
    )

    fun generateHumanCode(blocks: Int = 3, blockLen: Int = 4, prefix: String = "HK"): String {
        fun block(): String {
            val sb = StringBuilder(blockLen)
            repeat(blockLen) { sb.append(ALPHABET[rng.nextInt(ALPHABET.length)]) }
            return sb.toString()
        }
        return buildString {
            append(prefix)
            repeat(blocks) {
                append('-')
                append(block())
            }
        }
    }

    fun newPayloadForDevice(context: Context, codeHint: String? = null): Payload {
        val name = try {
            android.os.Build.MODEL ?: "Android"
        } catch (_: Throwable) { "Android" }
        val code = codeHint ?: generateHumanCode()
        return Payload(code = code, deviceName = name)
    }

    fun encode(p: Payload): String {
        val json = JSONObject()
            .put("code", p.code)
            .put("device", p.deviceName ?: JSONObject.NULL)
            .put("ts", p.createdAt)
            .put("v", p.version)
            .toString()
        // URL-safe sans padding pour que ça passe bien dans un lien ou un QR court.
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun decode(encoded: String): Payload? {
        return try {
            val raw = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val txt = String(raw, Charsets.UTF_8)
            val j = JSONObject(txt)
            Payload(
                code = j.optString("code", ""),
                deviceName = j.optString("device", null),
                createdAt = j.optLong("ts", 0L),
                version = j.optInt("v", 1)
            )
        } catch (_: Throwable) { null }
    }

    fun isDemoCode(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val c = input.trim().uppercase()
        return c == "DEMO-TOULOUSE" || c == "DEMO-ALPES"
    }

    /**
     * Roster de démo léger : renvoie une Map id->(lat,lon) sous forme d’un petit "struct" simple
     * pour éviter les dépendances à des classes internes. À toi de convertir vers GeoPoint/GroupMember.
     */
    data class LatLon(val lat: Double, val lon: Double)

    fun demoRoster(input: String): Map<String, LatLon> {
        val code = input.trim().uppercase()
        return when (code) {
            "DEMO-TOULOUSE" -> around(43.6045, 1.4440, listOf("Bruno","Elara","Zorak","Sylra"))
            "DEMO-ALPES"    -> around(45.9237, 6.8694, listOf("Korath","Varkos","Zyra","Pionnier"))
            else -> emptyMap()
        }
    }

    // Génère des points autour d’un centre (± ~300m) pour visualiser un groupe.
    private fun around(lat: Double, lon: Double, names: List<String>): Map<String, LatLon> {
        return names.mapIndexed { idx, name ->
            val dx = (rng.nextDouble() - 0.5) * 0.006   // ~±300 m Est/Ouest
            val dy = (rng.nextDouble() - 0.5) * 0.004   // ~±220 m Nord/Sud
            val jitterLat = lat + dy
            val jitterLon = lon + dx / kotlin.math.cos(Math.toRadians(lat))
            val id = "${name.lowercase()}-${abs(name.hashCode() % 1000)}-${idx+1}"
            id to LatLon(jitterLat, jitterLon)
        }.toMap()
    }

    /**
     * Essaie d’extraire un code depuis un contenu "QR" courant :
     * - "hiketrack://join?code=...."
     * - "https://hiketrack.app/join?code=...."
     * - code brut (ex: HK-XXXX-YYYY-ZZZZ)
     */
    fun extractCodeFromContent(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        val lower = s.lowercase()
        val key = "code="
        val idx = lower.indexOf(key)
        if (idx >= 0) {
            return s.substring(idx + key.length).takeWhile { it != '&' && it != ' ' && it != '"' && it != '\'' }
        }
        return s // brut
    }
}