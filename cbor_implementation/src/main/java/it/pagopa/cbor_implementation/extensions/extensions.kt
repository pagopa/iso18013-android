package it.pagopa.cbor_implementation.extensions

import com.android.identity.document.NameSpacedData
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.helper.parse


@JvmSynthetic
internal fun ByteArray.getEmbeddedCBORObject(): CBORObject {
    return CBORObject.DecodeFromBytes(this).getEmbeddedCBORObject()
}

@JvmSynthetic
internal fun CBORObject.getEmbeddedCBORObject(): CBORObject {
    return if (HasTag(24)) {
        CBORObject.DecodeFromBytes(GetByteString())
    } else {
        this
    }
}

@JvmSynthetic
internal fun ByteArray.withTag24(): ByteArray {
    return CBORObject.FromObjectAndTag(this, 24).EncodeToBytes()
}

@JvmSynthetic
internal fun ByteArray.toObject(): Any? {
    return CBORObject.DecodeFromBytes(this).parse()
}


@JvmSynthetic
internal fun CBORObject.toDigestIdMapping(): Map<String, List<ByteArray>> = keys.associate {
    it.AsString() to this[it].values.map { v ->
        val el = v.getEmbeddedCBORObject()
        CBORObject.NewMap()
            .Add("digestID", el["digestID"])
            .Add("random", el["random"])
            .Add("elementIdentifier", el["elementIdentifier"])
            .Add("elementValue", CBORObject.Null)
            .EncodeToBytes()
            .withTag24()
    }
}

@JvmSynthetic
internal fun CBORObject.asNameSpacedData(): NameSpacedData {
    val builder = NameSpacedData.Builder()
    keys.forEach { nameSpace ->
        this[nameSpace].values.forEach { v ->
            val el = v.getEmbeddedCBORObject()
            builder.putEntry(
                nameSpace.AsString(),
                el["elementIdentifier"].AsString(),
                el["elementValue"].EncodeToBytes(),
            )
        }
    }
    return builder.build()
}