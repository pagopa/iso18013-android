package it.pagopa.iso_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.rememberNavController
import it.pagopa.io.wallet.cbor.CborLogger
import it.pagopa.io.wallet.proximity.KindOfLog
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.iso_android.navigation.IsoAndroidPocNavHost
import it.pagopa.iso_android.navigation.menu.DrawerBody
import it.pagopa.iso_android.navigation.menu.TopBar
import it.pagopa.iso_android.navigation.menu.drawerScreens
import it.pagopa.iso_android.navigation.navigateIfDifferent
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.theme.IsoAndroidPocTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProximityLogger.enabled = BuildConfig.DEBUG
        CborLogger.enabled = BuildConfig.DEBUG
        setContent {
            this.MainApp()
        }
    }
}

@Composable
private fun MainActivity?.MainApp(showMenuPreview: MutableState<Boolean>? = null) {
    IsoAndroidPocTheme {
        val topBarImage = remember { mutableStateOf(Icons.Default.Menu) }
        val showMenu = showMenuPreview?.let {
            remember { it }
        } ?: run {
            mutableStateOf(false)
        }
        val navController = rememberNavController()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(
                    image = topBarImage.value,
                    titleResId = R.string.app_name
                ) {
                    if (topBarImage.value == Icons.Default.Menu)
                        showMenu.value = !showMenu.value
                    else
                        navController.popBackStack()
                }
            }
        ) { innerPadding ->
            this.IsoAndroidPocNavHost(navController, showMenu, innerPadding, topBarImage)
            AnimatedVisibility(
                modifier = Modifier
                    .padding(innerPadding),
                visible = showMenu.value,
                enter = slideInHorizontally(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        visibilityThreshold = IntOffset.VisibilityThreshold
                    ), initialOffsetX = { -it / 2 }),
                exit = slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = 1000,
                        easing = FastOutSlowInEasing
                    ), targetOffsetX = { -it })
            ) {
                DrawerBody(
                    menuItems = drawerScreens,
                    onItemClick = { menuItem ->
                        showMenu.value = false
                        navController.navigateIfDifferent(menuItem.id)
                    }) {
                    showMenu.value = false
                }
            }
        }
    }
}

@ThemePreviews
@Composable
fun MainAppPreview() {
    null.MainApp()
}


@ThemePreviews
@Composable
fun MainAppPreviewWithMenu() {
    val showMenu = remember { mutableStateOf(true) }
    null.MainApp(showMenu)
}