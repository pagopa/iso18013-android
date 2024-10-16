package it.pagopa.proximity.request

object CborValues {
    fun respectRequiredFieldsInstance(instance: RequiredFields): Array<String> {
        return if (instance is RequiredFieldsMdl)
            mdlCborValues
        else
            euPidCborValues
    }

    val euPidCborValues = arrayOf(
        "gender",
        "portrait",
        "birth_city",
        "birth_date",
        "given_name",
        "age_over_13",
        "age_over_16",
        "age_over_18",
        "age_over_21",
        "age_over_60",
        "age_over_65",
        "age_over_68",
        "birth_place",
        "birth_state",
        "expiry_date",
        "family_name",
        "nationality",
        "age_in_years",
        "birth_country",
        "issuance_date",
        "resident_city",
        "age_birth_year",
        "resident_state",
        "document_number",
        "issuing_country",
        "resident_street",
        "given_name_birth",
        "resident_address",
        "resident_country",
        "family_name_birth",
        "issuing_authority",
        "issuing_jurisdiction",
        "resident_postal_code",
        "administrative_number",
        "portrait_capture_date",
        "resident_house_number"
    )
    val mdlCborValues = arrayOf(
        "height",
        "weight",
        "portrait",
        "birth_date",
        "eye_colour",
        "given_name",
        "issue_date",
        "age_over_18",
        "age_over_21",
        "birth_place",
        "expiry_date",
        "family_name",
        "hair_colour",
        "nationality",
        "age_in_years",
        "resident_city",
        "age_birth_year",
        "resident_state",
        "document_number",
        "issuing_country",
        "resident_address",
        "resident_country",
        "issuing_authority",
        "driving_privileges",
        "issuing_jurisdiction",
        "resident_postal_code",
        "signature_usual_mark",
        "administrative_number",
        "portrait_capture_date",
        "un_distinguishing_sign",
        "given_name_national_character",
        "family_name_national_character"
    )
}