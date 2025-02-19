@file:JvmMultifileClass

package it.pagopa.io.wallet.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.parser.CBorParser
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
internal data class RequestWrapper(
    private val cborByte: ByteArray,
    val isAuthenticated: Boolean = false
) : Parcelable {
    @IgnoredOnParcel
    var requiredFields: CBORObject? = null

    @IgnoredOnParcel
    var docTypeCbor: String? = null

    fun prepare() = apply {
        val cbor = CBORObject.DecodeFromBytes(cborByte)
        docTypeCbor = cbor.get("docType")?.AsString()
        val docType = DocType(docTypeCbor)
        if (docType.isAccepted) {
            requiredFields = cbor.get("nameSpaces")
        }
    }

    fun toJson() = CBorParser(requiredFields!!.EncodeToBytes()).toJson()?.let {
        JSONObject().apply {
            put("docType", docTypeCbor)
            put("values", JSONObject(it))
            put("isAuthenticated", isAuthenticated)
        }
    }

    override fun toString(): String {
        return this.requiredFields.toString()
    }
}
