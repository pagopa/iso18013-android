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

    fun verifyValidity(
        issuerSigned: Document?,
    ): Pair<Boolean, Exception?> {
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

        val needData = when (issuerSigned?.docType) {
            MDL_DOCTYPE -> needDataForMdl
            EU_PID_DOCTYPE -> needDataForEuPid
            else -> {
                return false to DocTypeNotValid(issuerSigned?.docType)
            }
        }

        val usedKeys = issuerSigned.issuerSigned?.nameSpaces?.get(issuerSigned.docType)
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
}