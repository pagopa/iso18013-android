@file:JvmMultifileClass

package it.pagopa.cbor_implementation.document_manager.document

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.document_manager.createdAt
import it.pagopa.cbor_implementation.document_manager.docType
import it.pagopa.cbor_implementation.document_manager.documentName
import it.pagopa.cbor_implementation.document_manager.issuedAt
import it.pagopa.cbor_implementation.document_manager.nameSpacedData
import it.pagopa.cbor_implementation.document_manager.requiresUserAuth
import it.pagopa.cbor_implementation.document_manager.state
import it.pagopa.cbor_implementation.document_manager.usesStrongBox
import it.pagopa.cbor_implementation.helper.parse
import java.time.Instant
import com.android.identity.document.Document as BaseDocument

/**
 * An [IssuedDocument] is a document that has been issued. It contains the data that was issued.
 * To store the [IssuedDocument], use the [it.pagopa.cbor_implementation.document_manager.DocumentManager.storeDocument] method.
 *
 * @property issuedAt document's issuance date
 * @property requiresUserAuth flag that indicates if the document requires user authentication to be accessed
 * @property nameSpacedData retrieves the document's data, grouped by nameSpace. Values are in CBOR bytes
 * @property nameSpaces retrieves the document's nameSpaces and elementIdentifiers
 * @property nameSpacedDataValues retrieves the document's data, grouped by nameSpace. Values are in their original type
 */
data class IssuedDocument(
    override val id: DocumentId,
    override val docType: String,
    override val name: String,
    override val usesStrongBox: Boolean,
    override val requiresUserAuth: Boolean,
    override val createdAt: Instant,
    val issuedAt: Instant,
    val nameSpacedData: Map<NameSpace, Map<ElementIdentifier, ByteArray>>,
) : Document {

    @set:JvmSynthetic
    override var state: Document.State = Document.State.ISSUED
        internal set

    val nameSpaces: Map<NameSpace, List<ElementIdentifier>>
        get() = nameSpacedData.mapValues { it.value.keys.toList() }

    val nameSpacedDataValues: Map<NameSpace, Map<ElementIdentifier, Any?>>
        get() {
            val map = mutableMapOf<String, Map<ElementIdentifier, Any?>>()
            for ((namespace, data) in nameSpacedData) {
                val namespaceMap = mutableMapOf<String, Any?>()
                for ((elementIdentifier, value) in data) {
                    namespaceMap[elementIdentifier] = CBORObject.DecodeFromBytes(value).parse()
                }
                map[namespace] = namespaceMap
            }
            return map.toMap()
        }

    fun getDocumentCborBytes(): ByteArray? {
        return CBORObject.FromObject(this.nameSpacedData)?.EncodeToBytes()
    }

    internal companion object {
        @JvmSynthetic
        operator fun invoke(baseDocument: BaseDocument) = IssuedDocument(
            id = baseDocument.name,
            docType = baseDocument.docType,
            name = baseDocument.documentName,
            usesStrongBox = baseDocument.usesStrongBox,
            requiresUserAuth = baseDocument.requiresUserAuth,
            createdAt = baseDocument.createdAt,
            issuedAt = baseDocument.issuedAt,
            nameSpacedData = baseDocument.nameSpacedData.nameSpaceNames.associateWith { nameSpace ->
                baseDocument.nameSpacedData.getDataElementNames(nameSpace)
                    .associateWith { elementIdentifier ->
                        baseDocument.nameSpacedData.getDataElement(nameSpace, elementIdentifier)
                    }
            }
        ).also { it.state = baseDocument.state }
    }
}

