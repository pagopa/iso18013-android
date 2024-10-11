package it.pagopa.cbor_implementation.impl

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.model.ModelMDoc
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MDoc private constructor(
    private val source: Any,
    private val isByteArray: Boolean = false
) {
    constructor(source: String) : this(source, false)
    constructor(source: ByteArray) : this(source, true)

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeMDoc(
        onComplete: (ModelMDoc) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            decodeMDoc(
                if (isByteArray) source as ByteArray
                else Base64.decode(source as String), onComplete, onError
            )
        } catch (ex: Exception) {
            onError.invoke(ex)
        }
    }

    private fun decodeMDoc(
        source: ByteArray,
        onComplete: (ModelMDoc) -> Unit,
        onError: (Exception) -> Unit
    ) {
        ModelMDoc.fromCBORObject(
            model = CBORObject.DecodeFromBytes(source),
            onComplete = onComplete,
            onError = onError
        )
    }
}