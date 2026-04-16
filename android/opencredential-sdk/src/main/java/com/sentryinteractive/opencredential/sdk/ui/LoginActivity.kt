package com.sentryinteractive.opencredential.sdk.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.protobuf.ByteString
import com.sentryinteractive.opencredential.api.common.CredentialType
import com.sentryinteractive.opencredential.api.verification.AndroidKeyAttestation
import com.sentryinteractive.opencredential.api.verification.DeviceAttestation
import com.sentryinteractive.opencredential.sdk.CredentialStore
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK
import com.sentryinteractive.opencredential.sdk.R
import com.sentryinteractive.opencredential.sdk.Signer
import com.sentryinteractive.opencredential.sdk.grpc.VerificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Login activity that handles email verification and 2FA code entry.
 * On success, sets RESULT_OK, calls [OpenCredentialSDK.Callback.onLoginCompleted], and finishes.
 */
class LoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OC_LoginActivity"
    }

    private lateinit var credentialStore: CredentialStore
    private lateinit var verificationService: VerificationService

    private var email by mutableStateOf("")
    private var code by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var codeStatus by mutableStateOf<String?>(null)
    private var codeSent by mutableStateOf(false)
    private var verificationToken: String? = null

    /**
     * Signer minted in onSendCode; used to sign onVerify. `AtomicReference` because
     * `onDestroy()` / back-press cleanup may run concurrently with `onVerify()` (which
     * suspends across `Dispatchers.IO`). Whoever calls `getAndSet(null)` first "claims"
     * the signer; later callers see `null` and no-op. Prevents double-forget / late-forget
     * races where `onDestroy` could delete the AndroidKeyStore alias while `onVerify` is
     * still in flight.
     */
    private val pendingSigner = AtomicReference<Signer?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            forgetPendingSigner()
            OpenCredentialSDK.getCallback()?.onCancelled()
            finish()
        }

        credentialStore = CredentialStore(this)
        verificationService = VerificationService()

        setContent {
            MaterialTheme {
                LoginScreen()
            }
        }
    }

    @Composable
    private fun LoginScreen() {
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = getString(R.string.oc_login_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!codeSent) {
                EmailStep(scope)
            } else {
                CodeStep(scope)
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }

    @Composable
    private fun EmailStep(scope: kotlinx.coroutines.CoroutineScope) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text(getString(R.string.oc_email_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(8.dp)
            )

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { scope.launch { onSendCode() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(getString(R.string.oc_send_code))
            }
        }
    }

    @Composable
    private fun CodeStep(scope: kotlinx.coroutines.CoroutineScope) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = getString(R.string.oc_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it; errorMessage = null },
                label = { Text(getString(R.string.oc_code_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )

            codeStatus?.let { status ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { scope.launch { onVerify() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(getString(R.string.oc_verify))
            }

            TextButton(
                onClick = {
                    codeSent = false
                    code = ""
                    errorMessage = null
                    codeStatus = null
                    scope.launch { onSendCode() }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(getString(R.string.oc_resend_code))
            }
        }
    }

    private suspend fun onSendCode() {
        val emailText = email.trim()
        if (emailText.isEmpty()) {
            errorMessage = getString(R.string.oc_email_required)
            return
        }

        // If a previous attempt left an uncommitted signer behind, throw it away first.
        forgetPendingSigner()

        isLoading = true
        errorMessage = null

        try {
            val (signer, response) = withContext(Dispatchers.IO) {
                val provider = OpenCredentialSDK.getCryptoProvider()
                    ?: throw IllegalStateException("CryptoProvider not initialized. Call OpenCredentialSDK.initialize() first.")

                // Bootstrap: fetch attestation challenge anonymously, then mint a fresh credential
                // signer bound to it. The CryptoProvider populates attestationDocument when its
                // backend supports hardware attestation; otherwise the credential registers as
                // attested=false (server-side soft-fail).
                val challenge = verificationService.getAttestationChallenge()
                val attested = provider.createSigner(challenge.challenge.toByteArray())
                    ?: throw IllegalStateException("Failed to create credential signer.")

                try {
                    val attestation: DeviceAttestation? = attested.attestationDocument?.let { certChain ->
                        DeviceAttestation.newBuilder()
                            .setChallengeToken(challenge.challengeToken)
                            .setAndroid(
                                AndroidKeyAttestation.newBuilder()
                                    .setCertificateChain(ByteString.copyFrom(certChain))
                                    .build()
                            )
                            .build()
                    }

                    val credentialDer = attested.signer.publicKeyDer
                        ?: throw IllegalStateException("Newly minted signer has no public key — cannot register.")

                    val resp = verificationService.startEmailVerification(
                        signer = attested.signer,
                        email = emailText,
                        credential = credentialDer,
                        credentialType = CredentialType.CREDENTIAL_TYPE_P256,
                        attestation = attestation
                    )
                    attested.signer to resp
                } catch (e: Exception) {
                    // Roll back the uncommitted signer so it doesn't become an orphan.
                    provider.forget(attested.signer)
                    throw e
                }
            }

            pendingSigner.set(signer)
            verificationToken = response.verificationToken
            credentialStore.saveEmail(emailText)
            codeSent = true
            isLoading = false
            codeStatus = getString(R.string.oc_code_sent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start verification", e)
            isLoading = false
            errorMessage = getString(R.string.oc_error_send_code)
        }
    }

    private suspend fun onVerify() {
        val codeText = code.trim()
        if (codeText.isEmpty()) {
            errorMessage = getString(R.string.oc_code_required)
            return
        }

        val token = verificationToken
        if (token == null) {
            errorMessage = getString(R.string.oc_error_no_token)
            return
        }

        // Atomically claim the pending signer. If `forgetPendingSigner()` (called from
        // onDestroy / back-press) runs concurrently, whichever call reaches `getAndSet`
        // first wins; the other sees `null` and no-ops.
        val signer = pendingSigner.getAndSet(null)
        if (signer == null) {
            errorMessage = getString(R.string.oc_error_no_token)
            return
        }

        isLoading = true
        errorMessage = null

        try {
            withContext(Dispatchers.IO) {
                verificationService.completeEmailVerification(signer, token, codeText)
            }

            // Server-side credential is now real. Promote the signer from "uncommitted" to
            // "managed" so subsequent listSigners() picks it up.
            val confirmed = OpenCredentialSDK.getCryptoProvider()?.confirm(signer) ?: false
            if (!confirmed) {
                Log.w(TAG, "Signer confirm failed after successful registration")
            }

            isLoading = false

            setResult(RESULT_OK)
            OpenCredentialSDK.getCallback()?.onLoginCompleted()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete verification", e)
            // Roll back the signer we just claimed — it's still uncommitted server-side.
            try {
                OpenCredentialSDK.getCryptoProvider()?.forget(signer)
            } catch (forgetError: Exception) {
                Log.w(TAG, "Failed to forget signer after verification failure", forgetError)
            }
            isLoading = false
            errorMessage = getString(R.string.oc_error_verify_code)
        }
    }

    /**
     * Atomically claim and delete the [pendingSigner] (if any). Idempotent and thread-safe:
     * `getAndSet(null)` ensures only the first caller walks away with the signer; concurrent
     * or subsequent calls see `null` and no-op. Used by failure paths and lifecycle teardown
     * to prevent orphaned AndroidKeyStore aliases.
     */
    private fun forgetPendingSigner() {
        val signer = pendingSigner.getAndSet(null) ?: return
        try {
            OpenCredentialSDK.getCryptoProvider()?.forget(signer)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forget pending signer", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the activity is being destroyed before onVerify successfully confirmed the
        // signer, the signer is uncommitted and would otherwise be an orphan. Clean it up.
        forgetPendingSigner()
    }

}
