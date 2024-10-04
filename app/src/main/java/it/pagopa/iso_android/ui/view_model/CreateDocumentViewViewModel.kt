package it.pagopa.iso_android.ui.view_model

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.CreateDocumentResult
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult
import it.pagopa.cbor_implementation.model.MDL_DOCTYPE
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.AppDialog

class CreateDocumentViewViewModel(private val res: Resources) : ViewModel() {
    val appDialog = mutableStateOf<AppDialog?>(null)
    val buttonText = mutableStateOf(res.getString(R.string.create_doc))
    private var documentCreated = false
    private var documentManager: DocumentManager? = null
    private var unsignedDocument: UnsignedDocument? = null
    fun manageDocument(
        context: Context
    ) {
        if (documentManager == null) {
            documentManager = DocumentManager.build(
                builder = DocumentManagerBuilder(context)
                    .enableUserAuth(true)
                    .useEncryption(true)
                    .checkPublicKeyBeforeAdding(true)
            )
        }
        if (!documentCreated) {
            documentManager!!.createDocument(
                docType = MDL_DOCTYPE,
                strongBox = true,
                attestationChallenge = byteArrayOf(0x0, 0x0, 0x0, 23.toByte()),
                result = object : CreateDocumentResult {
                    override fun success(unsignedDocument: UnsignedDocument) {
                        buttonText.value = res.getString(R.string.store_doc)
                        unsignedDocument.successAppDialog()
                        this@CreateDocumentViewViewModel.unsignedDocument = unsignedDocument
                        documentCreated = true
                    }

                    override fun failure(throwable: Throwable) {
                        CborLogger.e("Exception during doc creation", "$throwable")
                        failureAppDialog()
                    }
                }
            )
        } else
            unsignedDocument?.storeDocument()
    }

    private fun UnsignedDocument.storeDocument() {
        documentManager!!.storeIssuedDocument(
            unsignedDocument = this,
            issuerDocumentData = byteArrayOf(0x0, 0x0, 0x0, 23.toByte()),
            result = object : StoreDocumentResult {
                override fun success(
                    documentId: DocumentId,
                    proofOfProvisioning: ByteArray?
                ) {
                    buttonText.value = res.getString(R.string.create_doc)
                    documentId successStoreAppDialogWith proofOfProvisioning
                }

                override fun failure(throwable: Throwable) {
                    CborLogger.e("Exception during doc storing", "$throwable")
                    failureAppDialog(isCreation = false)
                }
            }
        )
    }

    private infix fun DocumentId.successStoreAppDialogWith(proofOfProvisioning: ByteArray?) {
        appDialog.value = AppDialog(
            title = "Document stored with id: $this",
            description = "Proof of provisioning byte array: $proofOfProvisioning",
            button = AppDialog.DialogButton(
                text = "Ok",
                onClick = {
                    appDialog.value = null
                }
            )
        )
    }

    private fun UnsignedDocument.successAppDialog() {
        appDialog.value = AppDialog(
            title = "Ok",
            description = "Document created: ${this@successAppDialog}",
            button = AppDialog.DialogButton(
                text = "Ok",
                onClick = {
                    appDialog.value = null
                }
            )
        )
    }

    private fun failureAppDialog(isCreation: Boolean = true) {
        appDialog.value = AppDialog(
            title = "Error",
            description = "Fail to ${if (isCreation) "create" else "store"} document",
            button = AppDialog.DialogButton(
                text = "Ok",
                onClick = {
                    appDialog.value = null
                }
            )
        )
    }
}