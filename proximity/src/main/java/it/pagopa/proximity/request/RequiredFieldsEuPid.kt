package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class RequiredFieldsEuPid(
    val gender: Pair<Boolean?, String>,
    val portrait: Pair<Boolean?, String>,
    val birthCity: Pair<Boolean?, String>,
    val birthDate: Pair<Boolean?, String>,
    val givenName: Pair<Boolean?, String>,
    val ageOver13: Pair<Boolean?, String>,
    val ageOver16: Pair<Boolean?, String>,
    val ageOver18: Pair<Boolean?, String>,
    val ageOver21: Pair<Boolean?, String>,
    val ageOver60: Pair<Boolean?, String>,
    val ageOver65: Pair<Boolean?, String>,
    val ageOver68: Pair<Boolean?, String>,
    val birthPlace: Pair<Boolean?, String>,
    val birthState: Pair<Boolean?, String>,
    val expiryDate: Pair<Boolean?, String>,
    val familyName: Pair<Boolean?, String>,
    val nationality: Pair<Boolean?, String>,
    val ageInYears: Pair<Boolean?, String>,
    val birthCountry: Pair<Boolean?, String>,
    val issuanceDate: Pair<Boolean?, String>,
    val residentCity: Pair<Boolean?, String>,
    val ageBirthYear: Pair<Boolean?, String>,
    val residentState: Pair<Boolean?, String>,
    val documentNumber: Pair<Boolean?, String>,
    val issuingCountry: Pair<Boolean?, String>,
    val residentStreet: Pair<Boolean?, String>,
    val givenNameBirth: Pair<Boolean?, String>,
    val residentAddress: Pair<Boolean?, String>,
    val residentCountry: Pair<Boolean?, String>,
    val familyNameBirth: Pair<Boolean?, String>,
    val issuingAuthority: Pair<Boolean?, String>,
    val issuingJurisdiction: Pair<Boolean?, String>,
    val residentPostalCode: Pair<Boolean?, String>,
    val administrativeNumber: Pair<Boolean?, String>,
    val portraitCaptureDate: Pair<Boolean?, String>,
    val residentHouseNumber: Pair<Boolean?, String>
) : RequiredFields(), Parcelable {
    @IgnoredOnParcel
    override val docType: DocType = DocType.EU_PID
    override fun toArray() = arrayOf(
        gender,
        portrait,
        birthCity,
        birthDate,
        givenName,
        ageOver13,
        ageOver16,
        ageOver18,
        ageOver21,
        ageOver60,
        ageOver65,
        ageOver68,
        birthPlace,
        birthState,
        expiryDate,
        familyName,
        nationality,
        ageInYears,
        birthCountry,
        issuanceDate,
        residentCity,
        ageBirthYear,
        residentState,
        documentNumber,
        issuingCountry,
        residentStreet,
        givenNameBirth,
        residentAddress,
        residentCountry,
        familyNameBirth,
        issuingAuthority,
        issuingJurisdiction,
        residentPostalCode,
        administrativeNumber,
        portraitCaptureDate,
        residentHouseNumber
    )

    companion object {
        private fun CBORObject?.toBoolOrFalse(): Boolean {
            if (this == null) return false
            return if (this.isNull || this.isUndefined)
                false
            else
                this.isFalse
        }
        fun fromCbor(cbor: CBORObject) = RequiredFieldsEuPid(
            gender = cbor.get("gender").toBoolOrFalse() to "gender",
            portrait = cbor.get("portrait").toBoolOrFalse() to "portrait",
            birthCity = cbor.get("birth_city").toBoolOrFalse() to "birth_city",
            birthDate = cbor.get("birth_date").toBoolOrFalse() to "birth_date",
            givenName = cbor.get("given_name").toBoolOrFalse() to "given_name",
            ageOver13 = cbor.get("age_over_13").toBoolOrFalse() to "age_over_13",
            ageOver16 = cbor.get("age_over_16").toBoolOrFalse() to "age_over_16",
            ageOver18 = cbor.get("age_over_18").toBoolOrFalse() to "age_over_18",
            ageOver21 = cbor.get("age_over_21").toBoolOrFalse() to "age_over_21",
            ageOver60 = cbor.get("age_over_60").toBoolOrFalse() to "age_over_60",
            ageOver65 = cbor.get("age_over_65").toBoolOrFalse() to "age_over_65",
            ageOver68 = cbor.get("age_over_68").toBoolOrFalse() to "age_over_68",
            birthPlace = cbor.get("birth_place").toBoolOrFalse() to "birth_place",
            birthState = cbor.get("birth_state").toBoolOrFalse() to "birth_state",
            expiryDate = cbor.get("expiry_date").toBoolOrFalse() to "expiry_date",
            familyName = cbor.get("family_name").toBoolOrFalse() to "family_name",
            nationality = cbor.get("nationality").toBoolOrFalse() to "nationality",
            ageInYears = cbor.get("age_in_years").toBoolOrFalse() to "age_in_years",
            birthCountry = cbor.get("birth_country").toBoolOrFalse() to "birth_country",
            issuanceDate = cbor.get("issuance_date").toBoolOrFalse() to "issuance_date",
            residentCity = cbor.get("resident_city").toBoolOrFalse() to "resident_city",
            ageBirthYear = cbor.get("age_birth_year").toBoolOrFalse() to "age_birth_year",
            residentState = cbor.get("resident_state").toBoolOrFalse() to "resident_state",
            documentNumber = cbor.get("document_number").toBoolOrFalse() to "document_number",
            issuingCountry = cbor.get("issuing_country").toBoolOrFalse() to "issuing_country",
            residentStreet = cbor.get("resident_street").toBoolOrFalse() to "resident_street",
            givenNameBirth = cbor.get("given_name_birth").toBoolOrFalse() to "given_name_birth",
            residentAddress = cbor.get("resident_address").toBoolOrFalse() to "resident_address",
            residentCountry = cbor.get("resident_country").toBoolOrFalse() to "resident_country",
            familyNameBirth = cbor.get("family_name_birth").toBoolOrFalse() to "family_name_birth",
            issuingAuthority = cbor.get("issuing_authority").toBoolOrFalse() to "issuing_authority",
            issuingJurisdiction = cbor.get("issuing_jurisdiction")
                .toBoolOrFalse() to "issuing_jurisdiction",
            residentPostalCode = cbor.get("resident_postal_code")
                .toBoolOrFalse() to "resident_postal_code",
            administrativeNumber = cbor.get("administrative_number")
                .toBoolOrFalse() to "administrative_number",
            portraitCaptureDate = cbor.get("portrait_capture_date")
                .toBoolOrFalse() to "portrait_capture_date",
            residentHouseNumber = cbor.get("resident_house_number")
                .toBoolOrFalse() to "resident_house_number"
        )
    }
}
