package it.pagopa.cbor_implementation.document_manager

import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.javaX509Certificates
import it.pagopa.cbor_implementation.document_manager.Document.State
import java.security.cert.X509Certificate
import java.time.Instant
import com.android.identity.document.Document as BaseDocument

/**
 * A [DeferredDocument] is as [UnsignedDocument] with extra [relatedData] that can be used later on
 * by the issuing process. To store the [DeferredDocument] and its related data, use the
 * [DocumentManager.storeDeferredDocument]
 *
 * @property relatedData the related data
 */
class DeferredDocument(
    id: DocumentId,
    name: String,
    docType: String,
    usesStrongBox: Boolean,
    requiresUserAuth: Boolean,
    createdAt: Instant,
    certificatesNeedAuth: List<X509Certificate>,
    val relatedData: ByteArray
) : Document, UnsignedDocument(
    id,
    name,
    docType,
    usesStrongBox,
    requiresUserAuth,
    createdAt,
    certificatesNeedAuth,
) {

    override val state: State
        get() = base?.state ?: State.DEFERRED

    override fun toString(): String {
        return "DeferredDocument(id='$id', docType='$docType', name='$name', usesStrongBox=$usesStrongBox, requiresUserAuth=$requiresUserAuth, createdAt=$createdAt, state=$state, relatedData=${relatedData.contentToString()})"
    }

    internal companion object {
        @JvmSynthetic
        operator fun invoke(baseDocument: BaseDocument) = DeferredDocument(
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
            relatedData = baseDocument.deferredRelatedData,
        ).apply {
            this.base = baseDocument
        }
    }
}