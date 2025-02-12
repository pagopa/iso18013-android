package it.pagopa.io.wallet.cbor.cose

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.android.identity.crypto.EcSignature
import com.android.identity.crypto.toDer
import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.CborLogger
import it.pagopa.io.wallet.cbor.extensions.isDer
import it.pagopa.io.wallet.cbor.helper.addBcIfNeeded
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * With this class you can sign every object with COSE*/
class COSEManager {
    //ONLY FOR TEST PURPOSE
    private var createCOSE: CreateCOSE? = null

    init {
        addBcIfNeeded()
    }

    @VisibleForTesting
    internal fun withCreateCOSE(createCOSE: CreateCOSE) = apply {
        this.createCOSE = createCOSE
    }

    @CheckResult
    fun signWithCOSE(
        data: ByteArray,
        alias: String = "pagoPaAlias",
        isDetached: Boolean = false
    ): SignWithCOSEResult {
        return try {
            if (createCOSE != null)
                createCOSE!!.sign(data, isDetached)
            else
                CreateCOSE.build(alias).sign(data, isDetached)
        } catch (e: Exception) {
            CborLogger.e("signWithCOSE", e.toString())
            SignWithCOSEResult.Failure(e.toString())
        }
    }

    @VisibleForTesting
    fun algorithmFromProtectedHeader(protectedHeader: CBORObject): String {
        return when (protectedHeader[1].AsInt32()) {
            -7 -> "SHA256withECDSA"
            -35 -> "SHA384withECDSA"
            -36 -> "SHA512withECDSA"
            -37 -> "SHA256withRSA"
            -38 -> "SHA384withRSA"
            -39 -> "SHA512withRSA"
            else -> throw IllegalArgumentException("Unsupported algorithm")
        }
    }
    /*

        @Throws(Exception::class)
        private fun getPublicKey(publicKey: ByteArray, algorithm: String): PublicKey {
            val keySpec = X509EncodedKeySpec(publicKey)
            val keyFactory = when {
                algorithm.contains("ECDSA") -> KeyFactory.getInstance("EC", "BC")
                algorithm.contains("RSA") -> KeyFactory.getInstance("RSA", "BC")
                else -> throw IllegalArgumentException("Unsupported key algorithm")
            }
            return keyFactory.generatePublic(keySpec)
        }
    */

    private fun verifySign1(
        signature: ByteArray,
        publicKey: ByteArray,
        protectedHeader: CBORObject,
        data: ByteArray
    ): Boolean {
        return try {
            val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey)
            val publicKeySpec = X509EncodedKeySpec(subjectPublicKeyInfo.getEncoded())
            val keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME)
            val pubKey = keyFactory.generatePublic(publicKeySpec)
            val derSignature = if (EcSignature.isDer(signature))
                signature
            else
                EcSignature.fromCoseEncoded(signature).toDer()
            val alg = algorithmFromProtectedHeader(protectedHeader)
            Signature.getInstance(alg, BouncyCastleProvider.PROVIDER_NAME).apply {
                initVerify(pubKey)
                update(data)
            }.verify(derSignature)
        } catch (e: Exception) {
            CborLogger.e("verifying", e.toString())
            false
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun verifySign1(
        dataSigned: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        try {
            CborLogger.i("dataSigned", Base64.encode(dataSigned))
            val cborArray = CBORObject.DecodeFromBytes(dataSigned)
            // extracting CBOR components
            val protectedHeader = cborArray[0].GetByteString()
            val data = cborArray[2].GetByteString()
            val array = CBORObject.NewArray()
            array.Add("Signature1")
            array.Add(protectedHeader)
            array.Add(byteArrayOf())
            array.Add(data)
            val dataToVerify = array.EncodeToBytes()
            CborLogger.i("dataToVerify", Base64.encode(dataToVerify))
            CborLogger.i("dataToVerify length", dataToVerify.size.toString())
            val signature = cborArray[3].GetByteString()
            CborLogger.i("PUBKEY HERE", Base64.encode(publicKey))
            CborLogger.i("DATA HERE", Base64.encode(data))
            return verifySign1(
                signature,
                publicKey,
                CBORObject.DecodeFromBytes(cborArray[0].GetByteString()),
                dataToVerify
            )
        } catch (e: Exception) {
            CborLogger.e("verifying", e.toString())
            return false
        }
    }
}