package it.pagopa.proximity.document

import it.pagopa.proximity.request.RequiredFields

/**
 * Represents a response that contains the document data that will be sent to an mdoc verifier
 *
 * @property documentId the unique id of the document stored in identity credential api
 * @property docType the document type, e.g. eu.europa.ec.eudiw.pid.1
 * @property nameSpaces a [Map] that contains the document nameSpacesData
 * @property doNotSendArray an [Array] of [String] where to pass cbor values to don't share even if requested
 * @constructor Create empty Response document data
 */
data class DisclosedDocument(
    val documentId: String,
    val docType: String,
    val requestedFields: RequiredFields,
    val nameSpaces: Map<String, Map<String, Any?>>,
    val doNotSendArray: Array<String> = arrayOf()
) : java.io.Serializable