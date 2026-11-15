package com.nadim.bikepart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Shell-accessible compatibility probe, not part of shop navigation. */
class FluentCompatibilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FluentCompatibilityTrial() }
    }
}
