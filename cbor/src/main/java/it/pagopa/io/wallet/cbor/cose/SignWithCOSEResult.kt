package it.pagopa.io.wallet.cbor.cose

/**
 * The result of [CreateCOSE.sign] method
 */
sealed interface SignWithCOSEResult {
    /**
     * Success result containing the signature
     *
     * @property signature
     */
    data class Success(val signature: ByteArray, val publicKey: ByteArray) : SignWithCOSEResult

    /**
     * Failure while signing the data. Contains the throwable that caused the failure
     *
     * @property msg
     */
    data class Failure(val msg: String) : SignWithCOSEResult
}