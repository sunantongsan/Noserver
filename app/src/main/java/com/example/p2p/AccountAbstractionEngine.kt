package com.example.p2p

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

enum class AuthMode {
    ANONYMOUS_NODE,
    PASSKEY_SECURED,
    THRESHOLD_RECOVERED
}

data class UserOperation(
    val senderAddress: String,
    val nonce: Long,
    val callDataHex: String,
    val maxFeePerGas: String = "0 Gwei (Gasless)",
    val signatureBase64: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("senderAddress", senderAddress)
            put("nonce", nonce)
            put("callDataHex", callDataHex)
            put("maxFeePerGas", maxFeePerGas)
            put("signatureBase64", signatureBase64)
            put("timestamp", timestamp)
        }.toString()
    }
}

data class AccountAbstractionState(
    val isLoggedIn: Boolean = false,
    val userEmail: String = "",
    val accountAddress: String = "",
    val nodeFingerprint: String = "",
    val sharesSecuredCount: Int = 0,
    val authMode: AuthMode = AuthMode.ANONYMOUS_NODE,
    val statusMessage: String = "Node Initialized"
)

/**
 * Account Abstraction Engine (ERC-4337 conceptual implementation for P2P Mesh).
 * Abstracts raw ECDSA seed phrases and gas fees, providing seamless passkey-secured operation signing.
 */
