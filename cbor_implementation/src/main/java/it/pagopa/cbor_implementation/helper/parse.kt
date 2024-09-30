package it.pagopa.cbor_implementation.helper

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType

internal fun CBORObject.parse(): Any? {
    if (isNull) return null
    if (isTrue) return true
    if (isFalse) return false

    return when (this.type) {
        CBORType.Boolean, CBORType.SimpleValue -> isTrue
        CBORType.ByteString -> GetByteString()
        CBORType.TextString -> AsString()
        CBORType.Array -> values.map { it.parse() }.toList()
        CBORType.Map -> keys.associate { it.parse() to this[it].parse() }
        CBORType.Number, CBORType.Integer -> when {
            CanValueFitInInt32() -> ToObject(Int::class.java)
            CanValueFitInInt64() -> ToObject(Long::class.java)
            else -> ToObject(Double::class.java)
        }

        CBORType.FloatingPoint -> ToObject(Float::class.java)
        else -> null
    }
}