package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

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

    override fun toJson() = JSONObject().apply {
        put("docType",docType.value)
        put("nameSpaces", JSONObject().apply {
            this.addPairJson(gender)
            this.addPairJson(portrait)
            this.addPairJson(birthCity)
            this.addPairJson(birthDate)
            this.addPairJson(givenName)
            this.addPairJson(ageOver13)
            this.addPairJson(ageOver16)
            this.addPairJson(ageOver18)
            this.addPairJson(ageOver21)
            this.addPairJson(ageOver60)
            this.addPairJson(ageOver65)
            this.addPairJson(ageOver68)
            this.addPairJson(birthPlace)
            this.addPairJson(birthState)
            this.addPairJson(expiryDate)
            this.addPairJson(familyName)
            this.addPairJson(nationality)
            this.addPairJson(ageInYears)
            this.addPairJson(birthCountry)
            this.addPairJson(issuanceDate)
            this.addPairJson(residentCity)
            this.addPairJson(ageBirthYear)
            this.addPairJson(residentState)
            this.addPairJson(documentNumber)
            this.addPairJson(issuingCountry)
            this.addPairJson(residentStreet)
            this.addPairJson(givenNameBirth)
            this.addPairJson(residentAddress)
            this.addPairJson(residentCountry)
            this.addPairJson(familyNameBirth)
            this.addPairJson(issuingAuthority)
            this.addPairJson(issuingJurisdiction)
            this.addPairJson(residentPostalCode)
            this.addPairJson(administrativeNumber)
            this.addPairJson(portraitCaptureDate)
            this.addPairJson(residentHouseNumber)
        })
    }

    companion object {
        fun fromCbor(cbor: CBORObject) = RequiredFieldsEuPid(
            gender = cbor.get("gender")?.AsBoolean() to "gender",
            portrait = cbor.get("portrait")?.AsBoolean() to "portrait",
            birthCity = cbor.get("birth_city")?.AsBoolean() to "birth_city",
            birthDate = cbor.get("birth_date")?.AsBoolean() to "birth_date",
            givenName = cbor.get("given_name")?.AsBoolean() to "given_name",
            ageOver13 = cbor.get("age_over_13")?.AsBoolean() to "age_over_13",
            ageOver16 = cbor.get("age_over_16")?.AsBoolean() to "age_over_16",
            ageOver18 = cbor.get("age_over_18")?.AsBoolean() to "age_over_18",
            ageOver21 = cbor.get("age_over_21")?.AsBoolean() to "age_over_21",
            ageOver60 = cbor.get("age_over_60")?.AsBoolean() to "age_over_60",
            ageOver65 = cbor.get("age_over_65")?.AsBoolean() to "age_over_65",
            ageOver68 = cbor.get("age_over_68")?.AsBoolean() to "age_over_68",
            birthPlace = cbor.get("birth_place")?.AsBoolean() to "birth_place",
            birthState = cbor.get("birth_state")?.AsBoolean() to "birth_state",
            expiryDate = cbor.get("expiry_date")?.AsBoolean() to "expiry_date",
            familyName = cbor.get("family_name")?.AsBoolean() to "family_name",
            nationality = cbor.get("nationality")?.AsBoolean() to "nationality",
            ageInYears = cbor.get("age_in_years")?.AsBoolean() to "age_in_years",
            birthCountry = cbor.get("birth_country")?.AsBoolean() to "birth_country",
            issuanceDate = cbor.get("issuance_date")?.AsBoolean() to "issuance_date",
            residentCity = cbor.get("resident_city")?.AsBoolean() to "resident_city",
            ageBirthYear = cbor.get("age_birth_year")?.AsBoolean() to "age_birth_year",
            residentState = cbor.get("resident_state")?.AsBoolean() to "resident_state",
            documentNumber = cbor.get("document_number")?.AsBoolean() to "document_number",
            issuingCountry = cbor.get("issuing_country")?.AsBoolean() to "issuing_country",
            residentStreet = cbor.get("resident_street")?.AsBoolean() to "resident_street",
            givenNameBirth = cbor.get("given_name_birth")?.AsBoolean() to "given_name_birth",
            residentAddress = cbor.get("resident_address")?.AsBoolean() to "resident_address",
            residentCountry = cbor.get("resident_country")?.AsBoolean() to "resident_country",
            familyNameBirth = cbor.get("family_name_birth")?.AsBoolean() to "family_name_birth",
            issuingAuthority = cbor.get("issuing_authority")?.AsBoolean() to "issuing_authority",
            issuingJurisdiction = cbor.get("issuing_jurisdiction")
                ?.AsBoolean() to "issuing_jurisdiction",
            residentPostalCode = cbor.get("resident_postal_code")
                ?.AsBoolean() to "resident_postal_code",
            administrativeNumber = cbor.get("administrative_number")
                ?.AsBoolean() to "administrative_number",
            portraitCaptureDate = cbor.get("portrait_capture_date")
                ?.AsBoolean() to "portrait_capture_date",
            residentHouseNumber = cbor.get("resident_house_number")
                ?.AsBoolean() to "resident_house_number"
        )
    }
}
