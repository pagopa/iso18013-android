package it.pagopa.proximity.document.profile

import it.pagopa.proximity.ProximityLogger
import java.security.cert.X509Certificate

internal class CommonName : ProfileValidation {

    override fun validate(
        readerAuthCertificate: X509Certificate,
        trustCA: X509Certificate,
    ): Boolean {
        return readerAuthCertificate.subjectX500Principal.name.contains("CN=").also {
            ProximityLogger.d(this.javaClass.name, "CommonName: $it")
        }
    }
}