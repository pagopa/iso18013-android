package it.pagopa.io.wallet.cbor.model

import androidx.annotation.CheckResult
import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import it.pagopa.io.wallet.cbor.exception.DocTypeNotValid
import it.pagopa.io.wallet.cbor.exception.MandatoryFieldNotFound
import it.pagopa.io.wallet.cbor.helper.oneDocument
import it.pagopa.io.wallet.cbor.helper.parseIssuerSigned
import it.pagopa.io.wallet.cbor.helper.toModelMDoc
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class ModelMDoc(
    var documents: List<Document>?,
    var status: Int?,
    var version: String?
) {
    fun toJson(separateElementIdentifier: Boolean): String {
        return JSONObject().apply {
            put("documents", this@ModelMDoc.documents?.map { it.toJson(separateElementIdentifier) })
            put("status", status)
            put("version", version)
        }.toString()
    }

    companion object {
        fun fromCBORObject(
            model: CBORObject,
            onComplete: (ModelMDoc) -> Unit,
            onError: (Exception) -> Unit
        ) {
            try {
                onComplete.invoke(model.toModelMDoc())
            } catch (ex: Exception) {
                onError.invoke(ex)
            }
        }
    }
}

data class Document(
    var deviceSigned: DeviceSigned? = null,
    var docType: String?,
    var issuerSigned: IssuerSigned?,
    val rawValue: ByteArray
) {
    fun toJson(separateElementIdentifier: Boolean) = JSONObject().apply {
        put("deviceSigned", deviceSigned?.toJson())
        put("docType", docType)
        put("issuerSigned", issuerSigned?.toJson(separateElementIdentifier))
    }

    @CheckResult
    fun verifyValidity(): Pair<Boolean, Exception?> {
        val needDataForMdl = listOf(
            "family_name",
            "given_name",
            "birth_date",
            "issue_date",
            "expiry_date",
            "issuing_country",
            "issuing_authority",
            "document_number",
            "portrait",
            "driving_privileges",
            "un_distinguishing_sign"
        )

        val needDataForEuPid = listOf(
            "family_name",
            "given_name",
            "birth_date"
        )

        val needData = when (this.docType) {
            MDL_DOCTYPE -> needDataForMdl
            EU_PID_DOCTYPE -> needDataForEuPid
            else -> {
                return false to DocTypeNotValid(this.docType)
            }
        }

        val usedKeys = this.issuerSigned?.nameSpaces?.get(this.docType)
            ?.filter { it.elementValue != null }
            ?.map { it.elementIdentifier }

        val list = needData
            .filter {
                usedKeys?.contains(it)?.not() != false
            }

        return if (list.isEmpty())
            true to null
        else
            false to MandatoryFieldNotFound(list)
    }

    companion object {
        fun fromByteArray(
            model: ByteArray
        ): Document {
            val cbor = CBORObject.DecodeFromBytes(model)
            return cbor.oneDocument()
        }
    }
}

data class DeviceSigned(
    val deviceAuth: DeviceAuth?,
    var nameSpaces: Map<String, Any>?
) {
    fun toJson() = JSONObject().apply {
        put("deviceAuth", deviceAuth?.toJson())
        put("nameSpaces", JSONObject().apply {
            nameSpaces?.forEach {
                this.put(it.key, it.value)
            }
        })
    }
}

data class DeviceAuth(
    val deviceSignature: String?
) {
    fun toJson() = JSONObject().apply {
        put("deviceSignature", deviceSignature)
    }
}

data class IssuerSigned(
    var nameSpaces: Map<String, List<DocumentX>>?,
    val rawValue: ByteArray? = null,
    val nameSpacedData: Map<String, Map<String, ByteArray>>,
    val issuerAuth: IssuerAuth? = null
) {
    fun toJson(separateElementIdentifier: Boolean) = JSONObject().apply {
        put("issuerAuth", issuerAuth?.getValues()?.toJson())
        put("nameSpaces", JSONObject().apply {
            nameSpaces?.forEach {
                this.put(it.key, it.value.map { it.toJson(separateElementIdentifier) })
            }
        })
    }

    companion object {
        fun issuerSignedFromByteArray(
            nameSpaces: ByteArray
        ): IssuerSigned? {
            val cbor = CBORObject.DecodeFromBytes(nameSpaces)
            return cbor.parseIssuerSigned()
        }
    }
}

data class IssuerAuth(
    val rawValue: ByteArray?,
) {
    var protectedHeader: ByteArray? = null
    var unprotectedHeader: List<ByteArray>? = null
    var payload: Payload? = null
    var signature: ByteArray? = null

    data class Payload(private val cbor: CBORObject) {
        var docType: String? = null
        var version: String? = null
        var validityInfo: JSONObject? = null
        var valueDigests: List<ByteArray>? = null
        fun construct(): Payload {
            if (cbor.type != CBORType.Map) return this
            cbor.keys.distinct().forEach {
                when (it.AsString()) {
                    "docType" -> this.docType = cbor[it].AsString()
                    "version" -> this.version = cbor[it].AsString()
                    "validityInfo" -> this.validityInfo = JSONObject(cbor[it].ToJSONString())
                    "valueDigests" -> {
                        val valueCbor = cbor[it][this.docType]
                        this.valueDigests = valueCbor.keys.map { valueCbor[it].GetByteString() }
                    }
                }
            }
            return this
        }
    }

    fun getValues(): IssuerAuth {
        if (this.rawValue == null) return this
        val cborArray = CBORObject.DecodeFromBytes(this.rawValue)
        if (cborArray[0].type != CBORType.ByteString) return this
        protectedHeader = cborArray[0].GetByteString()
        if (cborArray[1].type != CBORType.Map) return this
        unprotectedHeader = cborArray[1].keys.map {
            cborArray[1][it].GetByteString()
        }
        if (cborArray[2].type != CBORType.ByteString) return this
        payload = Payload(
            CBORObject.DecodeFromBytes(
                CBORObject.DecodeFromBytes(cborArray[2].GetByteString()).GetByteString()
            )
        )
        if (cborArray[3].type != CBORType.ByteString) return this
        signature = cborArray[3].GetByteString()
        return this
    }

    fun toJson() = JSONObject().apply {
        if (protectedHeader != null)
            put("protectedHeader", Base64.getUrlEncoder().encodeToString(protectedHeader))
        if (unprotectedHeader != null)
            put("unprotectedHeader", unprotectedHeader?.map {
                Base64.getUrlEncoder().encodeToString(it)
            })
        if (payload != null)
            put("payload", payload?.construct()?.toJson())
        if (signature != null)
            put("signature", Base64.getUrlEncoder().encodeToString(signature))
    }
}

/**
 * elementIdentifier->es.:Name
 * elementValue->es:John*/
data class DocumentX(
    val digestID: Int?,
    val random: ByteArray?,
    val elementIdentifier: String?,
    val elementValue: Any?
) {
    fun toJson(separateElementIdentifier: Boolean) = JSONObject().apply {
        put("digestID", digestID)
        put("random", random?.let {
            Base64.getUrlEncoder().encodeToString(random)
        })
        if (separateElementIdentifier) {
            put("elementIdentifier", elementIdentifier)
            put("elementValue", elementValue.toJson())
        } else {
            if (elementIdentifier != null)
                put(elementIdentifier, elementValue.toJson())
        }
    }
}

private fun Any?.toJson(): Any? {
    if (this == null) return null
    val back = this
    return try {
        JSONObject(back.toString())
    } catch (_: Exception) {
        try {
            JSONArray(back.toString())
        } catch (_: Exception) {
            back
        }
    }
}


