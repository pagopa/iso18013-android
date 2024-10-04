package it.pagopa.cbor_implementation.document_manager.results

data class DocumentRetrieved(
    val issuerDocumentsData: ByteArray,
    val docType: String
)

interface IssuerSignedRetriever {
    fun success(issuerDocumentsData: List<DocumentRetrieved>)
    fun failure(throwable: Throwable)
}