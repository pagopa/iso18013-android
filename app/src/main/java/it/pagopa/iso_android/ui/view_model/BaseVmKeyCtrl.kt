package it.pagopa.iso_android.ui.view_model

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.lifecycle.ViewModel
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

abstract class BaseVmKeyCtrl : ViewModel() {
    open val alias = "pagoPa"
    private val keyStoreType by lazy {
        "AndroidKeyStore"
    }

    protected fun keyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            keyStore.containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    protected fun generateKey() {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                keyStoreType
            )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
    }
}