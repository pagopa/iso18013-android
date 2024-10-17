package it.pagopa.proximity.request

import android.content.res.Resources

/**String which represents cbor field*/
typealias CborValue = String
/**String which represents app string for cbor field*/
typealias AppValue = String

/**
 * You can use this class to map all cbor values requested during proximity and show to user your
 * string from [Resources]
 * @param resources Your app resources
 * */
abstract class CborValues(private val resources: Resources) {
    abstract val genderResource: Int
    val gender: String get() = resources.getString(genderResource)
    abstract val portraitResource: Int
    val portrait: String get() = resources.getString(portraitResource)
    abstract val birthCityResource: Int
    val birthCity: String get() = resources.getString(birthCityResource)
    abstract val birthDateResource: Int
    val birthDate: String get() = resources.getString(birthDateResource)
    abstract val givenNameResource: Int
    val givenName: String get() = resources.getString(givenNameResource)
    abstract val ageOver13Resource: Int
    val ageOver13: String get() = resources.getString(ageOver13Resource)
    abstract val ageOver16Resource: Int
    val ageOver16: String get() = resources.getString(ageOver16Resource)
    abstract val ageOver18Resource: Int
    val ageOver18: String get() = resources.getString(ageOver18Resource)
    abstract val ageOver21Resource: Int
    val ageOver21: String get() = resources.getString(ageOver21Resource)
    abstract val ageOver60Resource: Int
    val ageOver60: String get() = resources.getString(ageOver60Resource)
    abstract val ageOver65Resource: Int
    val ageOver65: String get() = resources.getString(ageOver65Resource)
    abstract val ageOver68Resource: Int
    val ageOver68: String get() = resources.getString(ageOver68Resource)
    abstract val birthPlaceResource: Int
    val birthPlace: String get() = resources.getString(birthPlaceResource)
    abstract val birthStateResource: Int
    val birthState: String get() = resources.getString(birthStateResource)
    abstract val expiryDateResource: Int
    val expiryDate: String get() = resources.getString(expiryDateResource)
    abstract val familyNameResource: Int
    val familyName: String get() = resources.getString(familyNameResource)
    abstract val nationalityResource: Int
    val nationality: String get() = resources.getString(nationalityResource)
    abstract val ageInYearsResource: Int
    val ageInYears: String get() = resources.getString(ageInYearsResource)
    abstract val birthCountryResource: Int
    val birthCountry: String get() = resources.getString(birthCountryResource)
    abstract val issuanceDateResource: Int
    val issuanceDate: String get() = resources.getString(issuanceDateResource)
    abstract val residentCityResource: Int
    val residentCity: String get() = resources.getString(residentCityResource)
    abstract val ageBirthYearResource: Int
    val ageBirthYear: String get() = resources.getString(ageBirthYearResource)
    abstract val residentStateResource: Int
    val residentState: String get() = resources.getString(residentStateResource)
    abstract val documentNumberResource: Int
    val documentNumber: String get() = resources.getString(documentNumberResource)
    abstract val issuingCountryResource: Int
    val issuingCountry: String get() = resources.getString(issuingCountryResource)
    abstract val residentStreetResource: Int
    val residentStreet: String get() = resources.getString(residentStreetResource)
    abstract val givenNameBirthResource: Int
    val givenNameBirth: String get() = resources.getString(givenNameBirthResource)
    abstract val residentAddressResource: Int
    val residentAddress: String get() = resources.getString(residentAddressResource)
    abstract val residentCountryResource: Int
    val residentCountry: String get() = resources.getString(residentCountryResource)
    abstract val familyNameBirthResource: Int
    val familyNameBirth: String get() = resources.getString(familyNameBirthResource)
    abstract val issuingAuthorityResource: Int
    val issuingAuthority: String get() = resources.getString(issuingAuthorityResource)
    abstract val issuingJurisdictionResource: Int
    val issuingJurisdiction: String get() = resources.getString(issuingJurisdictionResource)
    abstract val residentPostalCodeResource: Int
    val residentPostalCode: String get() = resources.getString(residentPostalCodeResource)
    abstract val administrativeNumberResource: Int
    val administrativeNumber: String get() = resources.getString(administrativeNumberResource)
    abstract val portraitCaptureDateResource: Int
    val portraitCaptureDate: String get() = resources.getString(portraitCaptureDateResource)
    abstract val residentHouseNumberResource: Int
    val residentHouseNumber: String get() = resources.getString(residentHouseNumberResource)

