package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.cbor_implementation.cose.COSEManager
import it.pagopa.cbor_implementation.document_manager.DocumentManager

@Composable
inline fun <reified VM : ViewModel> viewModelWithDocManager(dm: DocumentManager): VM {
    return viewModel<VM>(factory = DocumentManagerViewModelFactory<VM>(dm))
}

class DocumentManagerViewModelFactory<out VM : ViewModel>(
    private val docManager: DocumentManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructor = modelClass.getConstructor(DocumentManager::class.java)
        val instance = constructor.newInstance(docManager) as? VM
        return (instance as? T) ?: run {
            throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> viewModelWithCOSEManager(coseManager: COSEManager): VM {
    return viewModel<VM>(factory = COSEManagerViewModelFactory<VM>(coseManager))
}

class COSEManagerViewModelFactory<out VM : ViewModel>(
    private val coseManager: COSEManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructor = modelClass.getConstructor(COSEManager::class.java)
        val instance = constructor.newInstance(coseManager) as? VM
        return (instance as? T) ?: run {
            throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        }
    }
}