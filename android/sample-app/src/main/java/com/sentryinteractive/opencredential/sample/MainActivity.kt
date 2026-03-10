package com.sentryinteractive.opencredential.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK

/**
 * Sample main activity demonstrating OpenCredential SDK integration.
 *
 * Flow with invitation code:
 * 1. Launch consent with invite code
 * 2. On consent accepted → login callback fires → launch login
 * 3. On login completed → launch credential selection with org context
 * 4. On credentials approved → onCompleted callback fires
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SampleMainActivity"
    }

    private var statusText by mutableStateOf("Ready")
    private var linkInput by mutableStateOf("")
    private var credentialCount by mutableIntStateOf(0)

    // Stored org context from consent flow, used to launch credential selection after login
    private var pendingOrgId: String? = null
    private var pendingOrgName: String? = null
    private var pendingInviteCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupCallback()

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("OC SDK Sample") })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(32.dp))

                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "OpenCredential SDK",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Sample Application",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Demonstrates SDK integration: consent -> login -> credential selection -> share.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))


                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    label = { Text("Invitation code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val input = linkInput.trim()
                        if (input.isNotEmpty()) {
                            OpenCredentialSDK.launchConsent(this@MainActivity, input)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = linkInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Text("Launch Consent Flow")
                }

                Spacer(modifier = Modifier.weight(1f))


                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (credentialCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$credentialCount credential(s) shared",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    private fun setupCallback() {
        OpenCredentialSDK.setCallback(object : OpenCredentialSDK.Callback {
            override fun onConsentApproved(organizationId: String, organizationName: String, inviteCode: String) {
                Log.i(TAG, "Consent approved for $organizationName, launching login...")
                // Store org context so we can launch credential selection after login
                pendingOrgId = organizationId
                pendingOrgName = organizationName
                pendingInviteCode = inviteCode
                runOnUiThread {
                    statusText = "Consent given for $organizationName. Logging in..."
                }
                // Next step: login
                OpenCredentialSDK.launchLogin(this@MainActivity)
            }

            override fun onLoginCompleted() {
                Log.i(TAG, "Login completed, launching credential selection...")
                runOnUiThread {
                    statusText = "Login successful! Select credentials to share..."
                }
                // Next step: credential selection with org context
                val orgId = pendingOrgId
                val orgName = pendingOrgName
                val code = pendingInviteCode
                if (orgId != null && orgName != null && code != null) {
                    OpenCredentialSDK.launchCredentialSelection(
                        this@MainActivity, orgId, orgName, code
                    )
                }
            }

            override fun onCompleted(selectedCredentials: Array<ByteArray>) {
                Log.i(TAG, "SDK flow completed with ${selectedCredentials.size} credentials")
                clearPendingOrg()
                runOnUiThread {
                    credentialCount = selectedCredentials.size
                    statusText = "Flow completed! ${selectedCredentials.size} credential(s) shared."
                }
            }

            override fun onCancelled() {
                Log.i(TAG, "SDK flow cancelled")
                clearPendingOrg()
                runOnUiThread {
                    statusText = "Consent cancelled"
                }
            }
        })
    }

    private fun clearPendingOrg() {
        pendingOrgId = null
        pendingOrgName = null
        pendingInviteCode = null
    }

}
