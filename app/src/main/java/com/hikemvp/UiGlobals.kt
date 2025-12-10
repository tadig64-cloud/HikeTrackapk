package com.hikemvp

import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Références UI globales, initialisées par MapUiWiring.wire(activity).
 * Elles existent pour satisfaire les usages dans MapActivity sans régression.
 */

lateinit var toolbar: MaterialToolbar
lateinit var hudCoords: ViewGroup
lateinit var coordsText: TextView
lateinit var hudWeather: ViewGroup
lateinit var weatherPanel: ViewGroup
lateinit var bottomNav: ViewGroup
lateinit var bottomNavigation: BottomNavigationView
lateinit var bottomBar: BottomAppBar