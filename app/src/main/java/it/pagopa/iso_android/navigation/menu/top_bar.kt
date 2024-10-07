package it.pagopa.iso_android.navigation.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    titleResId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val whatIs = stringResource(id = titleResId)
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                Text(text = whatIs)
            },
            navigationIcon = {
                Icon(
                    imageVector = Icons.Default.Menu,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable {
                            onClick.invoke()
                        },
                    contentDescription = whatIs
                )
            }
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}