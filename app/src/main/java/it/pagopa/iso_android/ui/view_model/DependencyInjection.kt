package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
inline fun <reified VM : ViewModel> dependenciesInjectedViewModel(vararg dependencies: Any): VM {
    return viewModel<VM>(factory = DependencyInjectedVm<VM>(dependencies))
}

class DependencyInjectedVm<out VM : ViewModel>(
    private val dependencies: Array<*>
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructor = modelClass.constructors.firstOrNull {
            val paramSize = it.parameterTypes.size
            var cntOk = paramSize
            if (paramSize != dependencies.size)
                throw IllegalArgumentException("Array not filled completely or too much")
            it.parameterTypes.forEachIndexed { i, each ->
                var isResourcesClass = false
                try {
                    dependencies[i]!!.javaClass.getDeclaredConstructor().newInstance() as Resources
                    isResourcesClass = true
                } catch (_: Exception) {
                }
                if (isResourcesClass)
                    cntOk--
                else if (dependencies[i]!!.javaClass == each)
                    cntOk--
            }
            cntOk == 0
        } ?: throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        val instance = constructor.newInstance(*dependencies) as? VM
        return (instance as? T) ?: run {
            throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        }
    }
}