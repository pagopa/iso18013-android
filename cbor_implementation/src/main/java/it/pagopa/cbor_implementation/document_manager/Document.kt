package it.pagopa.cbor_implementation.document_manager

import java.time.Instant
import com.android.identity.document.Document as BaseDocument


typealias DocumentId = String
typealias NameSpace = String
typealias ElementIdentifier = String

/**
 * A document.
 * @property id the identifier of the document
 * @property docType the document type
 * @property name the name of the document
 * @property usesStrongBox whether the document's keys are in strongBox
 * @property requiresUserAuth whether the document requires user authentication
 * @property createdAt the creation date of the document
 * @property state the state of the document
 * @property isUnsigned whether the document is unsigned
 * @property isDeferred whether the document is deferred
 * @property isIssued whether the document is issued
 */
sealed interface Document {
    val id: DocumentId
    val docType: String
    val name: String
    val usesStrongBox: Boolean
    val requiresUserAuth: Boolean
    val createdAt: Instant
    val state: State

    val isUnsigned: Boolean
        get() = state == State.UNSIGNED

    val isDeferred: Boolean
        get() = state ==  State.DEFERRED

    val isIssued: Boolean
        get() = state ==  State.ISSUED

    /**
     * The state of the document.
     * @property UNSIGNED the document is unsigned
     * @property ISSUED the document is issued
     * @property DEFERRED the document is deferred
     */
    enum class State {
        UNSIGNED, ISSUED, DEFERRED;

        val value
            get() = ordinal.toLong()
    }

    companion object {

        internal operator fun invoke(baseDocument: BaseDocument) = when (baseDocument.state) {
            State.UNSIGNED -> UnsignedDocument(baseDocument)
            State.DEFERRED -> DeferredDocument(baseDocument)
            State.ISSUED -> IssuedDocument(baseDocument)
        }
    }
}