package it.pagopa.cbor_implementation.document_manager.results

import androidx.biometric.BiometricPrompt


sealed interface SignDataWithCOSEResult {
    data class Success(val cborBytes: ByteArray) : SignDataWithCOSEResult
    data class Failure(val msg: String) : SignDataWithCOSEResult
    data class UserAuthRequired(
        val cryptoObject: BiometricPrompt.CryptoObject?
    ) : SignDataWithCOSEResult
}