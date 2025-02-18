package it.pagopa.cbor_implementation.exception

class DocTypeNotValid(docType: String?) : Exception("DocType not valid: $docType")