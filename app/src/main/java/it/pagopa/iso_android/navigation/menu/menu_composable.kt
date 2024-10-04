package it.pagopa.iso_android.navigation.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val interactionSource = remember { MutableInteractionSource() }
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
        LazyColumn(
            Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            itemsIndexed(menuItems) { i, item ->
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