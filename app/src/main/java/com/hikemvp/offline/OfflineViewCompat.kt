package com.hikemvp.offline

import android.app.Activity
import android.view.View

// Donne accès sûr à la vue offline_list si le layout existe.
val Activity.offline_list: View?
    get() = findViewById(
        resources.getIdentifier("offline_list", "id", packageName)
    )
