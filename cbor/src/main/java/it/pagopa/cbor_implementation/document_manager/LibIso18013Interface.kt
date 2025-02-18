package it.pagopa.cbor_implementation.document_manager

import it.pagopa.cbor_implementation.model.Document

typealias DocumentId = String

interface LibIso18013Interface {
    fun getDocumentByIdentifier(id: DocumentId): Document
    fun createDocument(id: DocumentId, data: ByteArray)
    fun gelAllDocuments(): List<Document>
    fun gelAllMdlDocuments(): List<Document>
    fun gelAllEuPidDocuments(): List<Document>
    fun deleteDocument(id: DocumentId): Boolean
    fun removeAllDocuments()
}