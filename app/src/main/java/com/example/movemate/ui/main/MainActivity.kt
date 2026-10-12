package com.example.movemate.ui.main

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.movemate.R
import com.example.movemate.databinding.ActivityMainBinding
import com.example.movemate.notification.NotificationHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Maps each nav button id to its navigation destination id. */
    private val navItems by lazy {
        listOf(
            R.id.navHome to R.id.homeFragment,
            R.id.navSteps to R.id.stepsFragment,
            R.id.navSleep to R.id.sleepFragment,
            R.id.navFood to R.id.foodFragment,
            R.id.navProfile to R.id.profileFragment
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Top-level destinations (no back button shown)
        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.stepsFragment,
            R.id.sleepFragment,
            R.id.foodFragment,
            R.id.profileFragment
        )
        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)

        // Wire up custom nav bar buttons
        for ((buttonId, destId) in navItems) {
            findViewById<FrameLayout>(buttonId).setOnClickListener {
                navController.navigate(
                    destId,
                    null,
                    NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(navController.graph.startDestinationId, inclusive = false, saveState = false)
                        .build()
                )
            }
        }

        // Update selection state when destination changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavSelection(destination.id)
        }

        // Create notification channel on first launch
        NotificationHelper.createNotificationChannel(this)
    }

    private fun updateNavSelection(activeDestId: Int) {
        for ((buttonId, destId) in navItems) {
            val frame = findViewById<FrameLayout>(buttonId)
            val icon = frame.getChildAt(0) as ImageView
            val isSelected = destId == activeDestId

            frame.setBackgroundResource(
                if (isSelected) R.drawable.nav_selected_circle
                else R.drawable.nav_unselected_circle
            )
            icon.setColorFilter(
                ContextCompat.getColor(
                    this,
                    if (isSelected) R.color.nav_icon_selected
                    else R.color.nav_icon_default
                )
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}
