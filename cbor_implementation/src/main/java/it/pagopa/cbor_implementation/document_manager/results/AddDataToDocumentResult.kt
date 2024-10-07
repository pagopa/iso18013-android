package it.pagopa.cbor_implementation.document_manager.results

interface AddDataToDocumentResult {
    fun success()
    fun failure(t: Throwable)
}