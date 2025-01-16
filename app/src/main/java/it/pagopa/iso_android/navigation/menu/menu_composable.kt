package it.pagopa.iso_android.navigation.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.BuildConfig
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.MediumText

@Composable
fun DrawerItem(
    modifier: Modifier,
    menuItem: MenuItem,
    onItemClick: (MenuItem) -> Unit
) {
    Column(
        modifier = modifier
            .wrapContentSize()
            .clickable {
                onItemClick(menuItem)
            }
    ) {
        MediumText(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    onItemClick(menuItem)
                }
                .padding(all = 10.dp),
            text = stringResource(id = menuItem.textId),
            color = MaterialTheme.colorScheme.background
        )
    }
}

@Composable
fun DrawerBody(
    menuItems: List<MenuItem>,
    onItemClick: (MenuItem) -> Unit,
    onOutsideClick: () -> Unit
) {
    val localDensity = LocalDensity.current
    var columnWidth = remember { mutableStateOf<Dp?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSourceLc = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onOutsideClick.invoke()
            }
    ) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxHeight()
                .wrapContentWidth()
                .clickable(
                    interactionSource = interactionSourceLc,
                    indication = null
                ) {
                }
                .shadow(1.dp)
                .semantics { disabled() }
                .onGloballyPositioned {
                    columnWidth.value = with(localDensity) {
                        it.size.width.toDp()
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            menuItems.forEachIndexed { i, item ->
                if (i > 0)
                    Spacer(Modifier.height(8.dp))
                DrawerItem(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (i > 0) 8.dp else 16.dp,
                        bottom = if (i == menuItems.size - 1) 16.dp else 0.dp
                    ),
                    menuItem = item
                ) {
                    onItemClick(item)
                }
            }
            Spacer(
                modifier = Modifier
                    .height(0.dp)
                    .weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(columnWidth.value ?: 0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .heightIn(0.dp, 56.dp)
                            .widthIn(0.dp, 56.dp),
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    MediumText(
                        modifier = Modifier.wrapContentSize(),
                        text = "Ver n.: ${BuildConfig.appVersion}",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview
@Composable
fun TopBarOpenedPreview() {
    BasePreview {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.onBackground
                )
        ) {
            DrawerBody(
                menuItems = drawerScreens, onItemClick = {}) { }
        }
    }
}