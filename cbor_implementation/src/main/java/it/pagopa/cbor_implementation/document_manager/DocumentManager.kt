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
import com.android.identity.util.Logger
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
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
        Logger.isDebugEnabled = CborLogger.enabled
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
        curve: EcCurve
    ) = AndroidKeystoreCreateKeySettings.Builder(challenge)
        .setEcCurve(curve)
        .setUseStrongBox(useStrongBox)
        .setUserAuthenticationRequired(
            builder.userAuth, builder.userAuthTimeoutInMillis,
            DocumentManagerBuilder.AUTH_TYPE
        )
        .setKeyPurposes(setOf(KeyPurpose.SIGN))
        .build()

    @Throws(
        StrongBoxNotSupported::class,
        StrongBoxNotSupportedAlgorithm::class
    )
    override fun createDocument(
        docType: String,
        documentName: String,
        forceStrongBox: Boolean,
        algorithm: Algorithm.SupportedAlgorithms,
        attestationChallenge: ByteArray?
    ): UnsignedDocument {
        val domain = this.context.applicationContext.packageName.replace(".", "_")
        val documentId = "${UUID.randomUUID()}"
        if (forceStrongBox && !context.supportStrongBox)
            throw StrongBoxNotSupported()
        if (forceStrongBox && algorithm != Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA)
            throw StrongBoxNotSupportedAlgorithm(algorithm.toAlgorithm())
        val useStrongBox =
            context.supportStrongBox && algorithm == Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA
        val nonEmptyChallenge = attestationChallenge
            ?.takeUnless { it.isEmpty() }
            ?: generateRandomBytes()
        val keySettings = createKeySettings(
            nonEmptyChallenge,
            useStrongBox,
            algorithm.toAlgorithm().toEcCurve()
        )
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
        return UnsignedDocument(documentCredential)
    }

    override fun getAllDocuments(state: Document.State?): Array<Document> {
        val list = documentStore.listDocuments()
        if (list.isEmpty()) return arrayOf()
        val listBack = ArrayList<Document>()
        list.forEach { docId ->
            documentStore.lookupDocument(docId)?.let {
                listBack.add(Document(it))
            }
        }
        val filteredList = if (state != null) {
            listBack.filter { it.state == state }
        } else
            listBack
        return filteredList.toTypedArray()
    }

    override fun getAllMdlDocuments(state: Document.State?) = getAllDocuments(state).filter {
        it.docType == MDL_DOCTYPE
    }.toTypedArray()

    override fun getAllEuPidDocuments(state: Document.State?) = getAllDocuments(state).filter {
        it.docType == EU_PID_DOCTYPE
    }.toTypedArray()

    @Throws(DocumentWithIdentifierNotFound::class)
    override fun getDocumentByIdentifier(id: DocumentId): Document {
        val doc = documentStore.lookupDocument(id)
        if (doc == null) throw DocumentWithIdentifierNotFound()
        return Document(doc)
    }

    @Throws(DocumentWithIdentifierNotFound::class)
    override fun deleteDocument(id: DocumentId): Boolean {
        if (documentStore.lookupDocument(id) == null) throw DocumentWithIdentifierNotFound()
        documentStore.deleteDocument(id)
        return documentStore.lookupDocument(id) == null
    }

    @Throws(
        DocumentDecodingException::class,
        InvalidDeviceKeyException::class,
        DocumentWithIdentifierNotFound::class,
        DocumentAlreadyStoredException::class
    )
    override fun storeDocument(
        id: DocumentId,
        issuerDocumentData: ByteArray
    ): DocumentId {
        val documentCredential = documentStore.lookupDocument(id)
        if (documentCredential == null) throw DocumentWithIdentifierNotFound()
        val document = Document(documentCredential)
        if (document.state != Document.State.UNSIGNED)
            throw DocumentAlreadyStoredException()
        val unsignedDocument = UnsignedDocument(documentCredential)
        val issuerSigned = CBORObject.DecodeFromBytes(issuerDocumentData)
        val issuerAuth = issuerSigned["issuerAuth"]
        if (issuerAuth == null) throw DocumentDecodingException()
        with(documentCredential) {
            val issuerAuthBytes = issuerAuth.EncodeToBytes()
            val issuerAuth: Sign1Message = try {
                Message.DecodeFromBytes(issuerAuthBytes, MessageTag.Sign1) as Sign1Message
            } catch (_: Exception) {
                throw DocumentDecodingException()
            }
            val msoBytes = issuerAuth.GetContent().getEmbeddedCBORObject().EncodeToBytes()
            val mso = MobileSecurityObjectParser(msoBytes).parse()
            if (mso.deviceKey != unsignedDocument.publicKey.toEcPublicKey(mso.deviceKey.curve)) {
                if (builder.checkPublicKeyBeforeAdding)
                    throw InvalidDeviceKeyException()
            }
            this@with.state = Document.State.ISSUED
            this@with.docType = mso.docType
            this@with.issuedAt = Instant.now()
            this@with.clearDeferredRelatedData()
            val nameSpaces = issuerSigned["nameSpaces"]
            val digestIdMapping = nameSpaces.toDigestIdMapping()
            val staticAuthData = StaticAuthDataGenerator(digestIdMapping, issuerAuthBytes)
                .generate()
            this@with.pendingCredentials.forEach { credential ->
                credential.certify(staticAuthData, mso.validFrom, mso.validUntil)
            }
            this@with.nameSpacedData = nameSpaces.asNameSpacedData()
        }
        return documentCredential.name
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

        fun buildWithContext(context: Context) = DocumentManager().apply {
            this.context = context
            this.builder = DocumentManagerBuilder(context)
        }
    }
}