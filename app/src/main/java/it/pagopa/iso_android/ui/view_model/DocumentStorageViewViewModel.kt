package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.document_manager.DocManager
import it.pagopa.cbor_implementation.document_manager.DocumentWithIdentifierNotFound
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.impl.MDoc
import it.pagopa.cbor_implementation.model.Document
import it.pagopa.cbor_implementation.model.EU_PID_DOCTYPE
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import it.pagopa.iso_android.base64mockedMoreDocs
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.iso_android.ui.model.Actions
import it.pagopa.iso_android.ui.model.ActionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class DocumentStorageViewViewModel(
    private val libDao: DocManager?
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
            Actions.DELETE_ALL -> deleteAllDocs()
            Actions.GET_ALL_DOCS -> {
                if (action.isShowingList()) resetAll() else {
                    getAllDocuments(onOk = { docs ->
                        this.documents = docs.toTypedArray()
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
        }
    }

    private fun deleteAllDocs() {
        this.loader.value = "DELETING.."
        viewModelScope.launch(Dispatchers.IO) {
            libDao?.removeAllDocuments()
            appDialogWithOkBtn("Documents", "DELETED")
            this@DocumentStorageViewViewModel.loader.value = null
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

    private fun createDocument(isMdl: Boolean) {
        resetAllActions()
        this.loader.value = "Creating"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mDoc = MDoc(base64mockedMoreDocs)
                mDoc.decodeMDoc(onComplete = { model ->
                    model.documents?.forEach { document ->
                        if (isMdl) {
                            if (document.docType == MDL_DOCTYPE)
                                libDao?.createDocument(document.docType!!, document.rawValue)
                        } else {
                            if (document.docType == EU_PID_DOCTYPE)
                                libDao?.createDocument(document.docType!!, document.rawValue)
                        }
                    }
                }, onError = {
                    appDialogWithOkBtn("Exception", it.message.orEmpty())
                })
                appDialogWithOkBtn(
                    title = "Doc created",
                    message = "type: ${if (isMdl) MDL_DOCTYPE else EU_PID_DOCTYPE}"
                )
            } catch (e: Exception) {
                appDialogWithOkBtn("Exception", e.message.orEmpty())
            }
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    private fun getAllDocuments(onOk: (List<Document>) -> Unit, onEmptyArray: () -> Unit) {
        this.loader.value = "Loading"
        viewModelScope.launch(Dispatchers.IO) {
            val list = libDao?.gelAllDocuments()
            if (list.isNullOrEmpty())
                onEmptyArray.invoke()
            else
                onOk.invoke(list)
            this@DocumentStorageViewViewModel.loader.value = null
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
                libDao?.gelAllMdlDocuments()
            else
                libDao?.gelAllEuPidDocuments()
            if (docs.isNullOrEmpty())
                onEmptyArray.invoke()
            else
                onOk.invoke(docs.toTypedArray())
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }

    fun deleteDocument(actionCaller: ActionsManager, id: DocumentId?) {
        if (id == null) return
        this.loader.value = "Deleting"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val back = libDao?.deleteDocument(id)
                if (back == true)
                    documents = documents.filter { it.docType != id }.toTypedArray()
                if (documents.isEmpty()) actionCaller.reset()
                appDialogWithOkBtn("doc", if (back == true) "eliminated" else "fail to eliminate")
            } catch (e: DocumentWithIdentifierNotFound) {
                appDialogWithOkBtn("Exception", e.message)
            }
            this@DocumentStorageViewViewModel.loader.value = null
        }
    }
}