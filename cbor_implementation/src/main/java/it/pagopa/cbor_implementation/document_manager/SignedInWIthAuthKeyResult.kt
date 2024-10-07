package it.pagopa.cbor_implementation.document_manager

import androidx.biometric.BiometricPrompt

/**
 * The result of [it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument.signWithAuthKey] method
 */
sealed interface SignedWithAuthKeyResult {
    /**
     * Success result containing the signature of data
     *
     * @property signature
     */
    data class Success(val signature: ByteArray) : SignedWithAuthKeyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            return signature.contentHashCode()
        }
    }

    /**
     * User authentication is required to sign data
     *
     * @property cryptoObject
     */
    data class UserAuthRequired(val cryptoObject: BiometricPrompt.CryptoObject?) : SignedWithAuthKeyResult

    /**
     * Failure while signing the data. Contains the throwable that caused the failure
     *
     * @property throwable
     */
    data class Failure(val throwable: Throwable) : SignedWithAuthKeyResult

    /**
     * Execute block if the result is successful
     *
     * @param block
     * @return [SignedWithAuthKeyResult]
     */
    fun onSuccess(block: (ByteArray) -> Unit): SignedWithAuthKeyResult = apply {
        if (this is Success) block(signature)
    }

    /**
     * Execute block if the result is a failure
     *
     * @param block
     * @return [SignedWithAuthKeyResult]
     */
    fun onFailure(block: (Throwable) -> Unit): SignedWithAuthKeyResult = apply {
        if (this is Failure) block(throwable)
    }

    /**
     * Execute block if the result requires user authentication
     *
     * @param block
     * @return [SignedWithAuthKeyResult]
     */
    fun onUserAuthRequired(block: (BiometricPrompt.CryptoObject?) -> Unit): SignedWithAuthKeyResult = apply {
        if (this is UserAuthRequired) block(cryptoObject)
    }
}