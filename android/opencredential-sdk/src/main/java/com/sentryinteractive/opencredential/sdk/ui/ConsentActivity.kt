package com.sentryinteractive.opencredential.sdk.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentryinteractive.opencredential.api.organization.Organization
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import com.sentryinteractive.opencredential.sdk.R
import com.sentryinteractive.opencredential.sdk.grpc.OrganizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Consent activity that displays organization information and asks the user
 * to consent to sharing their credential with the organization.
 */
class ConsentActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OC_ConsentActivity"
        const val EXTRA_INVITE_CODE = "oc_invite_code"
    }

    private lateinit var organizationService: OrganizationService

    private var organization by mutableStateOf<Organization?>(null)
    private var isLoading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private val inviteCode: String?
        get() = intent.getStringExtra(EXTRA_INVITE_CODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) { onCancelClicked() }

        organizationService = OrganizationService()

        val code = inviteCode
        if (code.isNullOrEmpty()) {
            OpenCredentialSDK.getCallback()?.onCancelled()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                ConsentScreen(code)
            }
        }
    }

    @Composable
    private fun ConsentScreen(inviteCode: String) {
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            loadOrganization(inviteCode)
        }

        Column(modifier = Modifier.fillMaxSize()) {

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = {
                                errorMessage = null
                                isLoading = true
                                scope.launch { loadOrganization(inviteCode) }
                            }) {
                                Text(getString(R.string.oc_retry))
                            }
                        }
                    }
                    else -> {
                        val org = organization
                        if (org != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 28.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OrgContent(org)
                            }
                        }
                    }
                }
            }


            BottomButtons(inviteCode)
        }
    }

    @Composable
    private fun OrgContent(org: Organization) {

        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))


        Text(
            text = getString(R.string.oc_consent_permission, org.name),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))


        Text(
            text = getString(R.string.oc_consent_sharing_info, org.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))


        DataRow(icon = Icons.Default.Email, label = getString(R.string.oc_consent_data_email))
        Spacer(modifier = Modifier.height(12.dp))
        DataRow(icon = Icons.Default.Phone, label = getString(R.string.oc_consent_data_phone))
        Spacer(modifier = Modifier.height(12.dp))
        DataRow(icon = Icons.Default.QrCode, label = getString(R.string.oc_consent_data_identifiers))

        Spacer(modifier = Modifier.height(20.dp))


        Text(
            text = getString(R.string.oc_consent_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))


        OrgCard(org)
    }

    @Composable
    private fun DataRow(icon: ImageVector, label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    @Composable
    private fun OrgCard(org: Organization) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = org.name,
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (org.hasContactAddress() && org.contactAddress.isNotEmpty()) {
                        Text(
                            text = org.contactAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (org.hasContactPhone() && org.contactPhone.isNotEmpty()) {
                        Text(
                            text = org.contactPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = org.contactEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun BottomButtons(inviteCode: String) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { onConsentClicked(inviteCode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = organization != null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(getString(R.string.oc_consent_proceed))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onCancelClicked() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(getString(R.string.oc_cancel))
                }
            }
        }
    }

    private suspend fun loadOrganization(inviteCode: String) {
        try {
            val org = withContext(Dispatchers.IO) {
                organizationService.getOrganizationByInviteCode(inviteCode)
            }
            organization = org
            isLoading = false
            errorMessage = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load organization", e)
            isLoading = false
            errorMessage = getString(R.string.oc_error_load_org)
        }
    }

    private fun onConsentClicked(inviteCode: String) {
        val org = organization ?: return
        OpenCredentialSDK.getCallback()?.onConsentApproved(
            org.organizationId,
            org.name,
            inviteCode
        )
        finish()
    }

    private fun onCancelClicked() {
        OpenCredentialSDK.getCallback()?.onCancelled()
        finish()
    }

}
