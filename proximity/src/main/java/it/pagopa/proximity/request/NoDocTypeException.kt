package it.pagopa.proximity.request

class NoDocTypeException : Exception() {
    override val message: String = "no field docType present"
}