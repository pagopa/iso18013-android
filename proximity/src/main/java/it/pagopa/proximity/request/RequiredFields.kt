package it.pagopa.proximity.request

import com.upokecenter.cbor.CBORObject
import it.pagopa.proximity.DocType

abstract class RequiredFields {
    abstract val docType: DocType
    abstract fun toArray(): Array<Pair<Boolean?, String>>

    companion object {
        fun fromCbor(docType: DocType, cbor: CBORObject): RequiredFields {
            return if (docType == DocType.EU_PID)
                RequiredFieldsEuPid.fromCbor(cbor)
            else
                RequiredFieldsMdl.fromCbor(cbor)
        }
    }
}