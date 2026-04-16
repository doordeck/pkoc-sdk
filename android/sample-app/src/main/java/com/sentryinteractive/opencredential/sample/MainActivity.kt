package com.sentryinteractive.opencredential.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentryinteractive.opencredential.sdk.OCCredentialInfo
import com.sentryinteractive.opencredential.sdk.OCIdentity
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

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
    private var hasCredentials by mutableStateOf(false)
    private var credentialInfos by mutableStateOf(emptyList<OCCredentialInfo>())
    private var isLoading by mutableStateOf(false)
    private var showDeleteSheet by mutableStateOf(false)
    private var showCredentialsSheet by mutableStateOf(false)
    private var showIdentityPickerFor by mutableStateOf<String?>(null) // "identity" or "identity+key"

    /** Convenience accessor for the picker — derives identities from the cached credential infos. */
    private val identities: List<OCIdentity>
        get() = credentialInfos.map { it.identity }

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

    override fun onResume() {
        super.onResume()
        checkCredentials()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("OC SDK Sample") },
                    actions = {
                        IconButton(onClick = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = hasCredentials && !isLoading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Delete")
                        }
                    }

                    OutlinedButton(
                        onClick = { showCredentialsSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = hasCredentials && !isLoading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("View")
                    }
                }

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
                    enabled = linkInput.trim().isNotEmpty() && !isLoading,
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

        if (showDeleteSheet) {
            DeleteBottomSheet()
        }

        if (showCredentialsSheet) {
            CredentialsListSheet()
        }

        if (showIdentityPickerFor != null) {
            IdentityPickerSheet()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CredentialsListSheet() {
        val sheetState = rememberModalBottomSheetState()

        // Local state — independent of MainActivity's cached credentialInfos. Every time the
        // sheet opens we do a fresh fetch so the list is always current, never stale.
        var sheetLoading by remember { mutableStateOf(true) }
        var sheetError by remember { mutableStateOf<String?>(null) }
        var sheetCredentials by remember { mutableStateOf<List<OCCredentialInfo>>(emptyList()) }

        LaunchedEffect(Unit) {
            sheetLoading = true
            sheetError = null
            try {
                val fetched = withContext(Dispatchers.IO) {
                    OpenCredentialSDK.getCredentialDetails()
                }
                sheetCredentials = fetched
            } catch (e: Exception) {
                sheetError = "Failed to load credentials: ${e.message}"
            } finally {
                sheetLoading = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showCredentialsSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Your Credentials",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                when {
                    sheetLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    sheetError != null -> {
                        Text(
                            text = sheetError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    sheetCredentials.isEmpty() -> {
                        Text(
                            text = "No credentials registered on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    else -> {
                        sheetCredentials.forEach { info ->
                            ListItem(
                                headlineContent = { Text(info.identity.value) },
                                supportingContent = {
                                    Text(if (info.attested) "Attested" else "Not attested")
                                },
                                leadingContent = {
                                    if (info.attested) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = "Attested",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.GppMaybe,
                                            contentDescription = "Not attested",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DeleteBottomSheet() {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDeleteSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Delete Credentials",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Delete All") },
                    supportingContent = { Text("Remove every credential across all identities") },
                    modifier = Modifier.clickable {
                        showDeleteSheet = false
                        deleteCredentials(identity = null)
                    }
                )

                ListItem(
                    headlineContent = { Text("Delete by Identity") },
                    supportingContent = { Text("Remove the credential for a specific email") },
                    modifier = Modifier.clickable {
                        showDeleteSheet = false
                        showIdentityPickerFor = "identity"
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun IdentityPickerSheet() {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showIdentityPickerFor = null },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Select Identity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                identities.forEach { identity ->
                    ListItem(
                        headlineContent = { Text(identity.value) },
                        modifier = Modifier.clickable {
                            showIdentityPickerFor = null
                            deleteCredentials(identity = identity)
                        }
                    )
                }
            }
        }
    }

    private fun setupCallback() {
        OpenCredentialSDK.setCallback(object : OpenCredentialSDK.Callback {
            override fun onConsentApproved(organizationId: String, organizationName: String, inviteCode: String) {
                Log.i(TAG, "Consent approved for $organizationName, launching login...")
                pendingOrgId = organizationId
                pendingOrgName = organizationName
                pendingInviteCode = inviteCode
                runOnUiThread {
                    statusText = "Consent given for $organizationName. Logging in..."
                }
                OpenCredentialSDK.launchLogin(this@MainActivity)
            }

            override fun onLoginCompleted() {
                Log.i(TAG, "Login completed, launching credential selection...")
                runOnUiThread {
                    statusText = "Login successful! Select credentials to share..."
                }
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

    private fun deleteCredentials(identity: OCIdentity? = null) {
        isLoading = true
        thread {
            try {
                OpenCredentialSDK.deleteCredentials(identity)
                val remaining = try {
                    OpenCredentialSDK.getCredentialDetails()
                } catch (_: Exception) {
                    emptyList()
                }
                runOnUiThread {
                    isLoading = false
                    credentialInfos = remaining
                    hasCredentials = remaining.isNotEmpty()
                    statusText = "Credentials deleted"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete credentials", e)
                runOnUiThread {
                    isLoading = false
                    statusText = "Failed to delete credentials"
                }
            }
        }
    }

    private fun checkCredentials() {
        thread {
            try {
                val infos = OpenCredentialSDK.getCredentialDetails()
                runOnUiThread {
                    credentialInfos = infos
                    hasCredentials = infos.isNotEmpty()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check credentials", e)
            }
        }
    }

    private fun clearPendingOrg() {
        pendingOrgId = null
        pendingOrgName = null
        pendingInviteCode = null
    }

}
