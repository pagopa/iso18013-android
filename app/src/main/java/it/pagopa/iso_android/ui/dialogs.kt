package it.pagopa.iso_android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun LoaderDialog(text: MutableState<String?>) {
    text.value?.let { loaderText ->
        Dialog(
            onDismissRequest = { text.value = null },
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp)
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                AnimateDottedText(
                    text = loaderText,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

data class AppDialog(
    val title: String? = null,
    val image: Painter? = null,
    val description: String? = null,
    val button: DialogButton,
    val secondButton: DialogButton? = null
) {
    data class DialogButton(
        val text: String,
        val onClick: () -> Unit
    )
}

@Composable
fun GenericDialog(
    dialog: AppDialog
) {
    Dialog(
        onDismissRequest = {},
        DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        CenteredComposable(
            modifier = Modifier
                .wrapContentSize()
                .verticalScroll(rememberScrollState())
                .background(
                    MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            if (dialog.title != null)
                HorizontallyCenteredText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    kindOfText = KindOfText.BIG,
                    text = dialog.title,
                    textColor = MaterialTheme.colorScheme.onBackground
                )
            if (dialog.image != null)
                Image(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(vertical = 8.dp),
                    painter = dialog.image,
                    contentDescription = null
                )
            if (dialog.description != null)
                HorizontallyCenteredText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    kindOfText = KindOfText.MEDIUM,
                    text = dialog.description,
                    textColor = MaterialTheme.colorScheme.onBackground
                )
            Spacer(modifier = Modifier.height(16.dp))
            if (dialog.secondButton != null) {
                TwoButtonsInARow(
                    leftBtnText = dialog.button.text,
                    leftBtnAction = dialog.button.onClick,
                    rightBtnText = dialog.secondButton.text,
                    rightBtnAction = dialog.secondButton.onClick
                )
            } else {
                Button(onClick = {
                    dialog.button.onClick.invoke()
                }) {
                    Text(text = dialog.button.text)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}