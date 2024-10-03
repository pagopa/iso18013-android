package it.pagopa.cbor_implementation.document_manager

import androidx.annotation.StringDef
import com.android.identity.crypto.Algorithm as BaseAlgorith

/**
 * Algorithm used to sign the document
 */
@Retention(AnnotationRetention.SOURCE)
@StringDef(value = [Algorithm.SHA256withECDSA, Algorithm.SHA384withECDSA, Algorithm.SHA512withECDSA])
annotation class Algorithm {
    /**
     * Supported algorithms
     * @property SHA512withECDSA
     * @property SHA384withECDSA
     * @property SHA256withECDSA
     */
    companion object {
        const val SHA512withECDSA = "SHA512withECDSA"
        const val SHA384withECDSA = "SHA384withECDSA"
        const val SHA256withECDSA = "SHA256withECDSA"
    }
}

internal val String.algorithm: BaseAlgorith
    get() = when (this) {
        Algorithm.SHA256withECDSA -> BaseAlgorith.ES256
        Algorithm.SHA384withECDSA -> BaseAlgorith.ES384
        Algorithm.SHA512withECDSA -> BaseAlgorith.ES512
        else -> throw IllegalArgumentException("Unknown algorithm: $this")
    }