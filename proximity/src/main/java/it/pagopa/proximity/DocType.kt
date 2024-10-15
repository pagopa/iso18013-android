package it.pagopa.proximity

enum class DocType(
    val value: String,
    val nameSpacesValue: String
) {
    MDL("org.iso.18013.5.1.mDL", "org.iso.18013.5.1"),
    EU_PID("eu.europa.ec.eudi.pid.1", "eu.europa.ec.eudi.pid.1");

    companion object {
        fun fromString(string: String?): DocType? {
            return when (string) {
                MDL.value -> MDL
                EU_PID.value -> EU_PID
                else -> null
            }
        }
    }
}