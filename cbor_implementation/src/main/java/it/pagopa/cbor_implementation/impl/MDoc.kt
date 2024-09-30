package it.pagopa.cbor_implementation.impl

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.model.Document
import it.pagopa.cbor_implementation.model.EU_PID_DOCTYPE
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import it.pagopa.cbor_implementation.model.ModelMDoc
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MDoc {
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeMDoc(source: String) =
        decodeMDoc(
            source = Base64.decode(source)
        )

    fun decodeMDoc(source: ByteArray): ModelMDoc {
        val model = CBORObject.DecodeFromBytes(source)
        return ModelMDoc.fromCBORObject(model)
    }

    fun verifyValidity(issuerSigned: Document?): Boolean {
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

        val needDataForEuPid = listOf<String>()

        val needData = when (issuerSigned?.docType) {
            MDL_DOCTYPE -> needDataForMdl
            EU_PID_DOCTYPE -> needDataForEuPid
            else -> return false
        }

        val usedKeys = issuerSigned.issuerSigned?.nameSpaces?.values
            ?.first()
            ?.filter { it.elementValue != null }
            ?.map { it.elementIdentifier }

        return needData
            .all {
                usedKeys?.contains(it) ?: false
            }
    }
}