package it.pagopa.io.wallet.cbor.parser

import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.impl.MDoc

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