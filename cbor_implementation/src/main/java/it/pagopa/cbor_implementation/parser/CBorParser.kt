package it.pagopa.cbor_implementation.parser

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.impl.MDoc

class CBorParser(val rawCbor: ByteArray) {
    fun toJson(): String? {
        return try {
            CBORObject.DecodeFromBytes(this.rawCbor)?.ToJSONString()
        } catch (_: Exception) {
            null
        }
    }

    fun documentsCborToJson(
        separateElementIdentifier: Boolean = true,
        onComplete: (String?) -> Unit
    ) {
        val mDoc = MDoc(source = this.rawCbor)
        mDoc.decodeMDoc(onComplete = { model ->
            onComplete.invoke(model.toJson(separateElementIdentifier))
        }, onError = {
            onComplete.invoke(null)
        })
    }
}