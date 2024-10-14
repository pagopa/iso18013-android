package it.pagopa.cbor_implementation.document_manager

import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm

open class LibIso18013DAOException : Exception() {
    override fun toString(): String {
        return this.message.orEmpty()
    }
}

class DocumentDecodingException : LibIso18013DAOException() {
    override val message = "Document decoding failed"
}

class InvalidDeviceKeyException : LibIso18013DAOException() {
    override val message = "Invalid device key"
}

class StrongBoxNotSupported : LibIso18013DAOException() {
    override val message = "Strong box not supported by this device"
}

class StrongBoxNotSupportedAlgorithm(algorithm: Algorithm) : LibIso18013DAOException() {
    override val message = "algorithm ${algorithm.toEcCurve().name} not supported by Strong box"
}

class DocumentWithIdentifierNotFound : LibIso18013DAOException() {
    override val message = "No stored document with this identifier"
}

class DocumentAlreadyStoredException : LibIso18013DAOException() {
    override val message: String = "Document state must be unsigned"
}