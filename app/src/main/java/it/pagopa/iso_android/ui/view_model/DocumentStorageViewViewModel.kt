package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentWithIdentifierNotFound
import it.pagopa.cbor_implementation.document_manager.LibIso18013DAOException
import it.pagopa.cbor_implementation.document_manager.algorithm.Algorithm
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.impl.MDoc
import it.pagopa.cbor_implementation.model.EU_PID_DOCTYPE
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import it.pagopa.iso_android.base64mockedMoreDocs
import it.pagopa.iso_android.ui.AppDialog
import kotlin.io.encoding.ExperimentalEncodingApi

class DocumentStorageViewViewModel(
    private val documentManager: DocumentManager
) : ViewModel() {
    val actions = listOf<String>(
        "create MDL document",//0
        "create Eu pid document",//1
        "get document",//2
        "store document",//3
        "delete document",//4
        "get all documents",//5
        "get all MDL documents",//6
        "get all stored documents"//7
    )
    var appDialog = mutableStateOf<AppDialog?>(null)
    private var document: Document? = null
    fun manageActions(index: Int) {
        when (index) {
            0 -> document = createDocument(true)
            1 -> document = createDocument(false)
            2 -> document?.let { getDocument(it.id) }
            3 -> document?.let { storeDocument(it.id) }
            4 -> document?.let { deleteDocument(it.id) }
            5 -> getAllDocuments()
            6 -> getAllMdlDocuments()
            7 -> getAllStoredDocuments()
        }
    }

    private fun appDialogWithOkBtn(title: String, message: String) {
        appDialog.value = AppDialog(
            title = title,
            description = message,
            button = AppDialog.DialogButton("ok") {
                appDialog.value = null
            }
        )
    }

    private fun createDocument(isMdl: Boolean): Document {
        val back = documentManager.createDocument(
            docType = if (isMdl) MDL_DOCTYPE else EU_PID_DOCTYPE,
            documentName = "MDL",
            forceStrongBox = false,
            algorithm = Algorithm.SupportedAlgorithms.SHA256_WITH_ECD_SA,
            attestationChallenge = null
        )
        appDialogWithOkBtn(
            title = "Doc created",
            message = "id: ${back.id}"
        )
        return back
    }

    private fun getDocument(id: DocumentId) {
        try {
            val doc = documentManager.getDocumentByIdentifier(id)
            appDialogWithOkBtn("doc retrieved", "id: ${doc.id}")
        } catch (e: DocumentWithIdentifierNotFound) {
            appDialogWithOkBtn("Exception", e.message)
        }
    }

    private fun getAllDocuments() {
        val docs = documentManager.getAllDocuments(null)
        val sb = StringBuilder()
        docs.forEachIndexed { i, each ->
            sb.append("doc n° $i:\n")
            sb.append(each.id)
        }
        appDialogWithOkBtn("Documents", sb.toString())
    }

    private fun getAllMdlDocuments() {
        val docs = documentManager.getAllMdlDocuments(null)
        val sb = StringBuilder()
        docs.forEachIndexed { i, each ->
            sb.append("doc n° $i:\n")
            sb.append(each.id)
        }
        appDialogWithOkBtn("Documents", sb.toString())
    }

    private fun deleteDocument(id: DocumentId) {
        try {
            val back = documentManager.deleteDocument(id)
            appDialogWithOkBtn("doc", if (back) "eliminated" else "fail to eliminate")
        } catch (e: DocumentWithIdentifierNotFound) {
            appDialogWithOkBtn("Exception", e.message)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun storeDocument(id: DocumentId) {
        try {
            val mDoc= MDoc(base64mockedMoreDocs)
            mDoc.decodeMDoc(onComplete = {model->
                model.documents?.forEach { document->
                    document.issuerSigned?.issuerAuth?.let {
                        val docId = documentManager.storeDocument(id, it)
                        appDialogWithOkBtn("Doc stored!!", "ID: $docId")
                    }
                }
            }, onError = {
                appDialogWithOkBtn("Exception", it.message.orEmpty())
            })
        } catch (e: LibIso18013DAOException) {
            appDialogWithOkBtn("Exception", e.message.orEmpty())
        }
    }

    private fun getAllStoredDocuments() {
        val docs = documentManager.getAllDocuments(Document.State.ISSUED)
        val sb = StringBuilder()
        docs.forEachIndexed { i, each ->
            sb.append("doc n° $i:\n")
            sb.append(each.id)
        }
        appDialogWithOkBtn("Documents", sb.toString())
    }
}