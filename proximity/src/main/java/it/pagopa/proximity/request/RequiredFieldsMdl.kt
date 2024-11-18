package it.pagopa.proximity.request

import android.os.Parcelable
import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

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

    override fun toJson() = JSONObject().apply {
        put("nameSpaces", JSONObject().apply {
            this.addPairJson(height)
            this.addPairJson(weight)
            this.addPairJson(portrait)
            this.addPairJson(birthDate)
            this.addPairJson(eyeColour)
            this.addPairJson(givenName)
            this.addPairJson(issueDate)
            this.addPairJson(ageOver18)
            this.addPairJson(ageOver21)
            this.addPairJson(birthPlace)
            this.addPairJson(expiryDate)
            this.addPairJson(familyName)
            this.addPairJson(hairColour)
            this.addPairJson(nationality)
            this.addPairJson(ageInYears)
            this.addPairJson(residentCity)
            this.addPairJson(ageBirthYear)
            this.addPairJson(residentState)
            this.addPairJson(documentNumber)
            this.addPairJson(issuingCountry)
            this.addPairJson(residentAddress)
            this.addPairJson(residentCountry)
            this.addPairJson(issuingAuthority)
            this.addPairJson(drivingPrivileges)
            this.addPairJson(issuingJurisdiction)
            this.addPairJson(residentPostalCode)
            this.addPairJson(signatureUsualMark)
            this.addPairJson(administrativeNumber)
            this.addPairJson(portraitCaptureDate)
            this.addPairJson(unDistinguishingSign)
            this.addPairJson(givenNameNationalCharacter)
            this.addPairJson(familyNameNationalCharacter)
        })
    }

    companion object {
        fun fromCbor(cbor: CBORObject) = RequiredFieldsMdl(
            height = cbor.get("height")?.AsBoolean() to "height",
            weight = cbor.get("weight")?.AsBoolean() to "weight",
            portrait = cbor.get("portrait")?.AsBoolean() to "portrait",
            birthDate = cbor.get("birth_date")?.AsBoolean() to "birth_date",
            eyeColour = cbor.get("eye_colour")?.AsBoolean() to "eye_colour",
            givenName = cbor.get("given_name")?.AsBoolean() to "given_name",
            issueDate = cbor.get("issue_date")?.AsBoolean() to "issue_date",
            ageOver18 = cbor.get("age_over_18")?.AsBoolean() to "age_over_18",
            ageOver21 = cbor.get("age_over_21")?.AsBoolean() to "age_over_21",
            birthPlace = cbor.get("birth_place")?.AsBoolean() to "birth_place",
            expiryDate = cbor.get("expiry_date")?.AsBoolean() to "expiry_date",
            familyName = cbor.get("family_name")?.AsBoolean() to "family_name",
            hairColour = cbor.get("hair_colour")?.AsBoolean() to "hair_colour",
            nationality = cbor.get("nationality")?.AsBoolean() to "nationality",
            ageInYears = cbor.get("age_in_years")?.AsBoolean() to "age_in_years",
            residentCity = cbor.get("resident_city")?.AsBoolean() to "resident_city",
            ageBirthYear = cbor.get("age_birth_year")?.AsBoolean() to "age_birth_year",
            residentState = cbor.get("resident_state")?.AsBoolean() to "resident_state",
            documentNumber = cbor.get("document_number")?.AsBoolean() to "document_number",
            issuingCountry = cbor.get("issuing_country")?.AsBoolean() to "issuing_country",
            residentAddress = cbor.get("resident_address")?.AsBoolean() to "resident_address",
            residentCountry = cbor.get("resident_country")?.AsBoolean() to "resident_country",
            issuingAuthority = cbor.get("issuing_authority")?.AsBoolean() to "issuing_authority",
            drivingPrivileges = cbor.get("driving_privileges")
                ?.AsBoolean() to "driving_privileges",
            issuingJurisdiction = cbor.get("issuing_jurisdiction")
                ?.AsBoolean() to "issuing_jurisdiction",
            residentPostalCode = cbor.get("resident_postal_code")
                ?.AsBoolean() to "resident_postal_code",
            signatureUsualMark = cbor.get("signature_usual_mark")
                ?.AsBoolean() to "signature_usual_mark",
            administrativeNumber = cbor.get("administrative_number")
                ?.AsBoolean() to "administrative_number",
            portraitCaptureDate = cbor.get("portrait_capture_date")
                ?.AsBoolean() to "portrait_capture_date",
            unDistinguishingSign = cbor.get("un_distinguishing_sign")
                ?.AsBoolean() to "un_distinguishing_sign",
            givenNameNationalCharacter = cbor.get("given_name_national_character")
                ?.AsBoolean() to "given_name_national_character",
            familyNameNationalCharacter = cbor.get("family_name_national_character")
                ?.AsBoolean() to "family_name_national_character"
        )
    }
}
