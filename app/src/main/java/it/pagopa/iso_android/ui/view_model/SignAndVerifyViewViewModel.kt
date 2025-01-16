package it.pagopa.iso_android.ui.view_model

import android.util.Base64
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.cose.COSEManager
import it.pagopa.cbor_implementation.cose.SignWithCOSEResult
import it.pagopa.iso_android.ui.AppDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SignAndVerifyViewViewModel(
    private val coseManager: COSEManager
) : ViewModel() {
    val loader = mutableStateOf<String?>(null)
    val stringToSign = mutableStateOf("")
    val stringToCheck: MutableState<String> = mutableStateOf("")
    val publicKey = mutableStateOf("")
    var appDialog = mutableStateOf<AppDialog?>(null)
    var isValidString = mutableStateOf("")

    fun verify() {
        loader.value = "Verifying"
        viewModelScope.launch(Dispatchers.IO) {
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
            if (!breakIt) {
                try {
                    val dataSigned = Base64.decode(str, Base64.DEFAULT or Base64.NO_WRAP)
                    val publicKey = Base64.decode(pubKey, Base64.DEFAULT or Base64.NO_WRAP)
                    isValidString.value = verify(dataSigned, publicKey).toString()
                } catch (e: Exception) {
                    CborLogger.e("verify", e.toString())
                    isValidString.value = false.toString()
                }
            } else {
                isValidString.value = msg.toString()
            }
            loader.value = null
        }
    }

    fun sign(what: String) {
        isValidString.value = ""
        if (what.isEmpty()) return
        loader.value = "Signing"
        viewModelScope.launch(Dispatchers.IO) {
            val data = what.toByteArray()
            when (val result = coseManager.signWithCOSE(
                data = data,
                alias = "pagoPA"
            )) {
                is SignWithCOSEResult.Failure -> failureAppDialog(result.msg)
                is SignWithCOSEResult.Success -> {
                    successAppDialog()
                    val dataSigned = Base64.encodeToString(result.signature, Base64.DEFAULT or Base64.NO_WRAP)
                    val publicKey = Base64.encodeToString(result.publicKey, Base64.DEFAULT or Base64.NO_WRAP)
                    this@SignAndVerifyViewViewModel.stringToCheck.value = dataSigned
                    this@SignAndVerifyViewViewModel.publicKey.value = publicKey
                    CborLogger.i("HexStringReconverted", data.toString(Charsets.UTF_8))
                    CborLogger.i("dataSigned", dataSigned)
                    CborLogger.i("publicKey", publicKey)
                }
            }
            loader.value = null
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