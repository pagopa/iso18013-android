package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import it.pagopa.iso_android.ui.model.Actions
import it.pagopa.iso_android.ui.model.ActionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.encoding.ExperimentalEncodingApi


class DocumentStorageViewViewModel(
    private val documentManager: DocumentManager
) : ViewModel() {
    val loader = mutableStateOf<String?>(null)
    val actions = Actions.entries.toTypedArray().map { ActionsManager(it) }
    var appDialog = mutableStateOf<AppDialog?>(null)
    var documents = arrayOf<Document>()

    private fun resetAllActions() {
        this@DocumentStorageViewViewModel.actions.forEach {
            it.reset()
        }
    }

    private fun ActionsManager.showListAndResetOthers() {
        this@DocumentStorageViewViewModel.actions.forEach {
            if (it.action != this.action)
                it.reset()
            else
                it.showList()
        }
    }

    private fun resetAll() {
        this@DocumentStorageViewViewModel.actions.forEach {
            it.reset()
        }
    }

    fun manageActions(action: ActionsManager) {
        fun getTypedDocs(isMdl: Boolean) {
            getAllTypeDocuments(isMdl = isMdl,
                onOk = { docs ->
                    this.documents = docs
                    action.showListAndResetOthers()
                }, onEmptyArray = {
                    resetAll()
                    appDialogWithOkBtn("Empty", "No doc found")
                })
        }
        when (action.action) {
            Actions.CREATE_DOC -> createDocument(isMdl = true)
            Actions.CREATE_EU_PID_DOC -> createDocument(isMdl = false)
            Actions.GET_ALL_DOCS -> {
                if (action.isShowingList()) resetAll() else {
                    getAllDocuments(onOk = { docs ->
                        this.documents = docs
                        action.showListAndResetOthers()
                    }, onEmptyArray = {
                        resetAll()
                        appDialogWithOkBtn("Empty", "No doc found")
                    })
                }
            }

            Actions.GET_ALL_MDL_DOCS -> {
                if (action.isShowingList()) resetAll() else {
                    getTypedDocs(isMdl = true)
                }
            }

            Actions.GET_ALL_EU_PID_DOCS -> {
                if (action.isShowingList()) resetAll() else {
                    getTypedDocs(isMdl = false)
                }
            }

            Actions.GET_ALL_STORED_DOCS -> {
                if (action.isShowingList()) resetAll() else {
                    getAllStoredDocuments(onOk = { docs ->
                        this.documents = docs
                        action.showListAndResetOthers()
                    }, onEmptyArray = {
                        resetAll()
                        appDialogWithOkBtn("Empty", "No doc found")
                    })
                }
            }
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

    private fun createDocument(isMdl: Boolean){
        resetAllActions()
        this.loader.value = "Creating"
        viewModelScope.launch(Dispatchers.IO) {
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
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    private fun getAllDocuments(onOk: (Array<Document>) -> Unit, onEmptyArray: () -> Unit) {
        this.loader.value = "Loading"
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentManager.getAllDocuments(null)
            if (docs.isEmpty())
                onEmptyArray.invoke()
            else
                onOk.invoke(docs)
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    private fun getAllTypeDocuments(
        isMdl: Boolean = true,
        onOk: (Array<Document>) -> Unit,
        onEmptyArray: () -> Unit
    ) {
        this.loader.value = "Loading"
        viewModelScope.launch(Dispatchers.IO) {
            val docs = if (isMdl)
                documentManager.getAllMdlDocuments(null)
            else
                documentManager.getAllEuPidDocuments(null)
            if (docs.isEmpty())
                onEmptyArray.invoke()
            else
                onOk.invoke(docs)
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    fun deleteDocument(actionCaller: ActionsManager, id: DocumentId) {
        this.loader.value = "Deleting"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val back = documentManager.deleteDocument(id)
                if (back)
                    documents = documents.filter { it.id != id }.toTypedArray()
                if (documents.isEmpty()) actionCaller.reset()
                appDialogWithOkBtn("doc", if (back) "eliminated" else "fail to eliminate")
            } catch (e: DocumentWithIdentifierNotFound) {
                appDialogWithOkBtn("Exception", e.message)
            }
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun storeDocument(doc: Document, onOk: () -> Unit) {
        this.loader.value = "Storing"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mDoc = MDoc(base64mockedMoreDocs)
                mDoc.decodeMDoc(onComplete = { model ->
                    model.documents?.forEach { document ->
                        if (document.docType == doc.docType) {
                            document.issuerSigned?.rawValue?.let {
                                val docId = documentManager.storeDocument(doc.id, it)
                                appDialogWithOkBtn("Doc stored!!", "ID: $docId")
                                onOk.invoke()
                            }
                        }
                    }
                }, onError = {
                    appDialogWithOkBtn("Exception", it.message.orEmpty())
                })
            } catch (e: LibIso18013DAOException) {
                appDialogWithOkBtn("Exception", e.message.orEmpty())
            }
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    private fun getAllStoredDocuments(
        onOk: (Array<Document>) -> Unit,
        onEmptyArray: () -> Unit
    ) {
        this.loader.value = "Loading"
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentManager.getAllDocuments(Document.State.ISSUED)
            if (docs.isEmpty())
                onEmptyArray.invoke()
            else
                onOk.invoke(docs)
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }
}