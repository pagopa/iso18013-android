package it.pagopa.io.wallet.proximity.document.profile

import android.util.Log
import it.pagopa.io.wallet.proximity.ProximityLogger
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.cert.X509Certificate

internal class AuthorityKey : ProfileValidation {

    override fun validate(
        readerAuthCertificate: X509Certificate,
        trustCA: X509Certificate,
    ): Boolean {
        try {
            val authorityKeyIdentifier: AuthorityKeyIdentifier =
                AuthorityKeyIdentifier.getInstance(
                    DEROctetString.getInstance(
                        readerAuthCertificate.getExtensionValue(Extension.authorityKeyIdentifier.id),
                    ).octets,
                )

            val subjectKeyIdentifier: SubjectKeyIdentifier =
                SubjectKeyIdentifier.getInstance(
                    DEROctetString.getInstance(
                        trustCA.getExtensionValue(Extension.subjectKeyIdentifier.id),
                    ).octets,
                )

            return authorityKeyIdentifier.keyIdentifier
                .contentEquals(subjectKeyIdentifier.keyIdentifier)
                .also {
                    ProximityLogger.d(this.javaClass.name, "AuthorityKeyIdentifier: $it")
                }
        } catch (e: Throwable) {
            Log.e(this.javaClass.name, "Error", e)
            return false
        }
    }
}