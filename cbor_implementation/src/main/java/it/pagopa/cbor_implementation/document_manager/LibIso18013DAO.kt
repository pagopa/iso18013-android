package it.pagopa.cbor_implementation.document_manager

import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import kotlin.jvm.Throws

interface LibIso18013DAO {
    fun getAllDocuments(state: Document.State?): Array<Document>
    fun getAllMdlDocuments(state: Document.State?): Array<Document>
    fun getAllEuPidDocuments(state: Document.State?): Array<Document>

    @Throws(DocumentWithIdentifierNotFound::class)
    fun getDocumentByIdentifier(id: DocumentId): Document?

    @Throws(DocumentWithIdentifierNotFound::class)
    fun deleteDocument(id: DocumentId): Boolean

    @Throws(StrongBoxNotSupported::class, StrongBoxNotSupportedAlgorithm::class)
    fun createDocument(
        docType: String,
        documentName: String,
        forceStrongBox: Boolean,
        algorithm: Algorithm.SupportedAlgorithms,
        attestationChallenge: ByteArray?
    ): UnsignedDocument

    @Throws(
        DocumentDecodingException::class,
        InvalidDeviceKeyException::class,
        DocumentWithIdentifierNotFound::class,
        DocumentAlreadyStoredException::class
    )
    fun storeDocument(
        id: DocumentId,
        issuerDocumentData: ByteArray
    ): DocumentId
}