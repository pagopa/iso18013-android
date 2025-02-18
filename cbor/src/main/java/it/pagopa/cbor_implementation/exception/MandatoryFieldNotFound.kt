package it.pagopa.cbor_implementation.exception

class MandatoryFieldNotFound(fields: List<String>) : Exception("Mandatory fields not found: ${fields.joinToString(separator = ", ") { it }}")