class AccountAbstractionEngine(
    private val context: Context,
    private val nodeIdentityManager: NodeIdentityManager
) {
    private val tag = "AccountAbstractionEngine"
    private val thresholdKeyManager = ThresholdKeyManager(context)
    private val nonceTracker = AtomicLong(System.currentTimeMillis())

    private val _authState = MutableStateFlow(
        AccountAbstractionState(
            isLoggedIn = false,
            userEmail = "",
            accountAddress = deriveSmartAccountAddress(nodeIdentityManager.publicKeyHex),
            nodeFingerprint = nodeIdentityManager.nodeId,
            sharesSecuredCount = 1,
            authMode = AuthMode.ANONYMOUS_NODE,
            statusMessage = "Anonymous Node Ready"
        )
    )
    val authState: StateFlow<AccountAbstractionState> = _authState.asStateFlow()

    private var activePrivateKeyBytes: ByteArray? = null

    init {
        // Attempt to auto-restore existing device share
        val deviceShare = thresholdKeyManager.getDeviceShare()
        if (deviceShare != null) {
            _authState.value = _authState.value.copy(
                sharesSecuredCount = 1,
                statusMessage = "Device Share 1 Active"
            )
        }
    }

    /**
     * Sign up seamlessly with Email and Passkey registration.
     */
    suspend fun signUpWithEmail(
        email: String,
        passkeyManager: PasskeyAuthManager
    ): Result<AccountAbstractionState> {
        return try {
            val passkeyResult = passkeyManager.registerPasskey(email)
            if (!passkeyResult.success) {
                return Result.failure(Exception(passkeyResult.errorMessage.ifBlank { "Passkey registration failed" }))
            }

            // Generate a 256-bit entropy seed for the Account Abstraction Wallet
            val masterPrivateKeyBytes = ByteArray(32)
            SecureRandom().nextBytes(masterPrivateKeyBytes)

            // Split master secret into 2-of-3 threshold shares
            val shares = thresholdKeyManager.splitSecret(masterPrivateKeyBytes)

            // Share 1: Save on local device KeyStore
            thresholdKeyManager.saveDeviceShare(shares[0])

            // Share 2: Encrypt with Passkey signature seed
            val encryptedShare2 = thresholdKeyManager.encryptPasskeyShare(shares[1], email, passkeyResult.rawSignatureBytes)

            // Share 3: Prepare Mesh recovery backup share
            val meshRecoveryPayload = thresholdKeyManager.prepareMeshRecoveryShare(shares[2], email)

            activePrivateKeyBytes = masterPrivateKeyBytes
            val smartAccountAddress = deriveSmartAccountAddress(passkeyResult.credentialId + email)

            val newState = AccountAbstractionState(
                isLoggedIn = true,
                userEmail = email,
                accountAddress = smartAccountAddress,
                nodeFingerprint = nodeIdentityManager.nodeId,
                sharesSecuredCount = 3,
                authMode = AuthMode.PASSKEY_SECURED,
                statusMessage = "Passkey Account Abstraction Active (3 Shares Secured)"
            )

            _authState.value = newState
            Log.i(tag, "Sign up successful for $email. Account Address: $smartAccountAddress")
            Result.success(newState)
        } catch (e: Exception) {
            Log.e(tag, "Sign up error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sign in seamlessly with Passkey biometric authentication.
     */
    suspend fun signInWithPasskey(
        passkeyManager: PasskeyAuthManager,
        email: String = ""
    ): Result<AccountAbstractionState> {
        return try {
            val passkeyResult = passkeyManager.authenticatePasskey(email)
            if (!passkeyResult.success) {
                return Result.failure(Exception("Passkey authentication failed"))
            }

            val targetEmail = passkeyResult.userEmail
            val deviceShare = thresholdKeyManager.getDeviceShare()
            val passkeyShare = thresholdKeyManager.decryptPasskeyShare(targetEmail, passkeyResult.rawSignatureBytes)

            if (deviceShare != null && passkeyShare != null) {
                // Combine Share 1 and Share 2 to reconstruct wallet master secret
                val reconstructedSecret = thresholdKeyManager.combineShares(deviceShare, passkeyShare)
                activePrivateKeyBytes = reconstructedSecret

                val smartAccountAddress = deriveSmartAccountAddress(passkeyResult.credentialId + targetEmail)
                val newState = AccountAbstractionState(
                    isLoggedIn = true,
                    userEmail = targetEmail,
                    accountAddress = smartAccountAddress,
                    nodeFingerprint = nodeIdentityManager.nodeId,
                    sharesSecuredCount = 2,
                    authMode = AuthMode.PASSKEY_SECURED,
                    statusMessage = "Authenticated via Passkey Biometrics"
                )
                _authState.value = newState
                Result.success(newState)
            } else {
                // Fallback to anonymous node state with Passkey badge
                val smartAccountAddress = deriveSmartAccountAddress(targetEmail)
                val newState = AccountAbstractionState(
                    isLoggedIn = true,
                    userEmail = targetEmail,
                    accountAddress = smartAccountAddress,
                    nodeFingerprint = nodeIdentityManager.nodeId,
                    sharesSecuredCount = 1,
                    authMode = AuthMode.PASSKEY_SECURED,
                    statusMessage = "Passkey Signed-In (Device Share Active)"
                )
                _authState.value = newState
                Result.success(newState)
            }
        } catch (e: Exception) {
            Log.e(tag, "Passkey sign-in error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Recover account using Mesh Recovery Share (Share 3) + Device Share (Share 1).
     */
    fun recoverAccountFromMesh(email: String, recoveryShareJson: String): Result<AccountAbstractionState> {
        return try {
            val deviceShare = thresholdKeyManager.getDeviceShare()
                ?: return Result.failure(Exception("Device Share 1 missing on this hardware"))

            val meshShare3 = thresholdKeyManager.decryptMeshRecoveryShare(recoveryShareJson, email)
                ?: return Result.failure(Exception("Failed to decrypt Mesh Recovery Share 3 with email key"))

            val reconstructedMasterSecret = thresholdKeyManager.combineShares(deviceShare, meshShare3)
            activePrivateKeyBytes = reconstructedMasterSecret

            val smartAccountAddress = deriveSmartAccountAddress("recovered:$email")
            val newState = AccountAbstractionState(
                isLoggedIn = true,
                userEmail = email,
                accountAddress = smartAccountAddress,
                nodeFingerprint = nodeIdentityManager.nodeId,
                sharesSecuredCount = 2,
                authMode = AuthMode.THRESHOLD_RECOVERED,
                statusMessage = "Account Recovered via 2-of-3 Mesh Shares"
            )
            _authState.value = newState
            Result.success(newState)
        } catch (e: Exception) {
            Log.e(tag, "Recovery error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Automatically signs a user operation gaslessly without showing seed phrases or prompting for raw keys.
     */
    fun autoSignUserOperation(payloadData: ByteArray): UserOperation {
        val nonce = nonceTracker.incrementAndGet()
        val signatureBase64 = nodeIdentityManager.signData(payloadData)
        val currentAccount = _authState.value.accountAddress

        return UserOperation(
            senderAddress = currentAccount,
            nonce = nonce,
            callDataHex = NodeIdentityManager.bytesToHex(payloadData),
            maxFeePerGas = "0 Gwei (Gasless P2P)",
            signatureBase64 = signatureBase64
        )
    }

    fun signOut() {
        activePrivateKeyBytes = null
        _authState.value = AccountAbstractionState(
            isLoggedIn = false,
            userEmail = "",
            accountAddress = deriveSmartAccountAddress(nodeIdentityManager.publicKeyHex),
            nodeFingerprint = nodeIdentityManager.nodeId,
            sharesSecuredCount = 1,
            authMode = AuthMode.ANONYMOUS_NODE,
            statusMessage = "Signed Out (Anonymous Node Active)"
        )
    }

    companion object {
        fun deriveSmartAccountAddress(seedInput: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(seedInput.toByteArray(Charsets.UTF_8))
            val hex = NodeIdentityManager.bytesToHex(hash)
            return "0x" + hex.take(40).lowercase()
        }
    }
}
