package it.pagopa.proximity.response

import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialFactory
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.document.DocumentStore
import com.android.identity.document.NameSpacedData
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Constants
import it.pagopa.proximity.DocType
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.DisclosedDocument
import it.pagopa.proximity.document.ReaderAuth
import it.pagopa.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.proximity.request.RequiredFieldsEuPid
import it.pagopa.proximity.request.RequiredFieldsMdl
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.files.Path
import java.io.File

class ResponseGenerator(
    private val context: Context,
    private val sessionsTranscript: ByteArray
) {
    private var readerTrustStore: ReaderTrustStore? = null

    private sealed interface AddDocumentToResponse {
        object Success : AddDocumentToResponse
        data class UserAuthRequired(
            val keyUnlockData: AndroidKeystoreKeyUnlockData
        ) : AddDocumentToResponse
    }

    private fun setReaderAuthResultToDocRequest(documentRequest: DeviceRequestParser.DocRequest): ReaderAuth? {
        val trustStore = readerTrustStore ?: return null
        val readerAuth = documentRequest.readerAuth ?: return null
        val readerCertificateChain = documentRequest.readerCertificateChain ?: return null
        if (documentRequest.readerCertificateChain?.javaX509Certificates?.isEmpty() == true) return null

        val certChain =
            trustStore.createCertificationTrustPath(readerCertificateChain.javaX509Certificates)
                ?.takeIf { it.isNotEmpty() } ?: readerCertificateChain.javaX509Certificates

        val readerCommonName = certChain.firstOrNull()
            ?.subjectX500Principal
            ?.name
            ?.split(",")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == "CN" }
            ?.get(1)
            ?.trim()
            ?: ""
        return ReaderAuth(
            readerAuth,
            documentRequest.readerAuthenticated,
            readerCertificateChain.javaX509Certificates,
            trustStore.validateCertificationTrustPath(readerCertificateChain.javaX509Certificates),
            readerCommonName
        )
    }

    fun createResponse(
        disclosedDocuments: Array<DisclosedDocument>
    ): ByteArray? {
        try {
            val deviceResponse = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            disclosedDocuments.forEach { responseDocument ->
                addDocumentToResponse(deviceResponse, responseDocument, sessionsTranscript)
            }
            return deviceResponse.generate()
        } catch (_: Exception) {
            return null
        }
    }

    internal val storageEngine: StorageEngine by lazy {
        val path = Path(File(context.noBackupFilesDir.path, "pagopa-identity.bin").path)
        AndroidStorageEngine.Builder(context, path)
            .setUseEncryption(true)
            .build()
    }
    internal val androidSecureArea: AndroidKeystoreSecureArea by lazy {
        AndroidKeystoreSecureArea(context, storageEngine)
    }
    private val secureAreaRepository: SecureAreaRepository by lazy {
        SecureAreaRepository().apply {
            addImplementation(androidSecureArea)
        }
    }

    private val credentialFactory: CredentialFactory by lazy {
        CredentialFactory().apply {
            addCredentialImplementation(MdocCredential::class) { document, dataItem ->
                MdocCredential(document, dataItem)
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun addDocumentToResponse(
        responseGenerator: DeviceResponseGenerator,
        disclosedDocument: DisclosedDocument,
        transcript: ByteArray
    ): AddDocumentToResponse {
        if (!DocType.isAcceptedDocType(DocType.fromString(disclosedDocument.docType)))
            throw IllegalStateException("Doc type is not of kind ${DocType.EU_PID.value} || ${DocType.MDL.value}")
        val dataElements = ArrayList<DocumentRequest.DataElement>()
        val reqField = if (disclosedDocument.docType == DocType.EU_PID.value) {
            disclosedDocument.requestedFields as RequiredFieldsEuPid
        } else {
            disclosedDocument.requestedFields as RequiredFieldsMdl
        }
        val nameSpaceName = disclosedDocument.nameSpaces.keys.first()
        val nameSpacedData = disclosedDocument.nameSpaces.values.first()
        val reqFieldsArray = reqField.toArray()
        nameSpacedData.keys.forEach {
            for (i in 0 until reqFieldsArray.size) {
                val (value, cborValue) = reqFieldsArray[i]
                if (it == cborValue) {
                    dataElements.add(
                        DocumentRequest.DataElement(
                            nameSpaceName,
                            it,
                            false,
                            doNotSend = value != true
                        )
                    )
                    break
                }
            }
        }
        val request = DocumentRequest(dataElements)
        val documentStore =
            DocumentStore(storageEngine, secureAreaRepository, credentialFactory)
        val document = documentStore.lookupDocument(disclosedDocument.documentId)
            ?: throw IllegalStateException("Document not found")
        val credential = document.findCredential(Clock.System.now())
            ?: throw IllegalStateException("No credential available")
        val staticAuthData = StaticAuthDataParser(credential.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            request, document.applicationData.getNameSpacedData("nameSpacedData"), staticAuthData
        )
        val keyUnlockData = AndroidKeystoreKeyUnlockData(credential.alias)
        try {
            val generator =
                DocumentGenerator(disclosedDocument.docType, staticAuthData.issuerAuth, transcript)
                    .setIssuerNamespaces(mergedIssuerNamespaces)
            generator.setDeviceNamespacesSignature(
                NameSpacedData.Builder().build(),
                credential.secureArea,
                credential.alias,
                keyUnlockData,
                Algorithm.ES256
            )
            val data = generator.generate()
            responseGenerator.addDocument(data)
        } catch (lockedException: KeyLockedException) {
            ProximityLogger.e(this.javaClass.name, "error: $lockedException")
            return AddDocumentToResponse.UserAuthRequired(keyUnlockData)
        }
        return AddDocumentToResponse.Success
    }

    private fun Document.findCredential(
        now: Instant
    ): MdocCredential? {
        var candidate: MdocCredential? = null
        certifiedCredentials
            .filterIsInstance<MdocCredential>()
            .filter { now >= it.validFrom && now <= it.validUntil }
            .forEach { credential ->
                // If we already have a candidate, prefer this one if its usage count is lower
                candidate?.let { candidateCredential ->
                    if (credential.usageCount < candidateCredential.usageCount) {
                        candidate = credential
                    }
                } ?: run {
                    candidate = credential
                }

            }
        return candidate
    }
}