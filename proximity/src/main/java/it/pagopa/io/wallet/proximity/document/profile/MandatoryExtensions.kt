package it.pagopa.io.wallet.proximity.document.profile

import it.pagopa.io.wallet.proximity.ProximityLogger
import org.bouncycastle.asn1.x509.Extension
import java.security.cert.X509Certificate

internal class MandatoryExtensions : ProfileValidation {

    override fun validate(
        readerAuthCertificate: X509Certificate,
        trustCA: X509Certificate,
    ): Boolean {
        val result =
            readerAuthCertificate.getExtensionValue(Extension.authorityKeyIdentifier.id) != null &&
                    readerAuthCertificate.getExtensionValue(Extension.subjectKeyIdentifier.id) != null &&
                    readerAuthCertificate.keyUsage != null && readerAuthCertificate.keyUsage.isNotEmpty() &&
                    readerAuthCertificate.extendedKeyUsage != null && readerAuthCertificate.extendedKeyUsage.isNotEmpty() &&
                    readerAuthCertificate.getExtensionValue(Extension.cRLDistributionPoints.id) != null
        ProximityLogger.d(this.javaClass.name, "MandatoryExtensions: $result")
        return result
    }
}