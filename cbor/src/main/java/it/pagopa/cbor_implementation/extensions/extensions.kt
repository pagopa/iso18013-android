package it.pagopa.cbor_implementation.extensions

import com.android.identity.crypto.EcSignature
import com.android.identity.document.NameSpacedData
import com.upokecenter.cbor.CBORObject
import org.bouncycastle.asn1.ASN1InputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence

internal fun EcSignature.Companion.isDer(derEncodedSignature: ByteArray): Boolean {
    val asn1 = try {
        ASN1InputStream(ByteArrayInputStream(derEncodedSignature)).readObject() as? ASN1Sequence
    } catch (_: IOException) {
        return false
    } catch (_: ClassCastException) {
        return false
    }
    // Check if sequence has effectively two elements
    if (asn1 == null || asn1.size() != 2) return false

    // Check if both are valid integers
    val r = asn1.getObjectAt(0) as? ASN1Integer ?: return false
    val s = asn1.getObjectAt(1) as? ASN1Integer ?: return false
    // Check if r and s are positive
    return !(!isValidInteger(r.value) || !isValidInteger(s.value))
}

private fun isValidInteger(value: java.math.BigInteger): Boolean {
    if (value.signum() < 0) return false
    val bytes = value.toByteArray()
    return !(bytes.size > 1 && bytes[0].toInt() == 0 && bytes[1].toInt() and 0x80 == 0)
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