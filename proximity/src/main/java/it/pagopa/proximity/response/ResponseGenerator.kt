package it.pagopa.proximity.response

import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialFactory
import com.android.identity.crypto.Algorithm
import com.android.identity.document.Document
import com.android.identity.document.DocumentRequest
import com.android.identity.document.DocumentStore
import com.android.identity.document.NameSpacedData
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.mso.StaticAuthDataParser
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
    private sealed interface AddDocumentToResponse {
        object Success : AddDocumentToResponse
        data class UserAuthRequired(
            val keyUnlockData: AndroidKeystoreKeyUnlockData
        ) : AddDocumentToResponse
    }

    interface Response {
        /**@param [response] [ByteArray] generated for response*/
        fun onResponseGenerated(response: ByteArray)

        /**@param [message] [String] for error reached*/
        fun onError(message: String)
    }

    /**
     * It creates a mdoc response in ByteArray format respect documents requested and disclosed
     * @return[Response.onResponseGenerated] if ByteArray is created without Exceptions, else
     * [Response.onError] if disclosedDocumentsArray is Empty with "no doc found" message or if an
     * [Exception] was reached with [Throwable.message].
     */
    @JvmName("createResponseWithCallback")
    fun createResponse(
        disclosedDocuments: Array<DisclosedDocument>,
        response: Response
    ) {
        if (disclosedDocuments.isNotEmpty()) {
            val (responseToSend, message) = this.createResponse(
                disclosedDocuments
            )
            responseToSend?.let {
                response.onResponseGenerated(it)
            } ?: run {
                response.onError(message)
                ProximityLogger.e(
                    "Sending resp",
                    "found doc but fail to generate raw response: $message"
                )
            }
        } else {
            ProximityLogger.e("Sending resp", "no doc found")
            response.onError("no doc found")
        }
    }

    /**
     * It creates a mdoc response in ByteArray format respect documents requested and disclosed
     * @return[Pair]-> out <[ByteArray],[String]> with first element nullable,
     * if ByteArray is created without Exceptions message back will be "created" else
     * [Throwable.message] reached or empty string if this is null
     */
    private fun createResponse(
        disclosedDocuments: Array<DisclosedDocument>
    ): Pair<ByteArray?, String> {
        try {
            val deviceResponse = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            disclosedDocuments.forEach { responseDocument ->
                addDocumentToResponse(deviceResponse, responseDocument, sessionsTranscript)
            }
            return deviceResponse.generate() to "created"
        } catch (e: Exception) {
            return null to e.message.orEmpty()
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
                            doNotSend = value
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