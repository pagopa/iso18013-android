package it.pagopa.cbor_implementation.document_manager.results

import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument

interface CreateDocumentResult {
    fun success(unsignedDocument: UnsignedDocument)
    fun failure(throwable: Throwable)
}