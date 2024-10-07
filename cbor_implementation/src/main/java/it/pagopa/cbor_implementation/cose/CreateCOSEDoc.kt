package it.pagopa.cbor_implementation.cose

import COSE.AlgorithmID.ECDSA_256
import COSE.HeaderKeys.Algorithm
import COSE.OneKey
import COSE.Sign1Message
import androidx.annotation.CheckResult
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.extensions.getEmbeddedCBORObject
import it.pagopa.cbor_implementation.extensions.withTag24
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.collections.component1
import kotlin.collections.component2

internal class CreateCOSEDoc private constructor() {
    private lateinit var issuerPrivateKey: PrivateKey
    private lateinit var issuerCertificate: X509Certificate
    private lateinit var msoGenerated: ByteArray
    private val bc by lazy {
        BouncyCastleProvider()
    }

    private fun calculateDigests(
        digestAlg: String,
        issuerSignedItems: CBORObject
    ): Map<Long, ByteArray> {
        return issuerSignedItems.values.associate { issuerSignedItemBytes ->
            val issuerSignedItem = issuerSignedItemBytes.getEmbeddedCBORObject()
            val digest = MessageDigest.getInstance(digestAlg)
                .digest(issuerSignedItemBytes.EncodeToBytes())
            issuerSignedItem["digestID"].AsInt32().toLong() to digest
        }
    }

    fun generateMso(
        digestAlg: String,
        docType: String,
        authKey: EcPublicKey,
        nameSpaces: CBORObject
    ) = apply {
        this.msoGenerated = MobileSecurityObjectGenerator(digestAlg, docType, authKey)
            .apply {
                val now = Clock.System.now()
                val signed = now
                val validFrom = now
                val validUntil =
                    Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 1000L * 60L * 60L * 24L * 365L)
                setValidityInfo(signed, validFrom, validUntil, null)

                val digestIds = nameSpaces.entries.associate { (nameSpace, issuerSignedItems) ->
                    nameSpace.AsString() to calculateDigests(digestAlg, issuerSignedItems)
                }
                digestIds.forEach { (nameSpace, digestIds) ->
                    addDigestIdsForNamespace(nameSpace, digestIds)
                }
            }
            .generate()
    }

    fun withIssuerCertificate(issuerCertificate: String) = apply {
        this.issuerCertificate = PemReader(issuerCertificate.reader())
            .use { reader -> reader.readPemObject().content }
            .let { certificateBytes ->
                CertificateFactory.getInstance("X.509", bc)
                    .generateCertificate(certificateBytes.inputStream())
            } as X509Certificate
    }

    @CheckResult
    fun signMso(): CBORObject {
        if (!this::issuerCertificate.isInitialized)
            throw IllegalStateException("I.C. not found: Have you initialized issuerCertificate with method withIssuerCertificate?")
        if (!this::msoGenerated.isInitialized)
            throw IllegalStateException("Mso not found: Have you created MSO with method generateMso?")
        return Sign1Message(false, true).apply {
            protectedAttributes.Add(Algorithm.AsCBOR(), ECDSA_256.AsCBOR())
            unprotectedAttributes.Add(33L, issuerCertificate.encoded)
            SetContent(msoGenerated.withTag24())
            sign(OneKey(null, issuerPrivateKey))
        }.EncodeToCBORObject()
    }

    companion object {
        fun withPrivateKey(privateKey: String) = CreateCOSEDoc().apply {
            this.issuerPrivateKey = PemReader(privateKey.reader())
                .use { reader -> reader.readPemObject().content }
                .let { privateKeyBytes ->
                    KeyFactory.getInstance("EC", this.bc)
                        .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                }
        }
    }
}