package it.pagopa.cbor_implementation.helper

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import it.pagopa.cbor_implementation.extensions.asNameSpacedData
import it.pagopa.cbor_implementation.model.Document
import it.pagopa.cbor_implementation.model.DocumentX
import it.pagopa.cbor_implementation.model.IssuerSigned
import it.pagopa.cbor_implementation.model.ModelMDoc
import java.util.Base64

internal fun CBORObject.parse(): Any? {
    if (isNull) return null
    if (isTrue) return true
    if (isFalse) return false

    return when (this.type) {
        CBORType.Boolean, CBORType.SimpleValue -> isTrue
        CBORType.ByteString -> Base64.getEncoder().encodeToString(GetByteString())
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

internal fun CBORObject.oneDocument(): Document {
    val issuerSigned = this.get("issuerSigned")
    val nameSpaces = issuerSigned.get("nameSpaces")
    val nameSpacesKeys = issuerSigned.get("nameSpaces")?.keys
    val data = nameSpaces.asNameSpacedData()
    return Document(
        docType = this.get("docType")?.AsString(),
        issuerSigned = IssuerSigned(
            nameSpaces = nameSpacesKeys
                ?.let { keys ->
                    val mNameSpaces =
                        mutableMapOf<String, List<DocumentX>>()
                    keys
                        .distinct()
                        .forEach { key ->
                            val mList = mutableListOf<DocumentX>()
                            nameSpaces?.get(key)?.values?.forEach {
                                val value =
                                    CBORObject.DecodeFromBytes(it.GetByteString())
                                mList.add(
                                    DocumentX(
                                        digestID = value.get("digestID")
                                            ?.AsInt32(),
                                        random = value.get("random")
                                            ?.GetByteString(),
                                        elementIdentifier = value.get("elementIdentifier")
                                            ?.AsString(),
                                        elementValue = value.get("elementValue")
                                            ?.parse()
                                    )
                                )
                            }
                            mNameSpaces[key.AsString()] = mList
                        }
                    mNameSpaces
                },
            nameSpacedData = data.nameSpaceNames.associateWith { nameSpace ->
                data.getDataElementNames(nameSpace)
                    .associateWith { elementIdentifier ->
                        data.getDataElement(nameSpace, elementIdentifier)
                    }
            },
            rawValue = issuerSigned?.EncodeToBytes(),
            issuerAuth = issuerSigned?.get("issuerAuth")?.EncodeToBytes(),
        ),
        rawValue = this.EncodeToBytes()
    )
}

internal fun CBORObject.toModelMDoc(): ModelMDoc {
    fun isSingleDoc(): Boolean = this.get("docType") != null

    return if (isSingleDoc()) {
        ModelMDoc(
            documents = listOf(
                this.oneDocument()
            ),
            status = null,
            version = null
        )
    } else {
        ModelMDoc(
            version = this.get("version")?.AsString(),
            status = this.get("status")?.AsInt32(),
            documents = this.get("documents")?.values
                ?.map { doc ->
                    doc.oneDocument()
                }
        )
    }
}