    /**
     * first value is [CborValue], second value is [AppValue]
     * */
    val euPidCborValues by lazy {
        arrayOf<Pair<CborValue, AppValue>>(
            "gender" to gender,
            "portrait" to portrait,
            "birth_city" to birthCity,
            "birth_date" to birthDate,
            "given_name" to givenName,
            "age_over_13" to ageOver13,
            "age_over_16" to ageOver16,
            "age_over_18" to ageOver18,
            "age_over_21" to ageOver21,
            "age_over_60" to ageOver60,
            "age_over_65" to ageOver65,
            "age_over_68" to ageOver68,
            "birth_place" to birthPlace,
            "birth_state" to birthState,
            "expiry_date" to expiryDate,
            "family_name" to familyName,
            "nationality" to nationality,
            "age_in_years" to ageInYears,
            "birth_country" to birthCountry,
            "issuance_date" to issuanceDate,
            "resident_city" to residentCity,
            "age_birth_year" to ageBirthYear,
            "resident_state" to residentState,
            "document_number" to documentNumber,
            "issuing_country" to issuingCountry,
            "resident_street" to residentStreet,
            "given_name_birth" to givenNameBirth,
            "resident_address" to residentAddress,
            "resident_country" to residentCountry,
            "family_name_birth" to familyNameBirth,
            "issuing_authority" to issuingAuthority,
            "issuing_jurisdiction" to issuingJurisdiction,
            "resident_postal_code" to residentPostalCode,
            "administrative_number" to administrativeNumber,
            "portrait_capture_date" to portraitCaptureDate,
            "resident_house_number" to residentHouseNumber
        )
    }
    abstract val heightResource: Int
    val height: String get() = resources.getString(heightResource)
    abstract val weightResource: Int
    val weight: String get() = resources.getString(weightResource)
    abstract val eyeColourResource: Int
    val eyeColour: String get() = resources.getString(eyeColourResource)
    abstract val hairColourResource: Int
    val hairColour: String get() = resources.getString(hairColourResource)
    abstract val drivingPrivilegesResource: Int
    val drivingPrivileges: String get() = resources.getString(drivingPrivilegesResource)
    abstract val signatureUsualMarkResource: Int
    val signatureUsualMark: String get() = resources.getString(signatureUsualMarkResource)
    abstract val unDistinguishingSignResource: Int
    val unDistinguishingSign: String get() = resources.getString(unDistinguishingSignResource)
    abstract val givenNameNationalCharacterResource: Int
    val givenNameNationalCharacter: String
        get() = resources.getString(
            givenNameNationalCharacterResource
        )
    abstract val familyNameNationalCharacterResource: Int
    val familyNameNationalCharacter: String
        get() = resources.getString(
            familyNameNationalCharacterResource
        )

    /**
     * first value is [CborValue], second value is [AppValue]
     * */
    val mdlCborValues by lazy {
        arrayOf<Pair<CborValue, AppValue>>(
            "height" to height,
            "weight" to weight,
            "portrait" to portrait,
            "birth_date" to birthDate,
            "eye_colour" to eyeColour,
            "given_name" to givenName,
            "issue_date" to issuanceDate,
            "age_over_18" to ageOver18,
            "age_over_21" to ageOver21,
            "birth_place" to birthPlace,
            "expiry_date" to expiryDate,
            "family_name" to familyName,
            "hair_colour" to hairColour,
            "nationality" to nationality,
            "age_in_years" to ageInYears,
            "resident_city" to residentCity,
            "age_birth_year" to ageBirthYear,
            "resident_state" to residentState,
            "document_number" to documentNumber,
            "issuing_country" to issuingCountry,
            "resident_address" to residentAddress,
            "resident_country" to residentCountry,
            "issuing_authority" to issuingAuthority,
            "driving_privileges" to drivingPrivileges,
            "issuing_jurisdiction" to issuingJurisdiction,
            "resident_postal_code" to residentPostalCode,
            "signature_usual_mark" to signatureUsualMark,
            "administrative_number" to administrativeNumber,
            "portrait_capture_date" to portraitCaptureDate,
            "un_distinguishing_sign" to unDistinguishingSign,
            "given_name_national_character" to givenNameNationalCharacter,
            "family_name_national_character" to familyNameNationalCharacter
        )
    }
}