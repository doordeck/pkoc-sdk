package com.sentryinteractive.opencredential.sdk.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentryinteractive.opencredential.api.credential.Credential
import com.sentryinteractive.opencredential.api.credential.CredentialFilter
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import com.sentryinteractive.opencredential.sdk.R
import com.sentryinteractive.opencredential.sdk.grpc.CredentialService
import com.sentryinteractive.opencredential.sdk.grpc.OrganizationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for selecting credentials to share with an organization.
 */
class CredentialSelectionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OC_CredSelection"

        const val EXTRA_ORGANIZATION_ID = "oc_organization_id"
        const val EXTRA_ORGANIZATION_NAME = "oc_organization_name"
        const val EXTRA_INVITE_CODE = "oc_invite_code"

        /**
         * Convenience launcher without organization context (e.g., from LoginActivity).
         */
        @JvmStatic
        fun launch(activity: Activity) {
            val intent = Intent(activity, CredentialSelectionActivity::class.java)
            activity.startActivity(intent)
        }
    }

    private lateinit var credentialService: CredentialService
    private lateinit var organizationService: OrganizationService

    private var credentials by mutableStateOf<List<Credential>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var isSubmitting by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private var organizationId: String? = null
    private var organizationName: String? = null
    private var inviteCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) { onCancelClicked() }

        credentialService = CredentialService()
        organizationService = OrganizationService()

        organizationId = intent.getStringExtra(EXTRA_ORGANIZATION_ID)
        organizationName = intent.getStringExtra(EXTRA_ORGANIZATION_NAME)
        inviteCode = intent.getStringExtra(EXTRA_INVITE_CODE)

        setContent {
            MaterialTheme {
                CredentialSelectionScreen()
            }
        }
    }

    @Composable
    private fun CredentialSelectionScreen() {
        val scope = rememberCoroutineScope()
        val selectionState = remember(credentials) {
            MutableList(credentials.size) { false }.toMutableStateList()
        }

        LaunchedEffect(Unit) {
            loadCredentials()
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
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { errorMessage = null; isLoading = true; scope.launch { loadCredentials() } }) {
                                Text(getString(R.string.oc_retry))
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp)
                        ) {

                            HeaderSection()

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            if (credentials.isEmpty()) {
                                Text(
                                    text = getString(R.string.oc_credentials_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                CredentialList(selectionState)
                            }


                            AddNewEmailButton()
                        }
                    }
                }
            }


            BottomButtons(scope, selectionState)
        }
    }

    @Composable
    private fun HeaderSection() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "– – – >",
                    color = MaterialTheme.colorScheme.outlineVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val orgName = organizationName
            if (orgName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getString(R.string.oc_credentials_permission, orgName),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = getString(R.string.oc_credentials_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    @Composable
    private fun CredentialList(selectionState: MutableList<Boolean>) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            credentials.forEachIndexed { index, credential ->
                val identity = credential.identity
                val label = when {
                    identity.hasEmail() -> identity.email
                    identity.hasPhone() -> identity.phone
                    else -> getString(R.string.oc_unknown_identity)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectionState[index] = !selectionState[index] }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selectionState[index]) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                HorizontalDivider()
            }
        }
    }

    @Composable
    private fun AddNewEmailButton() {
        TextButton(
            onClick = { launchLogin() },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(getString(R.string.oc_credentials_add_email))
        }
    }

    @Composable
    private fun BottomButtons(
        scope: kotlinx.coroutines.CoroutineScope,
        selectionState: List<Boolean>
    ) {
        val hasOrgContext = organizationId != null && inviteCode != null

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (hasOrgContext) {
                    Button(
                        onClick = {
                            scope.launch { onApproveClicked(selectionState) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = selectionState.any { it } && !isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(getString(R.string.oc_approve))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(
                    onClick = { onCancelClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    Text(getString(R.string.oc_cancel))
                }
            }
        }
    }

    private fun launchLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }

    private suspend fun loadCredentials() {
        try {
            val response = withContext(Dispatchers.IO) {
                credentialService.getCredentials(CredentialFilter.CREDENTIAL_FILTER_SAME_KEY)
            }
            credentials = response.credentialsList
            isLoading = false
            errorMessage = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load credentials", e)
            isLoading = false
            errorMessage = getString(R.string.oc_error_load_credentials)
        }
    }

    private suspend fun onApproveClicked(selectionState: List<Boolean>) {
        val selectedCredentialBytes = mutableListOf<ByteArray>()
        val selectedIdentities = mutableListOf<com.sentryinteractive.opencredential.api.common.Identity>()

        selectionState.forEachIndexed { index, selected ->
            if (selected) {
                val cred = credentials[index]
                selectedCredentialBytes.add(cred.credential.toByteArray())
                selectedIdentities.add(cred.identity)
            }
        }

        if (selectedCredentialBytes.isEmpty()) return

        val orgId = organizationId
        val code = inviteCode

        if (orgId != null && code != null) {
            isSubmitting = true
            try {
                withContext(Dispatchers.IO) {
                    for (identity in selectedIdentities) {
                        organizationService.shareCredentialWithOrganization(orgId, identity, code)
                    }
                }
                isSubmitting = false
                completeFlow(selectedCredentialBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share credentials", e)
                isSubmitting = false
                errorMessage = getString(R.string.oc_error_share_credentials)
            }
        } else {
            completeFlow(selectedCredentialBytes)
        }
    }

    private fun completeFlow(selectedCredentialBytes: List<ByteArray>) {
        val result = selectedCredentialBytes.toTypedArray()
        OpenCredentialSDK.getCallback()?.onCompleted(result)
        finish()
    }

    private fun onCancelClicked() {
        OpenCredentialSDK.getCallback()?.onCancelled()
        finish()
    }

}
