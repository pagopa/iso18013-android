package it.pagopa.io.wallet.proximity.document.profile

import it.pagopa.io.wallet.proximity.ProximityLogger
import java.security.cert.X509Certificate

private const val READER_AUTH_OID = "1.0.18013.5.1.6"

internal class KeyExtended : ProfileValidation {

    override fun validate(
        readerAuthCertificate: X509Certificate,
        trustCA: X509Certificate,
    ): Boolean {
        return readerAuthCertificate.extendedKeyUsage?.contains(READER_AUTH_OID).also {
            ProximityLogger.d(this.javaClass.name, "KeyExtendedKeyUsage: $it")
        } == true
    }
}