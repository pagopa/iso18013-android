package it.pagopa.cbor_implementation.document_manager

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.cose.CreateCOSEDoc
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.CreateDocumentResult
import it.pagopa.cbor_implementation.document_manager.results.DocumentRetrieved

internal class AnalyzeOneDoc private constructor(
    val document: UnsignedDocument?,
    val itsOk: Boolean,
    val msg: String,
    private val issuerAuth: CBORObject? = null,
    private val nameSpaces: CBORObject? = null
) {
    fun generateData(): ByteArray = mapOf(
        "nameSpaces" to nameSpaces!!,
        "issuerAuth" to issuerAuth!!,
    ).let { CBORObject.FromObject(it).EncodeToBytes() }

    companion object {
        private const val DIGEST_ALG = "SHA-256"
        fun analyzeWithCborObject(
            documentCbor: CBORObject,
            documentManager: DocumentManager,
            useStrongBox: Boolean,
            attestationChallenge: ByteArray?,
            privateKey: String,
            issuerCertificate: String
        ): AnalyzeOneDoc {
            try {
                val docType = documentCbor["docType"].AsString()
                if (docType == null)
                    return AnalyzeOneDoc(null, false, "No field docType found")
                return when (val result = documentManager.createDocument(
                    docType = docType,
                    strongBox = useStrongBox,
                    attestationChallenge = attestationChallenge
                )) {
                    is CreateDocumentResult.Success -> {
                        val issuerSigned = documentCbor["issuerSigned"]
                        val nameSpaces = issuerSigned["nameSpaces"]
                        result.unsignedDocument.name = docType
                        val authKey = result.unsignedDocument.ecPublicKey
                        if (authKey == null)
                            return AnalyzeOneDoc(
                                result.unsignedDocument,
                                false,
                                "Auth Key is null for doc with id: ${result.unsignedDocument.id}"
                            )
                        val issuerAuth = CreateCOSEDoc
                            .withPrivateKey(privateKey)
                            .withIssuerCertificate(issuerCertificate)
                            .generateMso(DIGEST_ALG, docType, authKey, nameSpaces)
                            .signMso()
                        AnalyzeOneDoc(
                            result.unsignedDocument,
                            true,
                            "",
                            issuerAuth,
                            nameSpaces
                        )
                    }

                    is CreateDocumentResult.Failure -> AnalyzeOneDoc(
                        null,
                        false,
                        "Error while creating document: ${result.throwable}"
                    )
                }
            } catch (e: Exception) {
                return AnalyzeOneDoc(null, false, e.toString())
            }
        }

        fun analyzeWithDocumentRetrieved(
            documentRetrieved: DocumentRetrieved,
            documentManager: DocumentManager,
            useStrongBox: Boolean,
            attestationChallenge: ByteArray?,
            privateKey: String,
            issuerCertificate: String
        ): AnalyzeOneDoc {
            try {
                return when (val result = documentManager.createDocument(
                    docType = documentRetrieved.docType,
                    strongBox = useStrongBox,
                    attestationChallenge = attestationChallenge
                )) {
                    is CreateDocumentResult.Success -> {
                        val nameSpaces=CBORObject.DecodeFromBytes(documentRetrieved.nameSpaces)
                        result.unsignedDocument.name = documentRetrieved.docType
                        val authKey = result.unsignedDocument.ecPublicKey
                        if (authKey == null)
                            return AnalyzeOneDoc(
                                result.unsignedDocument,
                                false,
                                "Auth Key is null for doc with id: ${result.unsignedDocument.id}"
                            )
                        val issuerAuth = CreateCOSEDoc
                            .withPrivateKey(privateKey)
                            .withIssuerCertificate(issuerCertificate)
                            .generateMso(
                                DIGEST_ALG,
                                documentRetrieved.docType,
                                authKey,
                                nameSpaces
                            )
                            .signMso()
                        AnalyzeOneDoc(
                            result.unsignedDocument,
                            true,
                            "",
                            issuerAuth,
                            nameSpaces
                        )
                    }

                    is CreateDocumentResult.Failure -> AnalyzeOneDoc(
                        null,
                        false,
                        "Error while creating document: ${result.throwable}"
                    )
                }
            } catch (e: Exception) {
                return AnalyzeOneDoc(null, false, e.toString())
            }
        }
    }
}
