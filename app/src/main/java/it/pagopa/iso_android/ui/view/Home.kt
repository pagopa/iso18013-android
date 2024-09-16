package it.pagopa.iso_android.ui.view

import android.Manifest
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.cbor_implementation.impl.asCbor
import it.pagopa.cbor_implementation.impl.fromCborTo
import it.pagopa.iso_android.navigation.HomeDestination
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.SmallText
import it.pagopa.iso_android.ui.TwoButtonsInARow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class DriverLicense(
    val name: String,
    val dateOfBirth: String,
    val licenseNumber: String,
    val categories: List<String>
)

@Composable
fun HomeView(
    onBack: () -> Unit,
    onNavigate: (destination: HomeDestination) -> Unit
) {
    val context = LocalContext.current
    val dialog = remember { mutableStateOf<AppDialog?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted)
            onNavigate.invoke(HomeDestination.Slave)
        else
            Toast.makeText(
                context,
                "Ho bisogno del permesso alla fotocamera per procedere..",
                Toast.LENGTH_LONG
            ).show()
    }
    BackHandler {
        onBack.invoke()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            dialog cborWith scope
        }) {
            Text("Try cbor")
        }
        CenteredComposable(
            modifier = Modifier
                .height(0.dp)
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BigText(
                modifier = Modifier.wrapContentSize(),
                text = "Welcome",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            MediumText(
                modifier = Modifier.wrapContentSize(),
                text = "to",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            SmallText(
                modifier = Modifier.wrapContentSize(),
                text = "Home",
                color = MaterialTheme.colorScheme.primary
            )
        }
        TwoButtonsInARow(leftBtnText = "do as Master", leftBtnAction = {
            onNavigate.invoke(HomeDestination.Master)
        }, rightBtnText = "do as Slave", rightBtnAction = {
            launcher.launch(Manifest.permission.CAMERA)
        })
        Spacer(Modifier.height(16.dp))
    }
    if (dialog.value != null)
        GenericDialog(dialog.value!!)
}

private infix fun MutableState<AppDialog?>.cborWith(scope: CoroutineScope) {
    val license = DriverLicense(
        name = "John Doe",
        dateOfBirth = "1990-01-01",
        licenseNumber = "123456789",
        categories = listOf("A", "B", "C")
    )
    scope.launch {
        license.asCbor { byteArray ->
            this@cborWith.value = AppDialog(
                "Cbor encoded",
                description = byteArray.joinToString(", "),
                button = AppDialog.DialogButton(
                    text = "decode it!",
                    onClick = {
                        this@cborWith.value = null
                        scope.launch {
                            byteArray.fromCborTo<DriverLicense> { classDecoded ->
                                this@cborWith.value = AppDialog(
                                    title = "Cbor decoded",
                                    description = classDecoded.toString(),
                                    button = AppDialog.DialogButton(
                                        text = "Fantastic",
                                        onClick = {
                                            this@cborWith.value = null
                                        }
                                    )
                                )
                            }
                        }
                    }
                )
            )
        }
    }
}

@Preview
@Composable
fun PreviewHomeView() {
    BasePreview {
        HomeView(onBack = {

        }, onNavigate = {

        })
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun PreviewHomeViewNight() {
    BasePreview {
        HomeView(onBack = {

        }, onNavigate = {

        })
    }
}