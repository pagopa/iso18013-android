package it.pagopa.cbor_implementation.document_manager.results

import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument

sealed interface CreateDocumentResult {
    data class Success(val unsignedDocument: UnsignedDocument): CreateDocumentResult
    data class Failure(val throwable: Throwable): CreateDocumentResult
}