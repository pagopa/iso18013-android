package it.pagopa.iso_android.navigation.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    var lazyColumnWidth = remember { mutableStateOf<Dp?>(null) }
    var lazyColumnHeight = remember { mutableStateOf<Dp?>(null) }
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
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned {
                    lazyColumnWidth.value = with(localDensity) {
                        it.size.width.toDp()
                    }
                    lazyColumnHeight.value = with(localDensity) {
                        it.size.height.toDp()
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
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
            item {
                Box(
                    modifier = Modifier
                        .width(lazyColumnWidth.value ?: 0.dp),
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
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        val lineColor = MaterialTheme.colorScheme.onBackground
        if (lazyColumnWidth.value != null && lazyColumnHeight.value != null) {
            Canvas(
                Modifier
                    .width(lazyColumnWidth.value!!)
                    .height(lazyColumnHeight.value!!)
            ) {
                drawLine(
                    color = lineColor,
                    start = Offset(x = size.width, y = 0f),
                    end = Offset(x = size.width, y = size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = lineColor,
                    start = Offset(x = 0f, y = size.height),
                    end = Offset(x = size.width, y = size.height),
                    strokeWidth = 1.dp.toPx()
                )
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