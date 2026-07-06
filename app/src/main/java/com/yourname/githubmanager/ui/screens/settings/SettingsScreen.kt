// File: app/src/main/java/com/yourname/githubmanager/ui/screens/settings/SettingsScreen.kt
package com.yourname.githubmanager.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.githubmanager.ui.components.GitHubManagerTopAppBar
import com.yourname.githubmanager.ui.components.PrivacyPolicyDialog

/**
 * Settings screen: lets the user set the GitHub repo owner/name/branch and
 * their Personal Access Token, and view the privacy policy.
 *
 * @param onBackClick Invoked when the user wants to leave this screen
 *                     (e.g. wired to `navController.popBackStack()` once
 *                     a `Screen.Settings` route is added to AppNavigator).
 *                     Defaults to a no-op so this screen can be previewed
 *                     or used standalone before navigation is wired up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveConfirmationVisible) {
        if (uiState.saveConfirmationVisible) {
            snackbarHostState.showSnackbar("Settings saved")
            viewModel.onSaveConfirmationShown()
        }
    }

    Scaffold(
        topBar = {
            GitHubManagerTopAppBar(title = "Settings")
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Repository")
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.repoOwner,
                onValueChange = viewModel::onRepoOwnerChange,
                label = { Text("Repo owner") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.repoName,
                onValueChange = viewModel::onRepoNameChange,
                label = { Text("Repo name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.branch,
                onValueChange = viewModel::onBranchChange,
                label = { Text("Branch") },
                placeholder = { Text("main") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "GitHub Personal Access Token")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.isTokenSaved) {
                    "A token is currently saved (encrypted on-device)."
                } else {
                    "No token saved yet."
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = { Text("New token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isTokenSaved) {
                TextButton(onClick = viewModel::onClearTokenClick) {
                    Text("Remove saved token")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onSaveClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = viewModel::onShowPrivacyPolicyClick) {
                Text("Privacy Policy")
            }
        }
    }

    if (uiState.showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = viewModel::onDismissPrivacyPolicy)
    }
}
