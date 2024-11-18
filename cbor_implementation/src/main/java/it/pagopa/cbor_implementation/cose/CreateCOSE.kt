package it.pagopa.cbor_implementation.cose

import COSE.AlgorithmID.ECDSA_256
import COSE.Sign1Message
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.CheckResult
import com.upokecenter.cbor.CBORObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import COSE.HeaderKeys.Algorithm as BaseAlgorithm

internal class CreateCOSE private constructor() {
    private var alias = ""
    private fun keyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    private fun generateKey() {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    private fun getPrivateKeyAndPublicKey(): Pair<PrivateKey, PublicKey>? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null
        return entry.privateKey to entry.certificate.publicKey
    }

    private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray? {
        return try {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data)
            signature.sign()
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArray.signWithCOSE(): SignWithCOSEResult {
        if (!keyExists())
            generateKey()
        val pair = getPrivateKeyAndPublicKey()
        if (pair == null) return SignWithCOSEResult.Failure("key not found")
        val (private, public) = pair
        val signature = signData(this, private)
        if (signature == null) return SignWithCOSEResult.Failure("Fail to sign")
        return SignWithCOSEResult.Success(signature, public.encoded)
    }

    @CheckResult
    fun sign(data: ByteArray): SignWithCOSEResult {
        val msg = Sign1Message(false, true)
        val protectedAttr: CBORObject =
            msg.protectedAttributes.Add(BaseAlgorithm.AsCBOR(), ECDSA_256.AsCBOR())
        val unprotectedAttributes = msg.unprotectedAttributes
        val array = CBORObject.NewArray()
        array.Add("Signature1")
        array.Add(protectedAttr.EncodeToBytes())
        array.Add(byteArrayOf())
        array.Add(data)
        val dataToSign = array.EncodeToBytes()
        return when (val result = dataToSign.signWithCOSE()) {
            is SignWithCOSEResult.Failure -> SignWithCOSEResult.Failure(result.msg)
            is SignWithCOSEResult.Success -> {
                SignWithCOSEResult.Success(
                    CBORObject.NewArray().apply {
                        this.Add(protectedAttr.EncodeToBytes())
                        this.Add(unprotectedAttributes.EncodeToBytes())
                        this.Add(data)
                        this.Add(result.signature)
                    }.EncodeToBytes(),
                    result.publicKey
                )
            }
        }
    }


    companion object {
        fun build(alias: String) = CreateCOSE().apply {
            this.alias = alias
        }
    }
}