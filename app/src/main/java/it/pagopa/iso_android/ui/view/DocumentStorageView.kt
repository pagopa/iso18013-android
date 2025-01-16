package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.LoaderDialog
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.SmallText
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.view_model.DocumentStorageViewViewModel

@Composable
fun DocumentStorageView(
    vm: DocumentStorageViewViewModel?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            vm?.actions?.forEachIndexed { i, each ->
                if (i != vm.actions.size - 1)
                    Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    vm.manageActions(each)
                }) {
                    if (each.couldShowList())
                        Row {
                            Text(each.action.description)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    else
                        Text(each.action.description)
                }
                if (each.hasList.value) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.onPrimary)
                            .border(2.dp, color = MaterialTheme.colorScheme.primary)
                    ) {
                        vm.documents.forEach { document ->
                            SmallText(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                text = "Type: ${document.docType}\n id: ${document.docType}",
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                            ItemDoc(isDelete = true) {
                                vm.deleteDocument(each, document.docType)
                            }
                        }
                    }
                }
            }
        }
    }
    vm?.appDialog?.value?.let { appDialog ->
        GenericDialog(appDialog)
    }
    vm?.let {
        LoaderDialog(it.loader)
    }
}

@Composable
fun ItemDoc(
    isDelete: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        this.OneItem(isDelete, onClick)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RowScope.OneItem(
    isDelete: Boolean,
    onClick: () -> Unit
) {
    val delete = stringResource(R.string.delete_doc)
    val store = stringResource(R.string.store_doc)
    val text = if (isDelete) delete else store
    MediumText(
        modifier = Modifier
            .wrapContentSize(),
        text = text,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(
        modifier = Modifier
            .width(0.dp)
            .weight(1f)
    )
    Icon(
        modifier = Modifier
            .heightIn(0.dp, 56.dp)
            .widthIn(0.dp, 56.dp)
            .clickable(onClick = onClick),
        imageVector = if (isDelete) Icons.Default.Delete
        else
            Icons.Default.Lock,
        tint = if (isDelete) Color.Red else MaterialTheme.colorScheme.primary,
        contentDescription = text
    )
}

@ThemePreviews
@Composable
fun DocStoragePreview() {
    BasePreview {
        DocumentStorageView(
            vm = DocumentStorageViewViewModel(null),
            onBack = {}
        )
    }
}