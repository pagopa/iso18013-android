package it.pagopa.cbor_implementation.document_manager

import COSE.Message
import COSE.MessageTag
import COSE.Sign1Message
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.CreateDocumentResult
import it.pagopa.cbor_implementation.document_manager.results.DocumentIssuerAuth
import it.pagopa.cbor_implementation.document_manager.results.DocumentRetrieved
import it.pagopa.cbor_implementation.document_manager.results.IssuerAuthRetriever
import it.pagopa.cbor_implementation.document_manager.results.IssuerSignedRetriever
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult
import it.pagopa.cbor_implementation.extensions.asNameSpacedData
import it.pagopa.cbor_implementation.extensions.getEmbeddedCBORObject
import it.pagopa.cbor_implementation.extensions.toDigestIdMapping
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import java.time.Instant
import java.util.UUID

class DocumentManager private constructor() {
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
    private val supportStrongBox by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
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

    fun createDocument(
        docType: String,
        documentName: String,
        strongBox: Boolean,
        attestationChallenge: ByteArray?
    ): CreateDocumentResult {
        try {
            val domain = "pagopa"
            val documentId = "${UUID.randomUUID()}"
            val useStrongBox = strongBox && supportStrongBox
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

    fun storeDeferredDocument(
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

    fun retrieveIssuerDocumentData(
        documentData: ByteArray,
        result: IssuerSignedRetriever
    ) {
        try {
            var listBack: List<DocumentRetrieved> = listOf()
            val maybeList = CBORObject
                .DecodeFromBytes(documentData)["documents"]
            val isList = maybeList != null
            if (isList) {
                val docListSize = maybeList.size()
                for (i in 0 until docListSize) {
                    val issuerDocumentData = maybeList[i]["issuerSigned"].EncodeToBytes()
                    val docType = maybeList[i]["docType"].EncodeToBytes()
                    if (issuerDocumentData != null && docType != null) {
                        listBack += DocumentRetrieved(
                            issuerDocumentsData = maybeList[i]["issuerSigned"].EncodeToBytes(),
                            docType = maybeList[i]["docType"].AsString(),
                            nameSpaces = maybeList[i]["issuerSigned"]["nameSpaces"]?.EncodeToBytes()
                        )
                    }
                }
            } else {
                val obj = CBORObject
                    .DecodeFromBytes(documentData)
                val issuerDocumentData = obj["issuerSigned"].EncodeToBytes()
                val docType = obj["docType"].EncodeToBytes()
                if (issuerDocumentData != null && docType != null) {
                    listBack += DocumentRetrieved(
                        issuerDocumentsData = obj["issuerSigned"].EncodeToBytes(),
                        docType = obj["docType"].AsString(),
                        nameSpaces = obj["issuerSigned"]["nameSpaces"]?.EncodeToBytes()
                    )
                }
            }
            if (listBack.isEmpty()) {
                result.failure(IllegalArgumentException("No valid document found"))
                return
            }
            result.success(listBack)
        } catch (e: Exception) {
            CborLogger.e("retrieveIssuerDocumentData", e.toString())
            result.failure(e)
        }
    }

    fun retrieveIssuerAuth(
        documentData: ByteArray,
        retriever: IssuerAuthRetriever
    ) {
        try {
            retrieveIssuerDocumentData(documentData, object : IssuerSignedRetriever {
                override fun success(issuerDocumentsData: List<DocumentRetrieved>) {
                    retriever.success(issuerDocumentsData.map { docData ->
                        val cborObj = CBORObject
                            .DecodeFromBytes(docData.issuerDocumentsData)
                        val issuerAuth = cborObj["issuerAuth"]
                        DocumentIssuerAuth(issuerAuth?.EncodeToBytes(), docData.docType)
                    })
                }

                override fun failure(throwable: Throwable) {
                    retriever.failure(throwable)
                }
            })
        } catch (e: Exception) {
            CborLogger.e("retrieveIssuerAuth", e.toString())
            retriever.failure(e)
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

    fun storeIssuedDocument(
        unsignedDocument: UnsignedDocument,
        issuerDocumentData: ByteArray,
        result: StoreDocumentResult
    ) {
        try {//TODO: eccezione custom e fare il throw
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

    companion object {
        fun build(builder: DocumentManagerBuilder) = DocumentManager().apply {
            this.context = builder.context
            this.builder = builder
        }
    }
}