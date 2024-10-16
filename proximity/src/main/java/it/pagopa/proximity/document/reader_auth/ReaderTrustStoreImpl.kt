package it.pagopa.proximity.document.reader_auth

import android.util.Log
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.profile.ProfileValidation
import it.pagopa.proximity.document.reader_auth.crl.CertificateCRLValidation
import it.pagopa.proximity.document.reader_auth.crl.CertificateCRLValidationException
import org.bouncycastle.asn1.x500.X500Name
import java.security.InvalidAlgorithmParameterException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXCertPathValidatorResult
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date

internal class ReaderTrustStoreImpl(
    private val trustedCertificates: List<X509Certificate>,
    private val profileValidation: ProfileValidation
) : ReaderTrustStore {
    private val tag = this.javaClass.name
    private val trustedCertMap: Map<X500Name, X509Certificate> by lazy {
        trustedCertificates.associateBy { X500Name(it.subjectX500Principal.name) }
    }

    override fun createCertificationTrustPath(chain: List<X509Certificate>): List<X509Certificate>? {
        for (certificate in chain) {
            val x500Name = X500Name(certificate.issuerX500Principal.name)
            trustedCertMap[x500Name]?.let {
                return listOf(certificate, it)
            }
        }
        return null
    }

    override fun validateCertificationTrustPath(chainToDocumentSigner: List<X509Certificate>): Boolean {
        for (cert in chainToDocumentSigner) {
            val certificateList = arrayListOf(cert, *trustedCertificates.toTypedArray())

            try {
                val certStore = CertStore.getInstance(
                    "Collection",
                    CollectionCertStoreParameters(certificateList),
                )
                val certificateFactory = CertificateFactory.getInstance("X.509")

                val certPath = certificateFactory.generateCertPath(arrayListOf(cert))
                val trustAnchors = trustedCertificates.map { c ->
                    TrustAnchor(c, null)
                }.toSet()

                val validator = CertPathValidator.getInstance("PKIX")
                val param = PKIXParameters(trustAnchors).apply {
                    isRevocationEnabled = false
                    addCertStore(certStore)
                    date = Date()
                }

                // Path Validation
                val certPathValidationResult =
                    validator.validate(certPath, param) as PKIXCertPathValidatorResult
                val trustAnchor = certPathValidationResult.trustAnchor

                // Profile validation
                val profileValidationResult =
                    when (val trustAnchorCertificate = trustAnchor.trustedCert) {
                        null -> false
                        else -> profileValidation.validate(cert, trustAnchorCertificate)
                    }

                // CRL Validation
                CertificateCRLValidation.verify(cert)
                CertificateCRLValidation.verify(trustAnchor.trustedCert)

                if (profileValidationResult) return true
            } catch (e: Exception) {
                when (e) {
                    is InvalidAlgorithmParameterException -> Log.d(
                        this.tag,
                        "INVALID_ALGORITHM_PARAMETER",
                        e,
                    )

                    is NoSuchAlgorithmException -> ProximityLogger.d(this.tag, "NO_SUCH_ALGORITHM: $e")
                    is CertificateException -> ProximityLogger.d(this.tag, "CERTIFICATE_ERROR: $e")
                    is CertPathValidatorException -> ProximityLogger.d(this.tag, "CERTIFICATE_PATH_ERROR: $e")
                    is CertificateCRLValidationException -> ProximityLogger.d(
                        this.tag,
                        "CERTIFICATE_REVOKED: $e"
                    )
                }
            }
        }

        return false
    }
}