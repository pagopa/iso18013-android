package it.pagopa.cbor_implementation.impl

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.exception.DocTypeNotValid
import it.pagopa.cbor_implementation.exception.MandatoryFieldNotFound
import it.pagopa.cbor_implementation.model.Document
import it.pagopa.cbor_implementation.model.EU_PID_DOCTYPE
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import it.pagopa.cbor_implementation.model.ModelMDoc
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MDoc {
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeMDoc(
        source: String,
        onComplete: (ModelMDoc) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            decodeMDoc(
                source = Base64.decode(source),
                onComplete = onComplete,
                onError = onError
            )
        } catch (ex: Exception) {
            onError.invoke(ex)
        }
    }

    fun decodeMDoc(
        source: ByteArray,
        onComplete: (ModelMDoc) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            ModelMDoc.fromCBORObject(
                model = CBORObject.DecodeFromBytes(source),
                onComplete = onComplete,
                onError = onError
            )
        } catch (ex: Exception) {
            onError.invoke(ex)
        }
    }

    fun verifyValidity(
        issuerSigned: Document?,
        onValidate: () -> Unit,
        onError: (Exception) -> Unit
    ) {
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
            else -> {
                onError.invoke(DocTypeNotValid(issuerSigned?.docType))
                return
            }
        }

        val usedKeys = issuerSigned.issuerSigned?.nameSpaces?.get(issuerSigned.docType)
            ?.filter { it.elementValue != null }
            ?.map { it.elementIdentifier }

        val list = needData
            .filter {
                usedKeys?.contains(it)?.not() ?: true
            }

        if (list.isEmpty())
            onValidate.invoke()
        else
            onError.invoke(MandatoryFieldNotFound(list))
    }
}