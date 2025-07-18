package it.pagopa.io.wallet.proximity.document.reader_auth

import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.document.profile.ProfileValidation
import it.pagopa.io.wallet.proximity.document.reader_auth.crl.CertificateCRLValidation
import it.pagopa.io.wallet.proximity.document.reader_auth.crl.CertificateCRLValidationException
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
    private var trustedCertificates: List<X509Certificate>,
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

    private fun <T> List<T>.toArrayList(): ArrayList<T> {
        val back = ArrayList<T>()
        this.forEach {
            back.add(it)
        }
        return back
    }

    private fun List<X509Certificate>.findRoot(): X509Certificate? {
        if (this.isEmpty()) {
            return null
        }
        val subjectDNsInList = this.map { it.subjectX500Principal }.toSet()

        return this.firstOrNull { candidateCert ->
            val issuerDN = candidateCert.issuerX500Principal
            val subjectDN = candidateCert.subjectX500Principal
            if (issuerDN == subjectDN) {
                true
            } else {
                !subjectDNsInList.contains(issuerDN)
            }
        }
    }

    private fun findLastOfChain(
        trustedCerts: List<X509Certificate>,
        certificatesInChain: List<X509Certificate>
    ): Triple<Leaf?, Array<Intermediate?>, Array<Unknown?>>? {
        //TODO: Why is not working putting root and node in trusted certificates and verifierCertificateBa into validate : @file:ReaderTrustStoreTest
        try {
            val cert = trustedCerts.findRoot()
            val leafCert = certificatesInChain.firstOrNull { certToTestAsLeaf ->
                val issuerDn = certToTestAsLeaf.issuerX500Principal
                val issuerIsRoot = issuerDn == cert?.subjectX500Principal
                val issuerIsInChain = certificatesInChain.any { potentialIssuer ->
                    potentialIssuer.subjectX500Principal == issuerDn && potentialIssuer != certToTestAsLeaf
                }
                val isNotAnIssuerInChain = certificatesInChain.none { otherCert ->
                    otherCert.issuerX500Principal == certToTestAsLeaf.subjectX500Principal && otherCert != certToTestAsLeaf
                }
                (issuerIsRoot || issuerIsInChain) && isNotAnIssuerInChain
            }
            val certInChainArrayList = certificatesInChain.toArrayList()
            certInChainArrayList.remove(leafCert)
            val unknownCertificates = certInChainArrayList.filter {
                val isAnIssuerInChain = certInChainArrayList.none { otherCert ->
                    otherCert.issuerX500Principal == leafCert?.subjectX500Principal
                }
                !isAnIssuerInChain
            }
            unknownCertificates.forEach {
                certInChainArrayList.remove(it)
            }
            return Triple(
                leafCert,
                certInChainArrayList.toTypedArray(),
                unknownCertificates.toTypedArray()
            )
        } catch (e: Exception) {
            ProximityLogger.e(
                this.javaClass.name,
                "Errore durante la validazione della catena di certificati: ${e.localizedMessage}"
            )
            return null
        }
    }

    override fun validateCertificationTrustPath(chainToDocumentSigner: List<X509Certificate>): Boolean {
        val triple = this.findLastOfChain(trustedCertificates, chainToDocumentSigner)
        if (triple == null)
            return false
        val (leaf, intermediates, unknowns) = triple
        if (unknowns.isNotEmpty() || leaf == null)
            return false
        val certificateList = ArrayList<X509Certificate?>().apply {
            add(leaf)
            addAll(intermediates.toList())
            addAll(trustedCertificates)
        }
        try {
            val certStore = CertStore.getInstance(
                "Collection",
                CollectionCertStoreParameters(certificateList),
            )
            val certificateFactory = CertificateFactory.getInstance("X.509")

            val certPath = certificateFactory.generateCertPath(certificateList)
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
            val validatedLeaf =
                certPath.certificates.firstOrNull() as? X509Certificate ?: return false
            val trustAnchorUsed = certPathValidationResult.trustAnchor.trustedCert ?: return false

            if (!profileValidation.validate(validatedLeaf, trustAnchorUsed)) {
                ProximityLogger.d(tag, "Profile validation fallita.")
                return false
            }
            // CRL Validation
            CertificateCRLValidation.verify(validatedLeaf)
            CertificateCRLValidation.verify(trustAnchorUsed)
            return true
        } catch (e: Exception) {
            when (e) {
                is InvalidAlgorithmParameterException -> ProximityLogger.d(
                    this.tag,
                    "INVALID_ALGORITHM_PARAMETER: $e"
                )

                is NoSuchAlgorithmException -> ProximityLogger.d(
                    this.tag,
                    "NO_SUCH_ALGORITHM: $e"
                )

                is CertificateException -> ProximityLogger.d(this.tag, "CERTIFICATE_ERROR: $e")
                is CertPathValidatorException -> ProximityLogger.d(
                    this.tag,
                    "CERTIFICATE_PATH_ERROR: $e"
                )

                is CertificateCRLValidationException -> ProximityLogger.d(
                    this.tag,
                    "CERTIFICATE_REVOKED: $e"
                )
            }
        }

        return false
    }
}

typealias Leaf = X509Certificate
typealias Intermediate = X509Certificate
typealias Unknown = X509Certificate