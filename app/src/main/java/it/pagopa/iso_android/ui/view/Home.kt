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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.SmallText
import it.pagopa.iso_android.ui.TwoButtonsInARow

@Composable
fun HomeView(
    onBack: () -> Unit,
    onNavigate: (isMaster: Boolean) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted)
            onNavigate.invoke(false)
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
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            onNavigate.invoke(true)
        }, rightBtnText = "do as Slave", rightBtnAction = {
           launcher.launch(Manifest.permission.CAMERA)
        })
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