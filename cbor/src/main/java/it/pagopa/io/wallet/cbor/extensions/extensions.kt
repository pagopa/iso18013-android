package it.pagopa.io.wallet.cbor.extensions

import com.android.identity.crypto.EcSignature
import com.android.identity.document.NameSpacedData
import com.upokecenter.cbor.CBORObject
import org.bouncycastle.asn1.ASN1InputStream
import java.io.ByteArrayInputStream
import java.io.IOException

internal fun EcSignature.Companion.isDer(derEncodedSignature: ByteArray): Boolean {
    val asn1 = try {
        ASN1InputStream(ByteArrayInputStream(derEncodedSignature)).readObject()
    } catch (_: IOException) {
        null
    }
    return asn1 != null
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