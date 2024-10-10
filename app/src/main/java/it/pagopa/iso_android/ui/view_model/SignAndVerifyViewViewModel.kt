package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.cose.COSEManager
import it.pagopa.cbor_implementation.cose.SignWithCOSEResult
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.iso_android.ui.AppDialog
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SignAndVerifyViewViewModel(
    private val coseManager: COSEManager
) : ViewModel() {
    val stringToSign = mutableStateOf("")
    val stringToCheck: MutableState<String> = mutableStateOf("")
    val publicKey = mutableStateOf("")
    var appDialog = mutableStateOf<AppDialog?>(null)
    var isValidString = mutableStateOf("")

    @OptIn(ExperimentalEncodingApi::class)
    fun verify() {
        var breakIt = false
        var msg = StringBuilder().apply { append("compile ") }
        val str = stringToCheck.value
        val pubKey = publicKey.value
        if (str.isEmpty()) {
            msg.append("To check ")
            breakIt = true
        }
        if (pubKey.isEmpty()) {
            msg.append(if (str.isEmpty()) "and PublicKey " else "PublicKey ")
            breakIt = true
        }
        msg.append("value to verify the signature")
        if (breakIt) {
            isValidString.value = msg.toString()
            return
        }
        try {
            val dataSigned = Base64.decode(str)
            val publicKey = Base64.decode(pubKey)
            isValidString.value = this.verify(dataSigned, publicKey).toString()
        } catch (e: Exception) {
            CborLogger.e("verify", e.toString())
            isValidString.value = false.toString()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun sign(what: String) {
        if (what.isEmpty()) return
        when (val result = coseManager.signWithCOSE(
            data = what.toByteArray(),
            strongBox = true,
            attestationChallenge = null,
            alg = Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA,
            alias = UUID.randomUUID().toString()
        )) {
            is SignWithCOSEResult.Failure -> failureAppDialog(result.msg)
            is SignWithCOSEResult.UserAuthRequired -> failureAppDialog("user auth req.")
            is SignWithCOSEResult.Success -> {
                successAppDialog()
                val dataSigned = Base64.encode(result.signature)
                val publicKey = Base64.encode(result.publicKey)
                this.stringToCheck.value = dataSigned
                this.publicKey.value = publicKey
                CborLogger.i("dataSigned", dataSigned)
                CborLogger.i("publicKey", publicKey)
            }
        }
    }

    private fun verify(what: ByteArray, pubKey: ByteArray) = coseManager.verifySign1(
        dataSigned = what,
        publicKey = pubKey
    )

    private fun successAppDialog() {
        appDialog.value = AppDialog(
            title = "Success",
            image = null,
            description = "signed successfully",
            button = AppDialog.DialogButton(text = "ok") {
                this.appDialog.value = null
            }
        )
    }

    private fun failureAppDialog(msg: String) {
        appDialog.value = AppDialog(
            title = "Error",
            image = null,
            description = msg,
            button = AppDialog.DialogButton(text = "ok") {
                this.appDialog.value = null
            }
        )
    }
}