package it.pagopa.io.wallet.cbor.parser

import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.impl.MDoc
import it.pagopa.io.wallet.cbor.model.DocsModel
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CBorParser private constructor(
    private val source: Any,
    private val isByteArray: Boolean = false
) {
    constructor(source: String) : this(source, false)
    constructor(source: ByteArray) : this(source, true)

    @OptIn(ExperimentalEncodingApi::class)
    fun toJson(): String? {
        return try {
            CBORObject.DecodeFromBytes(
                if (isByteArray) source as ByteArray
                else Base64.decode(source as String)
            )?.ToJSONString()
        } catch (_: Exception) {
            null
        }
    }

    fun documentsCborToJson(
        separateElementIdentifier: Boolean = true,
        onComplete: (String) -> Unit,
        onError: ((Exception) -> Unit)?=null
    ) {
        val mDoc = if (isByteArray) MDoc(
            source = this.source as ByteArray
        ) else MDoc(
            source = this.source as String
        )
        mDoc.decodeMDoc(onComplete = { model ->
            val json = JSONObject(model.toJson(separateElementIdentifier))
            val model = DocsModel.fromJson(json)
            onComplete.invoke(model.toJson().toString())
        }, onError = onError?:{})
    }
}