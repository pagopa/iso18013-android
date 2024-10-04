package it.pagopa.cbor_implementation.document_manager

import COSE.Message
import COSE.MessageTag
import COSE.Sign1Message
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import it.pagopa.cbor_implementation.document_manager.results.IssuerSignedRetriever
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult
import it.pagopa.cbor_implementation.extensions.asNameSpacedData
import it.pagopa.cbor_implementation.extensions.getEmbeddedCBORObject
import it.pagopa.cbor_implementation.extensions.toDigestIdMapping
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.Instant
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class DocumentManager private constructor() {
    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
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
        strongBox: Boolean,
        attestationChallenge: ByteArray?,
        result: CreateDocumentResult
    ) {
        try {
            val domain = "pagopa"
            val documentId = "${UUID.randomUUID()}"
            val useStrongBox = strongBox && supportStrongBox
            val nonEmptyChallenge = attestationChallenge
                ?.takeUnless { it.isEmpty() }
                ?: generateRandomBytes()
            val keySettings = createKeySettings(nonEmptyChallenge, useStrongBox)
            val documentCredential = documentStore.createDocument(documentId).apply {
                state = Document.State.UNSIGNED
                this.docType = docType
                documentName = docType
                createdAt = Instant.now()
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
            result.success(unsignedDocument)
        } catch (e: Exception) {
            result.failure(e)
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

    /*@OptIn(ExperimentalEncodingApi::class)
    fun verifySignature(
        issuerDocumentData: ByteArray
    ): Boolean {
        val issuerSigned = CBORObject.DecodeFromBytes(issuerDocumentData)
        val certificateBase64 = issuerSigned.get("issuerAuth").get(1).values.first().AsString()
        // Decodifica del certificato e della firma
        val certificateBytes = Base64.decode(certificateBase64)
        val signatureBytes = Base64.decode(signatureBase64)

        // Creazione del certificato X.509
        val certificateFactory = CertificateFactory.getInstance("X.509", "BC")
        val certificate = certificateFactory.generateCertificate(certificateBytes.inputStream())
        // Estrazione della chiave pubblica dal certificato
        val publicKey = certificate.publicKey
        // Verifica della firma
        val signature = Signature.getInstance("SHA256withECDSA", "BC")
        signature.initVerify(publicKey)
        signature.update(dataToVerify)
        return signature.verify(signatureBytes)
    }*/

    fun retrieveIssuerDocumentData(
        documentData: ByteArray,
        result: IssuerSignedRetriever
    ) {
        try {
            var listBack: List<ByteArray> = listOf()
            val maybeList = CBORObject
                .DecodeFromBytes(documentData)["documents"]
            val isList = maybeList != null
            if (isList) {
                val docListSize = maybeList.size()
                for (i in 0 until docListSize) {
                    listBack += maybeList[i]["issuerSigned"].EncodeToBytes()
                }
            } else {
                listBack += CBORObject
                    .DecodeFromBytes(documentData)["issuerSigned"]
                    .EncodeToBytes()
            }
            result.success(listBack)
        } catch (e: Exception) {
            result.failure(e)
        }
    }

    fun storeIssuedDocument(
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

            with(documentCredential) {
                val issuerAuthBytes = issuerSigned["issuerAuth"].EncodeToBytes()
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
                state = Document.State.ISSUED
                docType = mso.docType
                issuedAt = Instant.now()
                clearDeferredRelatedData()

                val nameSpaces = issuerSigned["nameSpaces"]
                val digestIdMapping = nameSpaces.toDigestIdMapping()
                val staticAuthData = StaticAuthDataGenerator(digestIdMapping, issuerAuthBytes)
                    .generate()
                pendingCredentials.forEach { credential ->
                    credential.certify(staticAuthData, mso.validFrom, mso.validUntil)
                }
                nameSpacedData = nameSpaces.asNameSpacedData()
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