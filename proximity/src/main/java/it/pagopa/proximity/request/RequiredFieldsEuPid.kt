package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class RequiredFieldsEuPid(
    val gender: Pair<Boolean, String>,
    val portrait: Pair<Boolean, String>,
    val birthCity: Pair<Boolean, String>,
    val birthDate: Pair<Boolean, String>,
    val givenName: Pair<Boolean, String>,
    val ageOver13: Pair<Boolean, String>,
    val ageOver16: Pair<Boolean, String>,
    val ageOver18: Pair<Boolean, String>,
    val ageOver21: Pair<Boolean, String>,
    val ageOver60: Pair<Boolean, String>,
    val ageOver65: Pair<Boolean, String>,
    val ageOver68: Pair<Boolean, String>,
    val birthPlace: Pair<Boolean, String>,
    val birthState: Pair<Boolean, String>,
    val expiryDate: Pair<Boolean, String>,
    val familyName: Pair<Boolean, String>,
    val nationality: Pair<Boolean, String>,
    val ageInYears: Pair<Boolean, String>,
    val birthCountry: Pair<Boolean, String>,
    val issuanceDate: Pair<Boolean, String>,
    val residentCity: Pair<Boolean, String>,
    val ageBirthYear: Pair<Boolean, String>,
    val residentState: Pair<Boolean, String>,
    val documentNumber: Pair<Boolean, String>,
    val issuingCountry: Pair<Boolean, String>,
    val residentStreet: Pair<Boolean, String>,
    val givenNameBirth: Pair<Boolean, String>,
    val residentAddress: Pair<Boolean, String>,
    val residentCountry: Pair<Boolean, String>,
    val familyNameBirth: Pair<Boolean, String>,
    val issuingAuthority: Pair<Boolean, String>,
    val issuingJurisdiction: Pair<Boolean, String>,
    val residentPostalCode: Pair<Boolean, String>,
    val administrativeNumber: Pair<Boolean, String>,
    val portraitCaptureDate: Pair<Boolean, String>,
    val residentHouseNumber: Pair<Boolean, String>
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
        private fun CBORObject?.toBoolOrTrue() = this?.AsBoolean() != false
        fun fromCbor(cbor: CBORObject) = RequiredFieldsEuPid(
            gender = cbor.get("gender").toBoolOrTrue() to "gender",
            portrait = cbor.get("portrait").toBoolOrTrue() to "portrait",
            birthCity = cbor.get("birth_city").toBoolOrTrue() to "birth_city",
            birthDate = cbor.get("birth_date").toBoolOrTrue() to "birth_date",
            givenName = cbor.get("given_name").toBoolOrTrue() to "given_name",
            ageOver13 = cbor.get("age_over_13").toBoolOrTrue() to "age_over_13",
            ageOver16 = cbor.get("age_over_16").toBoolOrTrue() to "age_over_16",
            ageOver18 = cbor.get("age_over_18").toBoolOrTrue() to "age_over_18",
            ageOver21 = cbor.get("age_over_21").toBoolOrTrue() to "age_over_21",
            ageOver60 = cbor.get("age_over_60").toBoolOrTrue() to "age_over_60",
            ageOver65 = cbor.get("age_over_65").toBoolOrTrue() to "age_over_65",
            ageOver68 = cbor.get("age_over_68").toBoolOrTrue() to "age_over_68",
            birthPlace = cbor.get("birth_place").toBoolOrTrue() to "birth_place",
            birthState = cbor.get("birth_state").toBoolOrTrue() to "birth_state",
            expiryDate = cbor.get("expiry_date").toBoolOrTrue() to "expiry_date",
            familyName = cbor.get("family_name").toBoolOrTrue() to "family_name",
            nationality = cbor.get("nationality").toBoolOrTrue() to "nationality",
            ageInYears = cbor.get("age_in_years").toBoolOrTrue() to "age_in_years",
            birthCountry = cbor.get("birth_country").toBoolOrTrue() to "birth_country",
            issuanceDate = cbor.get("issuance_date").toBoolOrTrue() to "issuance_date",
            residentCity = cbor.get("resident_city").toBoolOrTrue() to "resident_city",
            ageBirthYear = cbor.get("age_birth_year").toBoolOrTrue() to "age_birth_year",
            residentState = cbor.get("resident_state").toBoolOrTrue() to "resident_state",
            documentNumber = cbor.get("document_number").toBoolOrTrue() to "document_number",
            issuingCountry = cbor.get("issuing_country").toBoolOrTrue() to "issuing_country",
            residentStreet = cbor.get("resident_street").toBoolOrTrue() to "resident_street",
            givenNameBirth = cbor.get("given_name_birth").toBoolOrTrue() to "given_name_birth",
            residentAddress = cbor.get("resident_address").toBoolOrTrue() to "resident_address",
            residentCountry = cbor.get("resident_country").toBoolOrTrue() to "resident_country",
            familyNameBirth = cbor.get("family_name_birth").toBoolOrTrue() to "family_name_birth",
            issuingAuthority = cbor.get("issuing_authority").toBoolOrTrue() to "issuing_authority",
            issuingJurisdiction = cbor.get("issuing_jurisdiction")
                .toBoolOrTrue() to "issuing_jurisdiction",
            residentPostalCode = cbor.get("resident_postal_code")
                .toBoolOrTrue() to "resident_postal_code",
            administrativeNumber = cbor.get("administrative_number")
                .toBoolOrTrue() to "administrative_number",
            portraitCaptureDate = cbor.get("portrait_capture_date")
                .toBoolOrTrue() to "portrait_capture_date",
            residentHouseNumber = cbor.get("resident_house_number")
                .toBoolOrTrue() to "resident_house_number"
        )
    }
}
