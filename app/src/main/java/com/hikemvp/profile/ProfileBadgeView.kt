package com.hikemvp.profile

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.hikemvp.R

/**
 * Single, final version of the profile badge used in the HUD.
 * Remove any duplicate class at: com/hikemvp/profile/ui/ProfileBadgeView.kt
 * to avoid "Redeclaration" errors.
 */
class ProfileBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: ImageView
    private val name: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_profile_badge, this, true)
        avatar = findViewById(R.id.profileBadgeImage)
        name = findViewById(R.id.profileBadgeName)
        refreshFromPrefs()
    }

    /** Re-reads stored profile and updates the chip. */
    fun refreshFromPrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val pseudo = prefs.getString(ProfilePrefs.KEY_PSEUDO, "") ?: ""
        val avatarUri = prefs.getString(ProfilePrefs.KEY_AVATAR_URI, null)?.let { Uri.parse(it) }

        setPseudo(pseudo)
        setAvatar(avatarUri)
    }

    fun setPseudo(pseudo: String?) {
        val p = (pseudo ?: "").trim()
        if (p.isEmpty()) {
            name.isVisible = false
        } else {
            name.text = p
            name.isVisible = true
        }
    }

    fun setAvatar(uri: Uri?) {
        if (uri == null) {
            avatar.setImageResource(R.drawable.ic_profile_avatar_round)
        } else {
            avatar.setImageURI(uri)
            avatar.invalidate()
        }
    }
}