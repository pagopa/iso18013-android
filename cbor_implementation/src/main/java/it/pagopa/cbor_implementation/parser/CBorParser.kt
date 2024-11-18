package it.pagopa.cbor_implementation.parser

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.impl.MDoc

class CBorParser(val rawCbor: ByteArray) {
    fun toJson(): String? {
        return CBORObject.DecodeFromBytes(this.rawCbor)?.ToJSONString()
    }

    fun documentsCborToJson(onComplete: (String?) -> Unit) {
        val mDoc = MDoc(source = this.rawCbor)
        mDoc.decodeMDoc(onComplete = { model ->
            onComplete.invoke(model.toJson())
        }, onError = {
            onComplete.invoke(null)
        })
    }
}