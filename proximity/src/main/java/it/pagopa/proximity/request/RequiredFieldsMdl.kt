package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class RequiredFieldsMdl(
    val height: Boolean?,
    val weight: Boolean?,
    val portrait: Boolean?,
    val birthDate: Boolean?,
    val eyeColour: Boolean?,
    val givenName: Boolean?,
    val issueDate: Boolean?,
    val ageOver18: Boolean?,
    val ageOver21: Boolean?,
    val birthPlace: Boolean?,
    val expiryDate: Boolean?,
    val familyName: Boolean?,
    val hairColour: Boolean?,
    val nationality: Boolean?,
    val ageInYears: Boolean?,
    val residentCity: Boolean?,
    val ageBirthYear: Boolean?,
    val residentState: Boolean?,
    val documentNumber: Boolean?,
    val issuingCountry: Boolean?,
    val residentAddress: Boolean?,
    val residentCountry: Boolean?,
    val issuingAuthority: Boolean?,
    val drivingPrivileges: Boolean?,
    val issuingJurisdiction: Boolean?,
    val residentPostalCode: Boolean?,
    val signatureUsualMark: Boolean?,
    val administrativeNumber: Boolean?,
    val portraitCaptureDate: Boolean?,
    val unDistinguishingSign: Boolean?,
    val givenNameNationalCharacter: Boolean?,
    val familyNameNationalCharacter: Boolean?
) : RequiredFields(), Parcelable {
    @IgnoredOnParcel
    override val docType: DocType = DocType.MDL

    companion object {
        fun fromCbor(cbor: CBORObject) = RequiredFieldsMdl(
            height = cbor.get("height")?.AsBoolean(),
            weight = cbor.get("weight")?.AsBoolean(),
            portrait = cbor.get("portrait")?.AsBoolean(),
            birthDate = cbor.get("birth_date")?.AsBoolean(),
            eyeColour = cbor.get("eye_colour")?.AsBoolean(),
            givenName = cbor.get("given_name")?.AsBoolean(),
            issueDate = cbor.get("issue_date")?.AsBoolean(),
            ageOver18 = cbor.get("age_over_18")?.AsBoolean(),
            ageOver21 = cbor.get("age_over_21")?.AsBoolean(),
            birthPlace = cbor.get("birth_place")?.AsBoolean(),
            expiryDate = cbor.get("expiry_date")?.AsBoolean(),
            familyName = cbor.get("family_name")?.AsBoolean(),
            hairColour = cbor.get("hair_colour")?.AsBoolean(),
            nationality = cbor.get("nationality")?.AsBoolean(),
            ageInYears = cbor.get("age_in_years")?.AsBoolean(),
            residentCity = cbor.get("resident_city")?.AsBoolean(),
            ageBirthYear = cbor.get("age_birth_year")?.AsBoolean(),
            residentState = cbor.get("resident_state")?.AsBoolean(),
            documentNumber = cbor.get("document_number")?.AsBoolean(),
            issuingCountry = cbor.get("issuing_country")?.AsBoolean(),
            residentAddress = cbor.get("resident_address")?.AsBoolean(),
            residentCountry = cbor.get("resident_country")?.AsBoolean(),
            issuingAuthority = cbor.get("issuing_authority")?.AsBoolean(),
            drivingPrivileges = cbor.get("driving_privileges")?.AsBoolean(),
            issuingJurisdiction = cbor.get("issuing_jurisdiction")?.AsBoolean(),
            residentPostalCode = cbor.get("resident_postal_code")?.AsBoolean(),
            signatureUsualMark = cbor.get("signature_usual_mark")?.AsBoolean(),
            administrativeNumber = cbor.get("administrative_number")?.AsBoolean(),
            portraitCaptureDate = cbor.get("portrait_capture_date")?.AsBoolean(),
            unDistinguishingSign = cbor.get("un_distinguishing_sign")?.AsBoolean(),
            givenNameNationalCharacter = cbor.get("given_name_national_character")?.AsBoolean(),
            familyNameNationalCharacter = cbor.get("family_name_national_character")?.AsBoolean()
        )
    }
}
