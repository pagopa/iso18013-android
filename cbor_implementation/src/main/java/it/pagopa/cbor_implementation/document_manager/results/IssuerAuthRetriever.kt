package it.pagopa.cbor_implementation.document_manager.results

data class DocumentIssuerAuth(
    val issuerAuth: ByteArray?,
    val docType: String
)

interface IssuerAuthRetriever {
    fun success(issuerAuthList: List<DocumentIssuerAuth>)
    fun failure(t: Throwable)
}