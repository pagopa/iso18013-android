package it.pagopa.cbor_implementation.document_manager

import COSE.Message
import COSE.MessageTag
import COSE.Sign1Message
import android.content.Context
import androidx.annotation.CheckResult
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.credential.CredentialFactory
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.toEcPublicKey
import com.android.identity.document.DocumentStore
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.MobileSecurityObjectParser
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureAreaRepository
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.CreateDocumentResult
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult
import it.pagopa.cbor_implementation.extensions.asNameSpacedData
import it.pagopa.cbor_implementation.extensions.getEmbeddedCBORObject
import it.pagopa.cbor_implementation.extensions.supportStrongBox
import it.pagopa.cbor_implementation.extensions.toDigestIdMapping
import it.pagopa.cbor_implementation.helper.addBcIfNeeded
import it.pagopa.cbor_implementation.model.EU_PID_DOCTYPE
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

class DocumentManager private constructor() : LibIso18013DAO {
    init {
        addBcIfNeeded()
    }

    private lateinit var context: Context
    private lateinit var builder: DocumentManagerBuilder
    private val secureAreaRepository: SecureAreaRepository by lazy {
        SecureAreaRepository().apply {
            addImplementation(builder.androidSecureArea)
        }
    }
    private val credentialFactory: CredentialFactory by lazy {
        CredentialFactory().apply {
            addCredentialImplementation(MdocCredential::class) { document, dataItem ->
                MdocCredential(document, dataItem)
            }
        }
    }
    private val documentStore: DocumentStore by lazy {
        DocumentStore(builder.storageEngine, secureAreaRepository, credentialFactory)
    }

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
            builder.userAuth, builder.userAuthTimeoutInMillis,
            DocumentManagerBuilder.AUTH_TYPE
        )
        .setKeyPurposes(setOf(KeyPurpose.SIGN))
        .build()


    override fun createDocument(
        docType: String,
        documentName: String,
        strongBox: Boolean,
        attestationChallenge: ByteArray?
    ): CreateDocumentResult {
        try {
            val domain = "pagopa"
            val documentId = "${UUID.randomUUID()}"
            val useStrongBox = strongBox && context.supportStrongBox
            val nonEmptyChallenge = attestationChallenge
                ?.takeUnless { it.isEmpty() }
                ?: generateRandomBytes()
            val keySettings = createKeySettings(nonEmptyChallenge, useStrongBox)
            val documentCredential = documentStore.createDocument(documentId).apply {
                this.state = Document.State.UNSIGNED
                this.docType = docType
                this.documentName = documentName
                this.createdAt = Instant.now()
                this.attestationChallenge = nonEmptyChallenge
            }
            MdocCredential(
                document = documentCredential,
                asReplacementFor = null,
                domain = domain,
                secureArea = builder.androidSecureArea,
                createKeySettings = keySettings,
                docType = docType
            )
            documentCredential.pendingCredentials
                .filterIsInstance<SecureAreaBoundCredential>()
                .firstOrNull()
                ?.attestation
                ?.publicKey
            documentStore.addDocument(documentCredential)

            val unsignedDocument = UnsignedDocument(documentCredential)
            return CreateDocumentResult.Success(unsignedDocument)
        } catch (e: Exception) {
            return CreateDocumentResult.Failure(e)
        }
    }

    override fun getAllDocuments(): Array<Document> {
        val list = documentStore.listDocuments()
        if (list.isEmpty()) return arrayOf()
        val listBack = ArrayList<Document>()
        list.forEach { docId ->
            documentStore.lookupDocument(docId)?.let {
                listBack.add(Document(it))
            }
        }
        return listBack.toTypedArray()
    }

    override fun getAllMdlDocuments() = getAllDocuments().filter {
        it.docType == MDL_DOCTYPE
    }.toTypedArray()

    override fun getAllEuPidDocuments() = getAllDocuments().filter {
        it.docType == EU_PID_DOCTYPE
    }.toTypedArray()

    override fun getDocumentByIdentifier(id: String): Document? {
        return documentStore.lookupDocument(id)?.let { Document(it) }
    }

    override fun deleteDocument(id: String): Boolean {
        documentStore.deleteDocument(id)
        return documentStore.lookupDocument(id) == null
    }

    override fun storeDocument(
        unsignedDocument: UnsignedDocument,
        issuerDocumentData: ByteArray,
        result: StoreDocumentResult
    ) {
        try {
            val documentCredential = documentStore.lookupDocument(unsignedDocument.id)
            if (documentCredential == null) {
                result.failure(IllegalArgumentException("No credential found for ${unsignedDocument.id}"))
                return
            }
            val issuerSigned = CBORObject.DecodeFromBytes(issuerDocumentData)
            val issuerAuth = issuerSigned["issuerAuth"]
            if (issuerAuth == null) {
                result.failure(IllegalArgumentException("No issuerAuth found"))
                return
            }
            with(documentCredential) {
                val issuerAuthBytes = issuerAuth.EncodeToBytes()
                val issuerAuth = Message
                    .DecodeFromBytes(issuerAuthBytes, MessageTag.Sign1) as Sign1Message
                val msoBytes = issuerAuth.GetContent().getEmbeddedCBORObject().EncodeToBytes()
                val mso = MobileSecurityObjectParser(msoBytes).parse()
                if (mso.deviceKey != unsignedDocument.publicKey.toEcPublicKey(mso.deviceKey.curve)) {
                    if (builder.checkPublicKeyBeforeAdding) {
                        val msg = "Public key in MSO does not match the one in the request"
                        result.failure(IllegalArgumentException(msg))
                        return
                    }
                }
                this.state = Document.State.ISSUED
                this.docType = mso.docType
                this.issuedAt = Instant.now()
                clearDeferredRelatedData()

                val nameSpaces = issuerSigned["nameSpaces"]
                val digestIdMapping = nameSpaces.toDigestIdMapping()
                val staticAuthData = StaticAuthDataGenerator(digestIdMapping, issuerAuthBytes)
                    .generate()
                this.pendingCredentials.forEach { credential ->
                    credential.certify(staticAuthData, mso.validFrom, mso.validUntil)
                }
                this.nameSpacedData = nameSpaces.asNameSpacedData()
            }
            result.success(documentCredential.name, null)
        } catch (e: Exception) {
            result.failure(e)
        }
    }

    internal fun storeDeferredDocument(
        unsignedDocument: UnsignedDocument,
        relatedData: ByteArray,
        result: StoreDocumentResult
    ) {
        try {
            val documentCredential = documentStore.lookupDocument(unsignedDocument.id)
            if (documentCredential == null) {
                result.failure(IllegalArgumentException("No credential found for ${unsignedDocument.id}"))
                return
            }
            with(documentCredential) {
                state = Document.State.DEFERRED
                deferredRelatedData = relatedData
            }
            result.success(documentCredential.name, byteArrayOf())
        } catch (e: Exception) {
            result.failure(e)
        }
    }

    fun verifyDocumentSignature(
        unsignedDocument: UnsignedDocument,
        issuerAuthBytes: ByteArray
    ): Boolean {
        val issuerAuth = Message
            .DecodeFromBytes(issuerAuthBytes, MessageTag.Sign1) as Sign1Message
        val msoBytes = issuerAuth.GetContent().getEmbeddedCBORObject().EncodeToBytes()
        val mso = MobileSecurityObjectParser(msoBytes).parse()
        return mso.deviceKey == unsignedDocument.publicKey.toEcPublicKey(mso.deviceKey.curve)
    }

    /** if the issuer requires the user to prove possession of the private key corresponding to the certificateNeedAuth
     * then user can use the method below to sign issuer's data and send the signature to the issuer*/
    @CheckResult
    fun signUnsignedDocumentIssuerData(
        unsignedDocument: UnsignedDocument,
        data: ByteArray
    ): SignedWithAuthKeyResult = unsignedDocument.signWithAuthKey(data)

    companion object {
        fun build(builder: DocumentManagerBuilder) = DocumentManager().apply {
            this.context = builder.context
            this.builder = builder
        }
    }
}