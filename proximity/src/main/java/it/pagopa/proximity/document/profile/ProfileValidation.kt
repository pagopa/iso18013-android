package it.pagopa.proximity.document.profile

import java.security.cert.X509Certificate

internal fun interface ProfileValidation {

    companion object {
        @JvmStatic
        val DEFAULT: ProfileValidation = ProfileValidationImpl(
            listOf(
                AuthorityKey(),
                CommonName(),
                CriticalExtensions(),
                KeyExtended(),
                KeyUsage(),
                MandatoryExtensions(),
                Period(),
                SignatureAlgorithm(),
                SubjectKey(),
            ),
        )
    }
    fun validate(readerAuthCertificate: X509Certificate, trustCA: X509Certificate): Boolean
}