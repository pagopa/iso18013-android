package it.pagopa.cbor_implementation.cose

import android.content.Context
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
import it.pagopa.cbor_implementation.document_manager.SignedWithAuthKeyResult
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.extensions.isDer
import it.pagopa.cbor_implementation.extensions.isDeviceSecure
import it.pagopa.cbor_implementation.extensions.supportStrongBox
import it.pagopa.cbor_implementation.helper.addBcIfNeeded
import kotlinx.io.files.Path
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * With this class you can sign every object with COSE*/
class COSEManager(val context: Context) {
    init {
        addBcIfNeeded()
    }

    private val _context = context.applicationContext
    var storageDir: File = _context.noBackupFilesDir
    var useEncryption: Boolean = true
    var userAuth: Boolean = context.isDeviceSecure
    var userAuthTimeoutInMillis: Long = AUTH_TIMEOUT

    /**
     * Sets whether to require user authentication to sign.
     * If the device is not secured, this will be set to false.
     * @param enable
     * @return [COSEManager]
     */
    fun enableUserAuth(enable: Boolean) = apply {
        this.userAuth =
            enable && context.isDeviceSecure
    }

    /**
     * The directory to store data files in.
     * By default, the [Context.getNoBackupFilesDir] is used.
     *
     * @param storageDir
     * @return [COSEManager]
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
        alg: Algorithm.SupportedAlgorithms,
        alias: String = "pagoPaAlias"
    ): SignWithCOSEResult {
        try {
            val nonEmptyChallenge = attestationChallenge
                ?.takeUnless { it.isEmpty() }
                ?: generateRandomBytes()
            return CreateCOSE.with(
                this.storageEngine, createKeySettings(
                    nonEmptyChallenge,
                    strongBox && _context.supportStrongBox
                )
            ).withAlg(alg)
                .withAlias(alias)
                .sign(_context, data)
        } catch (e: Exception) {
            CborLogger.e("signWithCOSE", e.toString())
            return SignWithCOSEResult.Failure(e.toString())
        }
    }

    @CheckResult
    fun signWithDocumentKey(
        data: ByteArray,
        unsignedDocument: UnsignedDocument,
        alg: Algorithm.SupportedAlgorithms = Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA
    ): SignedWithAuthKeyResult {
        try {
            return CreateCOSE.signWithDocumentKey(data, unsignedDocument, alg)
        } catch (e: Exception) {
            CborLogger.e("signWithCOSE", e.toString())
            return SignedWithAuthKeyResult.Failure(e)
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
            val publicKeySpec = X509EncodedKeySpec(subjectPublicKeyInfo.getEncoded())
            val keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME)
            val pubKey = keyFactory.generatePublic(publicKeySpec)
            val derSignature = if (EcSignature.isDer(signature))
                signature
            else
                EcSignature.fromCoseEncoded(signature).toDer()
            val alg = algorithmFromProtectedHeader(protectedHeader)
            Signature.getInstance(alg, BouncyCastleProvider.PROVIDER_NAME).apply {
                initVerify(pubKey)
                update(data)
            }.verify(derSignature)
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