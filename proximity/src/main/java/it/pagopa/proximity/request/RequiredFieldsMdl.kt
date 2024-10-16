package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class RequiredFieldsMdl(
    val height: Pair<Boolean?, String>,
    val weight: Pair<Boolean?, String>,
    val portrait: Pair<Boolean?, String>,
    val birthDate: Pair<Boolean?, String>,
    val eyeColour: Pair<Boolean?, String>,
    val givenName: Pair<Boolean?, String>,
    val issueDate: Pair<Boolean?, String>,
    val ageOver18: Pair<Boolean?, String>,
    val ageOver21: Pair<Boolean?, String>,
    val birthPlace: Pair<Boolean?, String>,
    val expiryDate: Pair<Boolean?, String>,
    val familyName: Pair<Boolean?, String>,
    val hairColour: Pair<Boolean?, String>,
    val nationality: Pair<Boolean?, String>,
    val ageInYears: Pair<Boolean?, String>,
    val residentCity: Pair<Boolean?, String>,
    val ageBirthYear: Pair<Boolean?, String>,
    val residentState: Pair<Boolean?, String>,
    val documentNumber: Pair<Boolean?, String>,
    val issuingCountry: Pair<Boolean?, String>,
    val residentAddress: Pair<Boolean?, String>,
    val residentCountry: Pair<Boolean?, String>,
    val issuingAuthority: Pair<Boolean?, String>,
    val drivingPrivileges: Pair<Boolean?, String>,
    val issuingJurisdiction: Pair<Boolean?, String>,
    val residentPostalCode: Pair<Boolean?, String>,
    val signatureUsualMark: Pair<Boolean?, String>,
    val administrativeNumber: Pair<Boolean?, String>,
    val portraitCaptureDate: Pair<Boolean?, String>,
    val unDistinguishingSign: Pair<Boolean?, String>,
    val givenNameNationalCharacter: Pair<Boolean?, String>,
    val familyNameNationalCharacter: Pair<Boolean?, String>
) : RequiredFields(), Parcelable {
    @IgnoredOnParcel
    override val docType: DocType = DocType.MDL
    override fun toArray() = arrayOf(
        height,
        weight,
        portrait,
        birthDate,
        eyeColour,
        givenName,
        issueDate,
        ageOver18,
        ageOver21,
        birthPlace,
        expiryDate,
        familyName,
        hairColour,
        nationality,
        ageInYears,
        residentCity,
        ageBirthYear,
        residentState,
        documentNumber,
        issuingCountry,
        residentAddress,
        residentCountry,
        issuingAuthority,
        drivingPrivileges,
        issuingJurisdiction,
        residentPostalCode,
        signatureUsualMark,
        administrativeNumber,
        portraitCaptureDate,
        unDistinguishingSign,
        givenNameNationalCharacter,
        familyNameNationalCharacter
    )

    companion object {
        private fun CBORObject?.toBoolOrFalse(): Boolean {
            if (this == null) return false
            return if (this.isNull || this.isUndefined)
                false
            else
                this.isFalse
        }
        fun fromCbor(cbor: CBORObject) = RequiredFieldsMdl(
            height = cbor.get("height").toBoolOrFalse() to "height",
            weight = cbor.get("weight").toBoolOrFalse() to "weight",
            portrait = cbor.get("portrait").toBoolOrFalse() to "portrait",
            birthDate = cbor.get("birth_date").toBoolOrFalse() to "birth_date",
            eyeColour = cbor.get("eye_colour").toBoolOrFalse() to "eye_colour",
            givenName = cbor.get("given_name").toBoolOrFalse() to "given_name",
            issueDate = cbor.get("issue_date").toBoolOrFalse() to "issue_date",
            ageOver18 = cbor.get("age_over_18").toBoolOrFalse() to "age_over_18",
            ageOver21 = cbor.get("age_over_21").toBoolOrFalse() to "age_over_21",
            birthPlace = cbor.get("birth_place").toBoolOrFalse() to "birth_place",
            expiryDate = cbor.get("expiry_date").toBoolOrFalse() to "expiry_date",
            familyName = cbor.get("family_name").toBoolOrFalse() to "family_name",
            hairColour = cbor.get("hair_colour").toBoolOrFalse() to "hair_colour",
            nationality = cbor.get("nationality").toBoolOrFalse() to "nationality",
            ageInYears = cbor.get("age_in_years").toBoolOrFalse() to "age_in_years",
            residentCity = cbor.get("resident_city").toBoolOrFalse() to "resident_city",
            ageBirthYear = cbor.get("age_birth_year").toBoolOrFalse() to "age_birth_year",
            residentState = cbor.get("resident_state").toBoolOrFalse() to "resident_state",
            documentNumber = cbor.get("document_number").toBoolOrFalse() to "document_number",
            issuingCountry = cbor.get("issuing_country").toBoolOrFalse() to "issuing_country",
            residentAddress = cbor.get("resident_address").toBoolOrFalse() to "resident_address",
            residentCountry = cbor.get("resident_country").toBoolOrFalse() to "resident_country",
            issuingAuthority = cbor.get("issuing_authority").toBoolOrFalse() to "issuing_authority",
            drivingPrivileges = cbor.get("driving_privileges").toBoolOrFalse() to "driving_privileges",
            issuingJurisdiction = cbor.get("issuing_jurisdiction")
                .toBoolOrFalse() to "issuing_jurisdiction",
            residentPostalCode = cbor.get("resident_postal_code")
                .toBoolOrFalse() to "resident_postal_code",
            signatureUsualMark = cbor.get("signature_usual_mark")
                .toBoolOrFalse() to "signature_usual_mark",
            administrativeNumber = cbor.get("administrative_number")
                .toBoolOrFalse() to "administrative_number",
            portraitCaptureDate = cbor.get("portrait_capture_date")
                .toBoolOrFalse() to "portrait_capture_date",
            unDistinguishingSign = cbor.get("un_distinguishing_sign")
                .toBoolOrFalse() to "un_distinguishing_sign",
            givenNameNationalCharacter = cbor.get("given_name_national_character")
                .toBoolOrFalse() to "given_name_national_character",
            familyNameNationalCharacter = cbor.get("family_name_national_character")
                .toBoolOrFalse() to "family_name_national_character"
        )
    }
}
