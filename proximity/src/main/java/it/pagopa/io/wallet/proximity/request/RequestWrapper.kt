@file:JvmMultifileClass

package it.pagopa.io.wallet.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.parser.CBorParser
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.Base64

/**
 * A document request of the reader, as sent to the wallet app.
 *
 * @property readerAuth the `ReaderAuth` COSE_Sign1 CBOR, when the reader signed the request
 * @property readerCertificateChain the DER encoded `x5chain` of [readerAuth], starting with the leaf
 * @property isSignatureValid whether [readerAuth] is a valid signature of the leaf certificate,
 * whether the chain is trusted or not
 */
@Parcelize
internal data class RequestWrapper(
    private val cborByte: ByteArray,
    val isAuthenticated: Boolean = false,
    val certSerialNumber: BigInteger?,
    val issuerRdnMap: Map<String, String>?,
    val readerAuth: ByteArray? = null,
    val readerCertificateChain: List<ByteArray>? = null,
    val isSignatureValid: Boolean = false,
) : Parcelable {
    @IgnoredOnParcel
    var requiredFields: CBORObject? = null

    @IgnoredOnParcel
    var docTypeCbor: String? = null

    @IgnoredOnParcel
    var requestInfo: CBORObject? = null

    fun prepare() = apply {
        val cbor = CBORObject.DecodeFromBytes(cborByte)
        docTypeCbor = cbor.get("docType")?.AsString()
        requiredFields = cbor.get("nameSpaces")
        requestInfo = cbor.get("requestInfo")
    }

    fun toJson(): JSONObject? {
        if (requiredFields == null) return null
        val encoder = Base64.getEncoder()
        return CBorParser(requiredFields!!.EncodeToBytes()).toJson()?.let {
            JSONObject().apply {
                put("docType", docTypeCbor)
                put("values", JSONObject(it))
                put("isAuthenticated", isAuthenticated)
                put("isSignatureValid", isSignatureValid)
                certSerialNumber?.let { serial ->
                    put("certificateSerial", serial.toString())
                }
                issuerRdnMap?.let { map ->
                    put("issuerRdnMap", JSONObject(map))
                }
                readerAuth?.let { put("readerAuth", encoder.encodeToString(it)) }
                readerCertificateChain?.let { chain ->
                    put("readerCertificateChain", JSONArray(chain.map(encoder::encodeToString)))
                }
                // Kept as CBOR, since it may carry byte strings, e.g. the registration certificate
                requestInfo?.let { put("requestInfo", encoder.encodeToString(it.EncodeToBytes())) }
            }
        }
    }

    override fun toString(): String {
        return this.requiredFields.toString()
    }
}
