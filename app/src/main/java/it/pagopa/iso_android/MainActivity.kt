package it.pagopa.iso_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.rememberNavController
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.iso_android.navigation.IsoAndroidPocNavHost
import it.pagopa.iso_android.navigation.menu.DrawerBody
import it.pagopa.iso_android.navigation.menu.TopBar
import it.pagopa.iso_android.navigation.menu.drawerScreens
import it.pagopa.iso_android.navigation.navigateIfDifferent
import it.pagopa.iso_android.ui.theme.IsoAndroidPocTheme
import it.pagopa.proximity.ProximityLogger


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProximityLogger.enabled = BuildConfig.DEBUG
        CborLogger.enabled = BuildConfig.DEBUG
        setContent {
            IsoAndroidPocTheme {
                var topBarImage = remember { mutableStateOf<ImageVector>(Icons.Default.Menu) }
                var showMenu = remember { mutableStateOf(false) }
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize(),
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
                        enter = slideInHorizontally(),
                        exit = slideOutHorizontally()
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
    }
}