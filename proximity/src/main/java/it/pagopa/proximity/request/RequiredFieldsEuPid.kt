package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class RequiredFieldsEuPid(
    val gender: Boolean?,
    val portrait: Boolean?,
    val birthCity: Boolean?,
    val birthDate: Boolean?,
    val givenName: Boolean?,
    val ageOver13: Boolean?,
    val ageOver16: Boolean?,
    val ageOver18: Boolean?,
    val ageOver21: Boolean?,
    val ageOver60: Boolean?,
    val ageOver65: Boolean?,
    val ageOver68: Boolean?,
    val birthPlace: Boolean?,
    val birthState: Boolean?,
    val expiryDate: Boolean?,
    val familyName: Boolean?,
    val nationality: Boolean?,
    val ageInYears: Boolean?,
    val birthCountry: Boolean?,
    val issuanceDate: Boolean?,
    val residentCity: Boolean?,
    val ageBirthYear: Boolean?,
    val residentState: Boolean?,
    val documentNumber: Boolean?,
    val issuingCountry: Boolean?,
    val residentStreet: Boolean?,
    val givenNameBirth: Boolean?,
    val residentAddress: Boolean?,
    val residentCountry: Boolean?,
    val familyNameBirth: Boolean?,
    val issuingAuthority: Boolean?,
    val issuingJurisdiction: Boolean?,
    val residentPostalCode: Boolean?,
    val administrativeNumber: Boolean?,
    val portraitCaptureDate: Boolean?,
    val residentHouseNumber: Boolean?
) : RequiredFields(), Parcelable {
    @IgnoredOnParcel
    override val docType: DocType = DocType.EU_PID

    companion object {
        fun fromCbor(cbor: CBORObject) = RequiredFieldsEuPid(
            gender = cbor.get("gender")?.AsBoolean(),
            portrait = cbor.get("portrait")?.AsBoolean(),
            birthCity = cbor.get("birth_city")?.AsBoolean(),
            birthDate = cbor.get("birth_date")?.AsBoolean(),
            givenName = cbor.get("given_name")?.AsBoolean(),
            ageOver13 = cbor.get("age_over_13")?.AsBoolean(),
            ageOver16 = cbor.get("age_over_16")?.AsBoolean(),
            ageOver18 = cbor.get("age_over_18")?.AsBoolean(),
            ageOver21 = cbor.get("age_over_21")?.AsBoolean(),
            ageOver60 = cbor.get("age_over_60")?.AsBoolean(),
            ageOver65 = cbor.get("age_over_65")?.AsBoolean(),
            ageOver68 = cbor.get("age_over_68")?.AsBoolean(),
            birthPlace = cbor.get("birth_place")?.AsBoolean(),
            birthState = cbor.get("birth_state")?.AsBoolean(),
            expiryDate = cbor.get("expiry_date")?.AsBoolean(),
            familyName = cbor.get("family_name")?.AsBoolean(),
            nationality = cbor.get("nationality")?.AsBoolean(),
            ageInYears = cbor.get("age_in_years")?.AsBoolean(),
            birthCountry = cbor.get("birth_country")?.AsBoolean(),
            issuanceDate = cbor.get("issuance_date")?.AsBoolean(),
            residentCity = cbor.get("resident_city")?.AsBoolean(),
            ageBirthYear = cbor.get("age_birth_year")?.AsBoolean(),
            residentState = cbor.get("resident_state")?.AsBoolean(),
            documentNumber = cbor.get("document_number")?.AsBoolean(),
            issuingCountry = cbor.get("issuing_country")?.AsBoolean(),
            residentStreet = cbor.get("resident_street")?.AsBoolean(),
            givenNameBirth = cbor.get("given_name_birth")?.AsBoolean(),
            residentAddress = cbor.get("resident_address")?.AsBoolean(),
            residentCountry = cbor.get("resident_country")?.AsBoolean(),
            familyNameBirth = cbor.get("family_name_birth")?.AsBoolean(),
            issuingAuthority = cbor.get("issuing_authority")?.AsBoolean(),
            issuingJurisdiction = cbor.get("issuing_jurisdiction")?.AsBoolean(),
            residentPostalCode = cbor.get("resident_postal_code")?.AsBoolean(),
            administrativeNumber = cbor.get("administrative_number")?.AsBoolean(),
            portraitCaptureDate = cbor.get("portrait_capture_date")?.AsBoolean(),
            residentHouseNumber = cbor.get("resident_house_number")?.AsBoolean()
        )
    }
}
