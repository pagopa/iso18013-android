package it.pagopa.cbor_implementation.document_manager.document

data class SignedWithCOSEDocument(
    val data: ByteArray,
    val unsignedDoc: UnsignedDocument
)
