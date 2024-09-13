package it.pagopa.iso_android.ui.view

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.SmallText

@Composable
fun HomeView(onBack: () -> Unit, onNavigate: () -> Unit) {
    BackHandler {
        onBack.invoke()
    }
    CenteredComposable(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BigText(
            modifier = Modifier.wrapContentSize(),
            text = "Welcome",
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        SmallText(
            modifier = Modifier.wrapContentSize(),
            text = "Home",
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(onClick = onNavigate)
        ) {
            MediumText(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.Center)
                    .padding(vertical = 8.dp),
                text = "Tap to navigate",
                color = MaterialTheme.colorScheme.secondary
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