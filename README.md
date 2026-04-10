# OpenCredential SDK

[![Release](https://img.shields.io/github/v/release/doordeck/pkoc-sdk?label=latest)](https://github.com/doordeck/pkoc-sdk/releases/latest)

SDKs for integrating Sentry Interactive OpenCredential into Android and iOS apps. Provides gRPC-Web service clients, email verification (2FA), organization consent, and credential selection — all with built-in UI screens.

## Features

- **Email verification + 2FA** — Login screen with email input and 6-digit code verification
- **Organization consent** — Consent screen showing org details before credential sharing
- **Credential selection** — Choose which credentials to share with an organization
- **gRPC-Web client** — Built-in transport with RFC 9421 HTTP Message Signatures (P-256 ECDSA)

---

## Android (GitHub Packages)

### Installation

Add the GitHub Packages repository and SDK to your `build.gradle.kts`:

```kotlin
// In settings.gradle.kts (dependencyResolutionManagement.repositories)
maven {
    url = uri("https://maven.pkg.github.com/doordeck/pkoc-sdk")
    credentials {
        username = providers.gradleProperty("gpr.user").getOrElse("")
        password = providers.gradleProperty("gpr.key").getOrElse("")
    }
}
```

Set credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PAT_WITH_READ_PACKAGES
```

```kotlin
dependencies {
    implementation("com.sentryinteractive.opencredential:opencredential-sdk:latestVersion")
}
```

Or if building locally, include the module in your `settings.gradle.kts`:

```kotlin
include(":opencredential-sdk")
project(":opencredential-sdk").projectDir = file("path/to/pkoc-sdk/android/opencredential-sdk")
```

### Integration

#### 1. Initialize the SDK

In your `Application.onCreate()` or before any SDK calls:

```java
import com.sentryinteractive.opencredential.sdk.OpenCredentialSDK;

OpenCredentialSDK.initialize(new OpenCredentialSDK.CryptoProvider() {
    @Override
    public byte[] getPublicKeyDer() {
        // Return the DER-encoded P-256 public key from your KeyStore
        return CryptoProvider.GetPublicKey().getEncoded();
    }

    @Override
    public byte[] sign(byte[] data) {
        // Sign with the device's private key (DER-encoded ECDSA signature)
        return CryptoProvider.GetSignedMessage(data);
    }
});
```

#### 2. Set up the callback

The SDK uses callbacks to let you orchestrate the flow: consent → login → credential selection → share.

```java
OpenCredentialSDK.setCallback(new OpenCredentialSDK.Callback() {
    @Override
    public void onConsentApproved(String organizationId, String organizationName, String inviteCode) {
        // User consented — next step: launch login
        OpenCredentialSDK.launchLogin(activity);
        // Store orgId, orgName, inviteCode for use after login
    }

    @Override
    public void onLoginCompleted() {
        // Login succeeded — next step: launch credential selection with org context
        OpenCredentialSDK.launchCredentialSelection(activity, orgId, orgName, inviteCode);
    }

    @Override
    public void onCompleted(byte[][] selectedCredentials) {
        // Flow completed — user approved credential sharing
        // selectedCredentials contains the proto-encoded Credential bytes
    }

    @Override
    public void onCancelled() {
        // User cancelled the flow
    }
});
```

#### 3. Launch the flow

```java
// Launch consent with an invite code (starts the full flow via callbacks)
OpenCredentialSDK.launchConsent(activity, "your-invite-code");

// Or launch individual screens directly
OpenCredentialSDK.launchLogin(activity);
OpenCredentialSDK.launchCredentialSelection(activity, orgId, orgName, inviteCode);
```

---

## iOS (Swift Package Manager)

### Installation

Add the package in Xcode:

1. **File → Add Package Dependencies**
2. Enter the repository URL: `https://github.com/doordeck/pkoc-sdk.git`
3. Select `OpenCredentialSDK`

Or add to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/doordeck/pkoc-sdk.git", from: "latestVersion"),
],
targets: [
    .target(
        name: "YourApp",
        dependencies: ["OpenCredentialSDK"]
    ),
]
```

### Integration

#### 1. Initialize the SDK

In your `@main` App struct or `AppDelegate`:

```swift
import OpenCredentialSDK

@main
struct YourApp: App {
    init() {
        let sdk = OpenCredentialSDK.shared
        if !sdk.loadStoredKeys() {
            sdk.generateKeys()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

Or initialize with existing keys:

```swift
let sdk = OpenCredentialSDK.shared
sdk.initialize(
    privateKey: yourP256PrivateKey,
    publicKey: yourP256PublicKey
)
```

#### 2. Use individual views (chained flow)

Use closures to chain the steps: consent → login → credential selection.

```swift
// Step 1: Consent
OCConsentView(
    inviteCode: "your-invite-code",
    onProceed: { inviteCode, orgName, orgId in
        // Store org context, then present login
    },
    onCancel: {
        // User cancelled
    }
)

// Step 2: Login (presented after consent)
OCLoginView {
    // Login succeeded, present credential selection
}

// Step 3: Credential selection (presented after login)
OCCredentialSelectionView(
    organizationName: "Acme Corp",
    organizationId: "org-123",
    inviteCode: "invite-456",
    onApprove: { credentials in
        // User approved sharing — flow complete
    }
)
```

> **Note:** Without organization context (empty `organizationId`/`inviteCode`), the credential selection view shows credentials in read-only mode without the Approve button.

---

## Architecture

```
Invite Code
    │
    ▼
ConsentActivity / OCConsentView
    │ (loads org via OrganizationService.getOrganizationByInviteCode)
    │
    ▼ onConsentApproved / onProceed
    │
LoginActivity / OCLoginView
    │ (email verification + 2FA via VerificationService)
    │
    ▼ onLoginCompleted / onSuccess
    │
CredentialSelectionActivity / OCCredentialSelectionView
    │ (loads credentials via CredentialService.getCredentials)
    │ (shares via OrganizationService.shareCredentialWithOrganization)
    │
    ▼ onCompleted / onApprove
```

**gRPC-Web transport:** All API calls use gRPC-Web over HTTP/1.1 to `https://api.opencredential.sentryinteractive.com`. Requests are signed with RFC 9421 HTTP Message Signatures using the device's P-256 key pair.

---

## Breaking Changes

### 0.0.7 — Multi-key `CryptoProvider` and SDK-managed credential keys (Android)

The Android SDK now manages credential keys per-credential instead of per-device. Each registered identity gets its own key in AndroidKeyStore (StrongBox-preferred, TEE fallback), bound to a server-supplied attestation challenge at key-generation time. Multi-account is now safe — registering a second email no longer clobbers the first.

The default behavior is fully transparent: most apps should call `OpenCredentialSDK.initialize(context)` and never think about keys or attestation again. Custom `CryptoProvider` overrides remain available for HSM, AWS KMS, hardware token, or other non-AndroidKeyStore backends.

**Android — initialization**

```kotlin
// Before
OpenCredentialSDK.initialize(object : OpenCredentialSDK.CryptoProvider {
    override fun getPublicKeyDer(): ByteArray? = /* one device key */
    override fun sign(data: ByteArray): ByteArray? = /* sign with one device key */
})

// After (recommended): SDK manages credential keys for you
OpenCredentialSDK.initialize(applicationContext)

// After (advanced): bring your own multi-key provider
OpenCredentialSDK.initialize(MyCustomCryptoProvider())
```

**Android — `CryptoProvider` interface (only relevant if you bring your own)**

The single-key methods have been replaced by a `Signer`-based API where each key is represented by a `Signer` object that exposes its own public key and signing method. There are no string handles — the `Signer` instance itself *is* the identifier.

```kotlin
// Before
interface CryptoProvider {
    fun getPublicKeyDer(): ByteArray?
    fun sign(data: ByteArray): ByteArray?
}

// After
interface CryptoProvider {
    fun listSigners(): List<Signer>
    fun createSigner(attestationChallenge: ByteArray? = null): AttestedSigner?
    fun confirm(signer: Signer): Boolean
    fun forget(signer: Signer): Boolean
}

interface Signer {
    val publicKeyDer: ByteArray
    fun sign(data: ByteArray): ByteArray?
}

data class AttestedSigner(
    val signer: Signer,
    val attestationDocument: ByteArray? = null
)
```

A typical custom implementation keeps its per-key state (alias, handle, keychain index, whatever) inside its own private `Signer` class and returns instances of that class from `listSigners()` / `createSigner()`.

**Two-phase commit for new credentials.** `createSigner()` returns an *uncommitted* signer that can sign requests but is **not** yet returned by `listSigners()`. The SDK calls `confirm(signer)` after the credential has been successfully registered server-side, or `forget(signer)` if registration fails. This prevents orphaned local keys from accumulating when registration is abandoned mid-flow. Custom implementations must respect this contract: don't include uncommitted signers in `listSigners()` until `confirm()` is called.

**Display-oriented API**

```kotlin
data class OCCredentialInfo(
    val identity: OCIdentity,
    val attested: Boolean
)

// New: returns identity + attested flag for every credential
val infos: List<OCCredentialInfo> = OpenCredentialSDK.getCredentialDetails()

// Existing — now a thin wrapper around getCredentialDetails()
val identities: List<OCIdentity> = OpenCredentialSDK.getIdentities()
```

Use `getCredentialDetails()` if you want to render an attestation indicator next to each credential in your UI. Use `getIdentities()` if you only need the identity strings.

**Android — `OpenCredentialSDK.deleteCredentials`**

```kotlin
// Before
OpenCredentialSDK.deleteCredentials(identity, keyThumbprint)

// After — keyThumbprint parameter removed (obsolete in the multi-key model where each
// credential already has its own key)
OpenCredentialSDK.deleteCredentials(identity)
```

**Android — `OpenCredentialSDK.getKeyThumbprint()` removed**

The method no longer exists. If you need a credential's thumbprint, compute it directly from the `Signer.publicKeyDer` of the signer you're interested in.

**iOS** is unchanged in this release. iOS continues with a single device key per install; multi-account works there because iOS doesn't regenerate the key on registration. The now-removed `attestation_document: String` parameter on `OCVerificationService.startEmailVerification` is gone — iOS sends the request without it, and the server (where field 4 is now `reserved`) silently drops the old payload. Hardware attestation on iOS is not yet wired up — credentials registered from iOS will be `attested=false`.

### 0.0.6 — Typed `OCIdentity`

`getIdentities()` and `deleteCredentials()` now accept/return a typed `OCIdentity` (email or phone) instead of a plain `String`. The underlying proto models `Identity` as a `oneof { email, phone }`; the previous String-based API could not express phone identities and would have silently encoded them as emails.

**Android**

```kotlin
// Before
val identities: List<String> = OpenCredentialSDK.getIdentities()
OpenCredentialSDK.deleteCredentials(email = "user@example.com")

// After
val identities: List<OCIdentity> = OpenCredentialSDK.getIdentities()
OpenCredentialSDK.deleteCredentials(identity = OCIdentity.Email("user@example.com"))
// or, for a phone identity:
OpenCredentialSDK.deleteCredentials(identity = OCIdentity.Phone("+15551234567"))
```

**iOS**

```swift
// Before
let identities: [String] = try await OpenCredentialSDK.shared.getIdentities()
try await OpenCredentialSDK.shared.deleteCredentials(email: "user@example.com")

// After
let identities: [OCIdentity] = try await OpenCredentialSDK.shared.getIdentities()
try await OpenCredentialSDK.shared.deleteCredentials(identity: .email("user@example.com"))
// or, for a phone identity:
try await OpenCredentialSDK.shared.deleteCredentials(identity: .phone("+15551234567"))
```
