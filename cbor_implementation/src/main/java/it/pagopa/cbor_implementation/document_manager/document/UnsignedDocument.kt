package it.pagopa.cbor_implementation.document_manager.document

import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.crypto.toDer
import com.android.identity.securearea.KeyLockedException
import it.pagopa.cbor_implementation.document_manager.SignedWithAuthKeyResult
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.document_manager.createdAt
import it.pagopa.cbor_implementation.document_manager.docType
import it.pagopa.cbor_implementation.document_manager.documentName
import it.pagopa.cbor_implementation.document_manager.requiresUserAuth
import it.pagopa.cbor_implementation.document_manager.state
import it.pagopa.cbor_implementation.document_manager.usesStrongBox
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.Instant
import com.android.identity.document.Document as BaseDocument

/**
 * A [UnsignedDocument] is a document that is in the process of being issued.
 * It contains the information required to issue the document and can be used to sign the
 * proof of possession required by the issuers using the [UnsignedDocument.signWithAuthKey] method.
 *
 * Use the [it.pagopa.cbor_implementation.document_manager.DocumentManager.createDocument] method to create a [UnsignedDocument]
 *
 * Once the document is issued and document's data are available by the issuer, use the
 * [it.pagopa.cbor_implementation.document_manager.DocumentManager.storeIssuedDocument] to store the document. This will transform the [UnsignedDocument] to
 * an [it.pagopa.cbor_implementation.document_manager.document.IssuedDocument]
 *
 * @property name the name of the document. This name can be updated before the document is issued
 * @property certificatesNeedAuth list of certificates that will be used for issuing the document
 * @property publicKey public key of the first certificate in [certificatesNeedAuth] list to be included in mobile security object that it will be signed from issuer
 */
open class UnsignedDocument(
    override val id: DocumentId,
    name: String,
    final override val docType: String,
    override val usesStrongBox: Boolean,
    override val requiresUserAuth: Boolean,
    override val createdAt: Instant,
    val certificatesNeedAuth: List<X509Certificate>
) : Document {

    @JvmSynthetic
    internal var base: BaseDocument? = null

    internal val ecPublicKey: EcPublicKey?
        get() = base?.pendingCredentials
            ?.firstOrNull { it is SecureAreaBoundCredential }
            ?.let { it as SecureAreaBoundCredential }
            ?.attestation
            ?.publicKey

    override var name: String = name
        set(value) {
            field = value
            base?.let { it.documentName = value }
        }

    override val state: Document.State
        get() = base?.state ?: Document.State.UNSIGNED

    val publicKey: PublicKey
        get() = certificatesNeedAuth.first().publicKey

    /**
     * Sign given data with authentication key
     *
     * Available algorithms are:
     * - [Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA]
     *
     * @param data to be signed
     * @param alg algorithm to be used for signing the data (example: "SHA256withECDSA")
     * @return [SignedWithAuthKeyResult.Success] containing the signature if successful,
     * [SignedWithAuthKeyResult.UserAuthRequired] if user authentication is required to sign data,
     * [SignedWithAuthKeyResult.Failure] if an error occurred while signing the data
     */
    fun signWithAuthKey(
        data: ByteArray,
        alg: Algorithm.SupportedAlgorithms = Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA
    ): SignedWithAuthKeyResult {
        return when (val cred = base?.pendingCredentials
            ?.firstOrNull { it is SecureAreaBoundCredential }
            ?.let { it as SecureAreaBoundCredential }) {

            null -> SignedWithAuthKeyResult.Failure(Exception("Not initialized correctly. Use DocumentManager.createDocument method."))
            else -> {
                val algorithm = Algorithm(alg).getCryptoAlgorithm()
                val keyUnlockData = AndroidKeystoreKeyUnlockData(cred.alias)
                try {
                    cred.secureArea.sign(
                        cred.alias,
                        algorithm,
                        data,
                        keyUnlockData
                    ).let {
                        SignedWithAuthKeyResult.Success(it.toDer())
                    }
                } catch (e: Exception) {
                    when (e) {
                        is KeyLockedException -> SignedWithAuthKeyResult.UserAuthRequired(
                            keyUnlockData.getCryptoObjectForSigning(algorithm)
                        )

                        else -> SignedWithAuthKeyResult.Failure(e)
                    }
                }

            }

        }
    }

    override fun toString(): String {
        return "UnsignedDocument(id=$id, docType='$docType', usesStrongBox=$usesStrongBox, requiresUserAuth=$requiresUserAuth, createdAt=$createdAt, state=$state, certificatesNeedAuth=$certificatesNeedAuth, name='$name')"
    }

    internal companion object {
        @JvmSynthetic
        operator fun invoke(baseDocument: BaseDocument) = UnsignedDocument(
            id = baseDocument.name,
            name = baseDocument.documentName,
            docType = baseDocument.docType,
            usesStrongBox = baseDocument.usesStrongBox,
            requiresUserAuth = baseDocument.requiresUserAuth,
            createdAt = baseDocument.createdAt,
            certificatesNeedAuth = baseDocument.pendingCredentials
                .filterIsInstance<SecureAreaBoundCredential>()
                .firstOrNull()
                ?.attestation
                ?.certChain
                ?.javaX509Certificates
                ?: emptyList(),
        ).also { it.base = baseDocument }
    }
}