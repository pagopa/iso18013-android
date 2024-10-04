package it.pagopa.cbor_implementation.document_manager.results

interface IssuerSignedRetriever {
    fun success(issuerDocumentsData: List<ByteArray>)
    fun failure(throwable: Throwable)
}