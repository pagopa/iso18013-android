package it.pagopa.cbor_implementation.cose

import COSE.AlgorithmID.ECDSA_256
import COSE.Sign1Message
import android.content.Context
import androidx.annotation.CheckResult
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyLockedException
import com.android.identity.storage.StorageEngine
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.helper.toBytes
import java.lang.IllegalArgumentException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import COSE.HeaderKeys.Algorithm as BaseAlgorithm

internal class CreateCOSE private constructor() {
    private lateinit var storageEngine: StorageEngine
    private lateinit var keySettings: CreateKeySettings
    private var alg = Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA
    fun withAlg(alg: Algorithm.SupportedAlgorithms) = apply {
        this.alg = alg
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.signWithCOSE(context: Context): SignWithCOSEResult {
        val softwareSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
        val alias = "pagoPaAlias"
        val keyExists = try {
            softwareSecureArea.getKeyInfo(alias)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
        val algorithm = Algorithm(alg).getCryptoAlgorithm()
        val keyUnlockData = AndroidKeystoreKeyUnlockData(alias)
        if (!keyExists)
            softwareSecureArea.createKey(alias, keySettings)
        val publicKey = softwareSecureArea.getKeyInfo(alias).publicKey
        return try {
            softwareSecureArea.sign(
                alias = alias,
                signatureAlgorithm = algorithm,
                dataToSign = this,
                keyUnlockData = keyUnlockData
            ).let {
                CborLogger.i("signature before", Base64.encode(it.toCoseEncoded()))
                SignWithCOSEResult.Success(it.toCoseEncoded(), publicKey.toBytes())
            }
        } catch (e: Exception) {
            when (e) {
                is KeyLockedException -> SignWithCOSEResult.UserAuthRequired(
                    keyUnlockData.getCryptoObjectForSigning(algorithm)
                )

                else -> SignWithCOSEResult.Failure(e.toString())
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @CheckResult
    fun sign(context: Context, data: ByteArray): SignWithCOSEResult {
        if (!this::storageEngine.isInitialized)
            throw IllegalStateException("StorageEngine not initialized correctly with with method")
        if (!this::keySettings.isInitialized)
            throw IllegalStateException("CreateKeySettings not initialized correctly with with method")
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
        CborLogger.i("dataToSign", Base64.encode(dataToSign))
        CborLogger.i("dataToSign length", dataToSign.size.toString())
        return when (val result = dataToSign.signWithCOSE(context)) {
            is SignWithCOSEResult.Failure -> SignWithCOSEResult.Failure(result.msg)
            is SignWithCOSEResult.UserAuthRequired -> SignWithCOSEResult.UserAuthRequired(
                result.cryptoObject
            )

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
        fun with(
            storageEngine: StorageEngine,
            keySettings: CreateKeySettings
        ) = CreateCOSE().apply {
            this.storageEngine = storageEngine
            this.keySettings = keySettings
        }
    }
}