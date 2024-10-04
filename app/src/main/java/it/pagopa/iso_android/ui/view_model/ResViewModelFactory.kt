package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
inline fun <reified VM : ViewModel> viewModelWithResources(res: Resources): VM {
    return viewModel<VM>(factory = ResViewModelFactory<VM>(res))
}

class ResViewModelFactory<out VM : ViewModel>(
    private val res: Resources
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructor = modelClass.getConstructor(Resources::class.java)
        val instance = constructor.newInstance(res) as? VM
        return (instance as? T) ?: run {
            throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        }
    }
}
