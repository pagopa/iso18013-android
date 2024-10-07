package it.pagopa.cbor_implementation.document_manager.results

import it.pagopa.cbor_implementation.document_manager.document.SignedWithCOSEDocument

interface SignDataWithCOSEResult {
    fun success(docs: List<SignedWithCOSEDocument>)
    fun failure(e: Throwable)
}