package it.pagopa.cbor_implementation.document_manager.results

import it.pagopa.cbor_implementation.document_manager.DocumentId

interface StoreDocumentResult {
    fun success(documentId: DocumentId, proofOfProvisioning: ByteArray?)
    fun failure(throwable: Throwable)
}