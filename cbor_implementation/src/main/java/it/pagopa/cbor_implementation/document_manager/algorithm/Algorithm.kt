package it.pagopa.cbor_implementation.document_manager.algorithm

import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.Algorithm as BaseAlgorithm

class Algorithm(private val algorithm: SupportedAlgorithms) {
    @OptIn(NotSupportedAlgorithm::class)
    fun getCryptoAlgorithm() = when (this.algorithm) {
        SupportedAlgorithms.SHA_384_WITH_EC_DSA -> BaseAlgorithm.ES384
        SupportedAlgorithms.SHA_512_WITH_EC_DSA -> BaseAlgorithm.ES512
        else -> BaseAlgorithm.ES256
    }

    /**
     * Supported algorithms
     * @property SHA_512_WITH_EC_DSA
     * @property SHA_384_WITH_EC_DSA
     * @property SHA256_WITH_ECD_SA
     **/
    enum class SupportedAlgorithms {
        @NotSupportedAlgorithm
        SHA_512_WITH_EC_DSA,

        @NotSupportedAlgorithm
        SHA_384_WITH_EC_DSA,
        SHA256_WITH_ECD_SA;

        fun toAlgorithm() = Algorithm(this)
    }

    @OptIn(NotSupportedAlgorithm::class)
    fun toEcCurve(): EcCurve {
        return when (this.algorithm) {
            SupportedAlgorithms.SHA256_WITH_ECD_SA -> EcCurve.P256
            SupportedAlgorithms.SHA_384_WITH_EC_DSA -> EcCurve.P384
            SupportedAlgorithms.SHA_512_WITH_EC_DSA -> EcCurve.P521
        }
    }
}
