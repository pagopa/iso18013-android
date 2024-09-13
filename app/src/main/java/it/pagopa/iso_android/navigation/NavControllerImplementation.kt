package it.pagopa.iso_android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import it.pagopa.iso_android.MainActivity
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.view.HomeView

@Composable
fun MainActivity.IsoAndroidPocNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        modifier = Modifier.padding(innerPadding),
        startDestination = Home,
    ) {
        composable<Home> {
            HomeView(onBack = {
                this@IsoAndroidPocNavHost.finishAndRemoveTask()
            }, onNavigate = {
                navController.navigate(SecondScreen(1))
            })
        }
        composable<SecondScreen> {
            val argument = it.toRoute<SecondScreen>()
            CenteredComposable(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                BigText(
                    modifier = Modifier.wrapContentSize(),
                    text = "Second Screen with id",
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                MediumText(
                    modifier = Modifier.wrapContentSize(),
                    text = "Id: ${argument.id}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}