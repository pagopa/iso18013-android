package it.pagopa.proximity

enum class DocType(
    val value: String,
    val nameSpacesValue: String
) {
    MDL("org.iso.18013.5.1.mDL", "org.iso.18013.5.1"),
    EU_PID("eu.europa.ec.eudi.pid.1", "eu.europa.ec.eudi.pid.1"),
    ANY_OTHER("", "");

    val isAccepted get() = this == MDL || this == EU_PID

    companion object {
        operator fun invoke(string: String?): DocType {
            return when (string) {
                MDL.value -> MDL
                EU_PID.value -> EU_PID
                else -> ANY_OTHER
            }
        }
    }
}
/*
* {
  "org.iso.18013.5.reservation.1" : {
    "guests" : false,
    "location" : false,
    "birth_date" : false,
    "car_rental" : false,
    "given_name" : false,
    "expiry_date" : false,
    "family_name" : false,
    "num_of_rooms" : false,
    "check_in_date" : false,
    "issuance_date" : false,
    "check_out_date" : false,
    "reservation_id" : false,
    "issuing_country" : false,
    "reservation_date" : false,
    "issuing_authority" : false,
    "booking_service_name" : false,
    "service_provider_name" : false
  }
}
*
* "org.iso.23220.photoid.1" : {
    "gender" : false,
    "portrait" : false,
    "person_id" : false,
    "birth_city" : false,
    "birth_date" : false,
    "given_name" : false,
    "age_over_18" : false,
    "birth_place" : false,
    "birth_state" : false,
    "expiry_date" : false,
    "family_name" : false,
    "nationality" : false,
    "age_in_years" : false,
    "birth_country" : false,
    "issuance_date" : false,
    "resident_city" : false,
    "age_birth_year" : false,
    "resident_state" : false,
    "document_number" : false,
    "issuing_country" : false,
    "resident_street" : false,
    "given_name_birth" : false,
    "resident_address" : false,
    "resident_country" : false,
    "family_name_birth" : false,
    "issuing_authority" : false,
    "issuing_jurisdiction" : false,
    "resident_postal_code" : false,
    "administrative_number" : false,
    "portrait_capture_date" : false,
    "resident_house_number" : false,
    "travel_document_number" : false
  }
  * */