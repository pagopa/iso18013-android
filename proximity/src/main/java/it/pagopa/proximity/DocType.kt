package it.pagopa.proximity

enum class DocType(
    val value: String,
    val nameSpacesValue: String
) {
    MDL("org.iso.18013.5.1.mDL", "org.iso.18013.5.1"),
    EU_PID("eu.europa.ec.eudi.pid.1", "eu.europa.ec.eudi.pid.1"),
    ANY_OTHER("", "");

    companion object {
        fun isAcceptedDocType(docType: DocType?): Boolean {
            return docType == MDL || docType == EU_PID
        }

        fun fromString(string: String?): DocType? {
            return when (string) {
                MDL.value -> MDL
                EU_PID.value -> EU_PID
                null -> null
                else -> ANY_OTHER
            }
        }
    }
}