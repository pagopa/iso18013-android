package it.pagopa.cbor_implementation.extensions

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.identity.crypto.EcSignature
import com.android.identity.document.NameSpacedData
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.helper.parse
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

internal val Context.supportStrongBox: Boolean
    @JvmSynthetic
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && this.packageManager.hasSystemFeature(
        PackageManager.FEATURE_STRONGBOX_KEYSTORE
    )

internal val Context.isDeviceSecure: Boolean
    @JvmSynthetic
    get() = this.getSystemService(KeyguardManager::class.java).isDeviceSecure

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