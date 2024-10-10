package it.pagopa.cbor_implementation.document_manager

import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.CreateDocumentResult
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult

interface LibIso18013DAO {
    fun getAllDocuments(): Array<Document>
    fun getAllMdlDocuments(): Array<Document>
    fun getAllEuPidDocuments(): Array<Document>
    fun getDocumentByIdentifier(id: String): Document?
    fun deleteDocument(id: String): Boolean
    fun createDocument(
        docType: String,
        documentName: String,
        strongBox: Boolean,
        attestationChallenge: ByteArray?
    ): CreateDocumentResult

    fun storeDocument(
        unsignedDocument: UnsignedDocument,
        issuerDocumentData: ByteArray,
        result: StoreDocumentResult
    )
}