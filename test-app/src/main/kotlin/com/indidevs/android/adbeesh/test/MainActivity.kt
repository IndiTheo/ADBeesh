package com.indidevs.android.adbeesh.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.indidevs.android.adbeesh.test.theme.AppTheme
import com.indidevs.android.adbeesh.test.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { Surface { MainScreen(this) } } }
    }
}
