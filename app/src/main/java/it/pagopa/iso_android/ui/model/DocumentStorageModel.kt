package it.pagopa.iso_android.ui.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.Serializable


data class ActionsManager(
    val action: Actions,
    val hasList: MutableState<Boolean> = mutableStateOf(false)
) : Serializable {
    fun couldShowList(): Boolean {
        return this.action != Actions.CREATE_DOC && this.action != Actions.CREATE_EU_PID_DOC
    }

    fun reset() {
        this.hasList.value = false
    }

    fun showList() {
        this.hasList.value = true
    }

    fun isShowingList() = this.hasList.value
}

enum class Actions(val description: String) {
    CREATE_DOC("create MDL Doc"),
    CREATE_EU_PID_DOC("Create Eu Pid Doc"),
    GET_ALL_DOCS("Get All docs"),
    GET_ALL_MDL_DOCS("Get ALL Mdl Docs"),
    GET_ALL_EU_PID_DOCS("Get all eu pid docs"),
    DELETE_ALL("Delete all docs")
}