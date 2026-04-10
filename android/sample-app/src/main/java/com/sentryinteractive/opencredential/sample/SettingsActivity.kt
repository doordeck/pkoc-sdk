package com.sentryinteractive.opencredential.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Settings screen with a single radio toggle: pick which CryptoProvider mode the sample app
 * uses to register and sign credentials. Selecting a different mode persists the choice and
 * re-initializes the SDK immediately — credentials registered in the other mode become hidden
 * until you switch back.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    initialMode = SampleModePreference.get(this),
                    onModeSelected = { mode ->
                        SampleModePreference.set(this, mode)
                        SampleApplication.reinitializeSDK(application)
                    },
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    initialMode: SampleModePreference.Mode,
    onModeSelected: (SampleModePreference.Mode) -> Unit,
    onClose: () -> Unit
) {
    var selected by remember { mutableStateOf(initialMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "CryptoProvider mode",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose how the sample app supplies credential keys to the SDK. " +
                        "Switching modes hides credentials registered in the other mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            ModeOption(
                title = "SDK-managed Keystore (default)",
                description = "The SDK manages credential keys for you via OCKeyStore (AndroidKeyStore + auto-attestation).",
                isSelected = selected == SampleModePreference.Mode.SDK_KEYSTORE,
                onClick = {
                    selected = SampleModePreference.Mode.SDK_KEYSTORE
                    onModeSelected(SampleModePreference.Mode.SDK_KEYSTORE)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModeOption(
                title = "Custom CryptoProvider",
                description = "Sample app provides its own CryptoProvider impl (also AndroidKeyStore-backed) to exercise the override path.",
                isSelected = selected == SampleModePreference.Mode.CUSTOM_PROVIDER,
                onClick = {
                    selected = SampleModePreference.Mode.CUSTOM_PROVIDER
                    onModeSelected(SampleModePreference.Mode.CUSTOM_PROVIDER)
                }
            )
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
