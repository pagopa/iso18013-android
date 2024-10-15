@file:JvmMultifileClass

package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize


/*Example EU pid
* {"docType": "eu.europa.ec.eudi.pid.1", "nameSpaces": {"eu.europa.ec.eudi.pid.1": {"gender": false, "portrait": false, "birth_city": false, "birth_date": false, "given_name": false, "age_over_13": false, "age_over_16": false, "age_over_18": false, "age_over_21": false, "age_over_60": false, "age_over_65": false, "age_over_68": false, "birth_place": false, "birth_state": false, "expiry_date": false, "family_name": false, "nationality": false, "age_in_years": false, "birth_country": false, "issuance_date": false, "resident_city": false, "age_birth_year": false, "resident_state": false, "document_number": false, "issuing_country": false, "resident_street": false, "given_name_birth": false, "resident_address": false, "resident_country": false, "family_name_birth": false, "issuing_authority": false, "issuing_jurisdiction": false, "resident_postal_code": false, "administrative_number": false, "portrait_capture_date": false, "resident_house_number": false}}}
*
* Example mdl
* {"docType": "org.iso.18013.5.1.mDL", "nameSpaces": {"org.iso.18013.5.1": {"height": false, "weight": false, "portrait": false, "birth_date": false, "eye_colour": false, "given_name": false, "issue_date": false, "age_over_18": false, "age_over_21": false, "birth_place": false, "expiry_date": false, "family_name": false, "hair_colour": false, "nationality": false, "age_in_years": false, "resident_city": false, "age_birth_year": false, "resident_state": false, "document_number": false, "issuing_country": false, "resident_address": false, "resident_country": false, "issuing_authority": false, "driving_privileges": false, "issuing_jurisdiction": false, "resident_postal_code": false, "signature_usual_mark": false, "administrative_number": false, "portrait_capture_date": false, "un_distinguishing_sign": false, "given_name_national_character": false, "family_name_national_character": false}}}
* */
@Parcelize
data class RequestFromDevice(val list: List<RequestWrapper>) : Parcelable {
    override fun toString(): String {
        val sb = StringBuilder()
        list.forEachIndexed { i, it ->
            sb.append("element $i:\n")
            sb.append(it.toString())
            sb.append("\n")
        }
        return sb.toString()
    }
}


@Parcelize
data class RequestWrapper(private val cborByte: ByteArray) : Parcelable {
    @IgnoredOnParcel
    internal var requiredFields: RequiredFields? = null

    @Throws(NoDocTypeException::class)
    fun prepare() = apply {
        val cbor = CBORObject.DecodeFromBytes(cborByte)
        val docType = DocType.fromString(cbor.get("docType")?.AsString())
        if (docType == null)
            throw NoDocTypeException()
        val namespaces = cbor.get("nameSpaces")
        val fields = namespaces.get(docType.nameSpacesValue)
        this.requiredFields = RequiredFields.fromCbor(docType, fields)
    }

    override fun toString(): String {
        return this.requiredFields.toString()
    }
}
