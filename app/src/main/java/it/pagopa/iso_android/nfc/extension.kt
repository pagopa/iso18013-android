package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.request.DocRequested

fun List<Document>.toDocRequestedArray(alias: String) {
    val back = ArrayList<DocRequested>()
    this.forEach {
        back.add(DocRequested(it.issuerSigned!!.rawValue!!, alias, it.docType!!))
    }
}