package it.pagopa.cbor_implementation.cose

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.CheckResult
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcSignature
import com.android.identity.crypto.toDer
import com.android.identity.securearea.KeyPurpose
import com.android.identity.storage.StorageEngine
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder.Companion.AUTH_TIMEOUT
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import kotlinx.io.files.Path
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * With this class you can sign every object with COSE*/
class COSEManager(val context: Context) {
    init {
        val isBcAlreadyIntoProviders = Security.getProviders().any {
            it.name == BouncyCastleProvider.PROVIDER_NAME
        }
        if (!isBcAlreadyIntoProviders) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } else {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    private val _context = context.applicationContext
    var storageDir: File = _context.noBackupFilesDir
    var useEncryption: Boolean = true
    var userAuth: Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    var userAuthTimeoutInMillis: Long = AUTH_TIMEOUT
    private val supportStrongBox by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    /**
     * The directory to store data files in.
     * By default, the [Context.getNoBackupFilesDir] is used.
     *
     * @param storageDir
     * @return [DocumentManagerBuilder]
     */
    fun withStorageDir(storageDir: File) = apply {
        this.storageDir = storageDir
    }

    internal val storageEngine: StorageEngine by lazy {
        val path = Path(File(storageDir.path, "pagopa-identity.bin").path)
        AndroidStorageEngine.Builder(_context, path)
            .setUseEncryption(useEncryption)
            .build()
    }

    /**
     * Sets whether to encrypt the values stored on disk.
     * Note that keys are not encrypted, only values.
     * By default, this is set to true.
     *
     * @param useEncryption
     * @return [DocumentManagerBuilder]
     */
    fun useEncryption(useEncryption: Boolean) = apply { this.useEncryption = useEncryption }
    private fun generateRandomBytes(): ByteArray {
        val secureRandom = SecureRandom()
        val randomBytes = ByteArray(10)
        secureRandom.nextBytes(randomBytes)
        return randomBytes
    }

    private fun createKeySettings(
        challenge: ByteArray,
        useStrongBox: Boolean,
    ) = AndroidKeystoreCreateKeySettings.Builder(challenge)
        .setEcCurve(EcCurve.P256)
        .setUseStrongBox(useStrongBox)
        .setUserAuthenticationRequired(
            userAuth, userAuthTimeoutInMillis,
            DocumentManagerBuilder.AUTH_TYPE
        )
        .setKeyPurposes(setOf(KeyPurpose.SIGN))
        .build()

    @CheckResult
    fun signWithCOSE(
        data: ByteArray,
        strongBox: Boolean,
        attestationChallenge: ByteArray?,
        alg: Algorithm.SupportedAlgorithms
    ): SignWithCOSEResult {
        try {
            val nonEmptyChallenge = attestationChallenge
                ?.takeUnless { it.isEmpty() }
                ?: generateRandomBytes()
            return CreateCOSE.with(
                this.storageEngine, createKeySettings(
                    nonEmptyChallenge,
                    strongBox && supportStrongBox
                )
            ).withAlg(alg).sign(_context, data)
        } catch (e: Exception) {
            CborLogger.e("signWithCOSE", e.toString())
            return SignWithCOSEResult.Failure(e.toString())
        }
    }

    private fun algorithmFromProtectedHeader(protectedHeader: CBORObject): String {
        return when (protectedHeader[1].AsInt32()) {
            -7 -> "SHA256withECDSA"
            -35 -> "SHA384withECDSA"
            -36 -> "SHA512withECDSA"
            -37 -> "SHA256withRSA"
            -38 -> "SHA384withRSA"
            -39 -> "SHA512withRSA"
            else -> throw IllegalArgumentException("Unsupported algorithm")
        }
    }
    /*

        @Throws(Exception::class)
        private fun getPublicKey(publicKey: ByteArray, algorithm: String): PublicKey {
            val keySpec = X509EncodedKeySpec(publicKey)
            val keyFactory = when {
                algorithm.contains("ECDSA") -> KeyFactory.getInstance("EC", "BC")
                algorithm.contains("RSA") -> KeyFactory.getInstance("RSA", "BC")
                else -> throw IllegalArgumentException("Unsupported key algorithm")
            }
            return keyFactory.generatePublic(keySpec)
        }
    */

    private fun verifySign1(
        signature: ByteArray,
        publicKey: ByteArray,
        protectedHeader: CBORObject,
        data: ByteArray
    ): Boolean {
        return try {
            val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey)
            // Crea una chiave pubblica
            val publicKeySpec = X509EncodedKeySpec(subjectPublicKeyInfo.getEncoded())
            val keyFactory = KeyFactory.getInstance("ECDSA", "BC")

            val pubKey = keyFactory.generatePublic(publicKeySpec)

            // Converte la firma nel formato appropriato
            val derSignature = EcSignature.fromCoseEncoded(signature)

            // Inizializza la verifica della firma utilizzando l'algoritmo ECDSA con SHA-256
            val sig = Signature.getInstance(algorithmFromProtectedHeader(protectedHeader), "BC")
            sig.initVerify(pubKey)
            sig.update(data)

            // Verifica la firma
            sig.verify(derSignature.toDer())
        } catch (e: Exception) {
            CborLogger.e("verifying", e.toString())
            false
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun verifySign1(
        dataSigned: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        try {
            val cborArray = CBORObject.DecodeFromBytes(dataSigned)
            // extracting CBOR components
            val protectedHeader = cborArray[0].GetByteString()
            val data = cborArray[2].GetByteString()
            val array = CBORObject.NewArray()
            array.Add("Signature1")
            array.Add(protectedHeader)
            array.Add(byteArrayOf())
            array.Add(data)
            val dataToVerify = array.EncodeToBytes()
            CborLogger.i("dataToVerify", Base64.encode(dataToVerify))
            CborLogger.i("dataToVerify length", dataToVerify.size.toString())
            val signature = cborArray[3].GetByteString()
            CborLogger.i("PUBKEY HERE", Base64.encode(publicKey))
            CborLogger.i("DATA HERE", Base64.encode(data))
            return verifySign1(
                signature,
                publicKey,
                CBORObject.DecodeFromBytes(cborArray[0].GetByteString()),
                dataToVerify
            )
        } catch (e: Exception) {
            CborLogger.e("verifying", e.toString())
            return false
        }
    }
}