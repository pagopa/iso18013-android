package it.pagopa.proximity.request

import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import org.json.JSONObject

abstract class RequiredFields {
    abstract val docType: DocType
    abstract fun toJson(): JSONObject
    abstract fun toArray(): Array<Pair<Boolean?, String>>
    protected fun JSONObject.addPairJson(valuePair: Pair<Boolean?, String>) {
        val (intentToRetain, value) = valuePair
        if (intentToRetain == null) {
            this@addPairJson.put(value, JSONObject().apply {
                put("requested", false)
                put("intentToRetain", false)
            })
        } else {
            this@addPairJson.put(value, JSONObject().apply {
                put("requested", true)
                put("intentToRetain", intentToRetain)
            })
        }
    }

    companion object {
        internal operator fun invoke(
            docType: DocType,
            cbor: CBORObject
        ): RequiredFields = if (docType == DocType.EU_PID)
            RequiredFieldsEuPid.fromCbor(cbor)
        else
            RequiredFieldsMdl.fromCbor(cbor)
    }
}