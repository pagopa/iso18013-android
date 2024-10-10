package it.pagopa.cbor_implementation.cose

import androidx.biometric.BiometricPrompt.CryptoObject

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
     * User authentication is required to sign data
     *
     * @property cryptoObject
     */
    data class UserAuthRequired(val cryptoObject: CryptoObject?) : SignWithCOSEResult
    /**
     * Failure while signing the data. Contains the throwable that caused the failure
     *
     * @property msg
     */
    data class Failure(val msg: String) : SignWithCOSEResult
}