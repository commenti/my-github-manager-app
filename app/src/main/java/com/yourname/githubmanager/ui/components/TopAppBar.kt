// File: app/src/main/java/com/yourname/githubmanager/ui/components/TopAppBar.kt
package com.yourname.githubmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.yourname.githubmanager.R
import com.yourname.githubmanager.ui.theme.GitHubManagerTheme

/**
 * App-wide Top App Bar.
 *
 * Shows the app name on the left and a 3-dot overflow menu icon on the right.
 *
 * Phase 1: The menu icon is visible but does nothing when tapped.
 * Future phases can pass a real [onMenuClick] lambda to open a dropdown.
 *
 * @param title       The title string to display (defaults to app name from resources).
 * @param onMenuClick Callback invoked when the 3-dot icon is tapped.
 *                    Defaults to an empty lambda so callers are not forced to wire it up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubManagerTopAppBar(
    title: String = stringResource(id = R.string.app_name),
    onMenuClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            // 3-dot overflow menu icon — disabled/no-op in Phase 1
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarPreview() {
    GitHubManagerTheme {
        GitHubManagerTopAppBar()
    }
}
