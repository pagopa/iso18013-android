package it.pagopa.cbor_implementation.document_manager

import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm

open class CustomException : Exception() {
    override fun toString(): String {
        return this.message.orEmpty()
    }
}

class DocumentDecodingException : CustomException() {
    override val message = "Document decoding failed"
}

class InvalidDeviceKeyException : CustomException() {
    override val message = "Invalid device key"
}

class StrongBoxNotSupported : CustomException() {
    override val message = "Strong box not supported by this device"
}

class StrongBoxNotSupportedAlgorithm(algorithm: Algorithm) : CustomException() {
    override val message = "algorithm ${algorithm.toEcCurve().name} not supported by Strong box"
}

class DocumentWithIdentifierNotFound : CustomException() {
    override val message = "No stored document with this identifier"
}

class DocumentAlreadyStoredException : CustomException() {
    override val message: String = "Document state must be unsigned"
}