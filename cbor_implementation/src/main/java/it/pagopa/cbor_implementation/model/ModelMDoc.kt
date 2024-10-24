package it.pagopa.cbor_implementation.model

import androidx.annotation.CheckResult
import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.exception.DocTypeNotValid
import it.pagopa.cbor_implementation.exception.MandatoryFieldNotFound
import it.pagopa.cbor_implementation.helper.toModelMDoc

data class ModelMDoc(
    var documents: List<Document>?,
    var status: Int?,
    var version: String?
) {
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
    var docType: String?,
    var issuerSigned: IssuerSigned?
) {
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
}

data class IssuerSigned(
    var nameSpaces: Map<String, List<DocumentX>>?,
    val rawValue: ByteArray? = null,
    val issuerAuth: ByteArray? = null
)
/**
 * elementIdentifier->es.:Name
 * elementValue->es:John*/
data class DocumentX(
    val digestID: Int?,
    val random: ByteArray?,
    val elementIdentifier: String?,
    val elementValue: Any?
)


