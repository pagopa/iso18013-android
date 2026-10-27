package it.pagopa.io.wallet.proximity.response

/**
 * A document in a format other than mdoc, as returned in the `otherDocuments` of the
 * `DeviceResponse` defined by the ISO/IEC 18013-5 second edition.
 *
 * @property docFormat the format of the document, e.g. `dc+sd-jwt`
 * @property data the presented document, compressed with zlib (RFC 1950)
 */
class OtherDocument(
    val docFormat: String,
    val data: ByteArray
)
