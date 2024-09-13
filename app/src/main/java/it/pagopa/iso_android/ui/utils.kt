package it.pagopa.iso_android.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.pagopa.iso_android.ui.theme.IsoAndroidPocTheme

@Composable
fun BasePreview(content: @Composable () -> Unit) {
    IsoAndroidPocTheme {
        content()
    }
}

@Composable
fun BigText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = color
    )
}

@Composable
fun MediumText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = color
    )
}

@Composable
fun SmallText(modifier: Modifier, text: String, color: Color) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
fun CenteredComposable(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
fun LoaderDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TwoButtonsInARow(
    leftBtnText: String,
    leftBtnAction: () -> Unit,
    rightBtnText: String,
    rightBtnAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .weight(1f)
                .width(0.dp)
        ) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = leftBtnAction) {
                Text(text = leftBtnText)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .width(0.dp)
        ) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = rightBtnAction) {
                Text(text = rightBtnText)
            }
        }
    }
}