// File: app/src/main/java/com/yourname/githubmanager/MainActivity.kt
package com.yourname.githubmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourname.githubmanager.navigation.AppNavigator
import com.yourname.githubmanager.ui.theme.GitHubManagerTheme

/**
 * Single Activity that hosts the entire Compose UI.
 * Sets up edge-to-edge display and delegates all navigation
 * to [AppNavigator].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitHubManagerTheme {
                AppNavigator()
            }
        }
    }
}
