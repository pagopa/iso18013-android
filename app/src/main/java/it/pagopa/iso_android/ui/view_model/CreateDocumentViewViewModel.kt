package it.pagopa.iso_android.ui.view_model

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.DocumentId
import it.pagopa.cbor_implementation.document_manager.document.SignedWithCOSEDocument
import it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument
import it.pagopa.cbor_implementation.document_manager.results.DocumentRetrieved
import it.pagopa.cbor_implementation.document_manager.results.IssuerSignedRetriever
import it.pagopa.cbor_implementation.document_manager.results.SignDataWithCOSEResult
import it.pagopa.cbor_implementation.document_manager.results.StoreDocumentResult
import it.pagopa.iso_android.R
import it.pagopa.iso_android.SAMPLE_ISSUER_DS
import it.pagopa.iso_android.SAMPLE_ISSUER_PRIVATE_KEY
import it.pagopa.iso_android.base64mockedMoreDocsNoIssuerAuth
import it.pagopa.iso_android.ui.AppDialog
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CreateDocumentViewViewModel(private val res: Resources) : ViewModel() {
    val switchChecked = mutableStateOf(false)
    var useRetrieveBefore = false
    val appDialog = mutableStateOf<AppDialog?>(null)
    val buttonText = mutableStateOf(res.getString(R.string.sign_doc))
    private var documentCreated = false
    private var documentManager: DocumentManager? = null
    private var signedDocs: List<SignedWithCOSEDocument>? = null

    @OptIn(ExperimentalEncodingApi::class)
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
            createAndSignDoc(documentManager!!)
        } else {
            signedDocs?.forEachIndexed { i, each ->
                CborLogger.i("Storing document n°", "$i")
                each.unsignedDoc.storeDocument(each.data)
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun createAndSignDocWithRetrieve(documentManager: DocumentManager) {
        documentManager.retrieveIssuerDocumentData(
            documentData = Base64.decode(base64mockedMoreDocsNoIssuerAuth),
            object : IssuerSignedRetriever {
                override fun success(issuerDocumentsData: List<DocumentRetrieved>) {
                    documentManager.signWithCOSE(
                        documents = issuerDocumentsData,
                        privateKey = SAMPLE_ISSUER_PRIVATE_KEY,
                        issuerCertificate = SAMPLE_ISSUER_DS,
                        strongBox = true,
                        attestationChallenge = null,
                        object : SignDataWithCOSEResult {
                            override fun success(docs: List<SignedWithCOSEDocument>) {
                                //if(docs[0].unsignedDoc.docType== MDL_DOCTYPE)//this is an example if I just want to sign MDL
                                signedDocs = docs
                                successAppDialog()
                                documentCreated = true
                                buttonText.value = res.getString(R.string.store_doc)
                            }

                            override fun failure(e: Throwable) {
                                CborLogger.e("Exception during sign", "$e")
                                failureAppDialog(isSigning = true)
                            }
                        }
                    )
                }

                override fun failure(throwable: Throwable) {
                    CborLogger.e("Exception during retrieving data", "$throwable")
                    failureAppDialog(isSigning = true)
                }
            }
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun createAndSignDoc(documentManager: DocumentManager) {
        if (this.useRetrieveBefore) {
            createAndSignDocWithRetrieve(documentManager)
        } else {
            documentManager.signWithCOSE(
                data = Base64.decode(base64mockedMoreDocsNoIssuerAuth),
                privateKey = SAMPLE_ISSUER_PRIVATE_KEY,
                issuerCertificate = SAMPLE_ISSUER_DS,
                strongBox = true,
                attestationChallenge = null,
                object : SignDataWithCOSEResult {
                    override fun success(docs: List<SignedWithCOSEDocument>) {
                        //if(docs[0].unsignedDoc.docType== MDL_DOCTYPE)//this is an example if I just want to sign MDL
                        signedDocs = docs
                        successAppDialog()
                        documentCreated = true
                        buttonText.value = res.getString(R.string.store_doc)
                    }

                    override fun failure(e: Throwable) {
                        CborLogger.e("Exception during sign", "$e")
                        failureAppDialog(isSigning = true)
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun UnsignedDocument.storeDocument(data: ByteArray) {
        documentManager!!.storeIssuedDocument(
            unsignedDocument = this@storeDocument,
            issuerDocumentData = data,
            result = object : StoreDocumentResult {
                override fun success(
                    documentId: DocumentId,
                    proofOfProvisioning: ByteArray?
                ) {
                    buttonText.value = res.getString(R.string.sign_doc)
                    documentId successStoreAppDialogWith proofOfProvisioning
                }

                override fun failure(throwable: Throwable) {
                    CborLogger.e("Exception during doc storing", "$throwable")
                    failureAppDialog(isSigning = false)
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

    private fun successAppDialog() {
        appDialog.value = AppDialog(
            title = "Ok",
            description = "Documents signed",
            button = AppDialog.DialogButton(
                text = "Ok",
                onClick = {
                    appDialog.value = null
                }
            )
        )
    }

    private fun failureAppDialog(isSigning: Boolean) {
        appDialog.value = AppDialog(
            title = "Error",
            description = "Fail to ${if (isSigning) "sign" else "store"} document",
            button = AppDialog.DialogButton(
                text = "Ok",
                onClick = {
                    appDialog.value = null
                }
            )
        )
    }
}
