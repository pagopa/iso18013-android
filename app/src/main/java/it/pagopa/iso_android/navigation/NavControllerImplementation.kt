package it.pagopa.iso_android.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.pagopa.iso_android.MainActivity
import it.pagopa.iso_android.ui.view.HomeView
import it.pagopa.iso_android.ui.view.MasterView
import it.pagopa.iso_android.ui.view.SlaveView

@Composable
fun MainActivity.IsoAndroidPocNavHost(
    navController: NavHostController, innerPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        modifier = Modifier.padding(innerPadding),
        startDestination = Home,
    ) {
        composable<Home>(exitTransition = {
            return@composable slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(700)
            )
        }, popEnterTransition = {
            return@composable slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(700)
            )
        }) {
            HomeView(onBack = {
                this@IsoAndroidPocNavHost.finishAndRemoveTask()
            }, onNavigate = { asMaster ->
                if (!asMaster) {
                    navController.navigate(Slave)
                } else navController.navigate(Master)
            })
        }
        customAnimatedComposable<Master> {
            MasterView(onBack = {
                navController.popBackStack()
            }, onNavigate = {

            })
        }
        customAnimatedComposable<Slave> {
            SlaveView(onBack = {
                navController.popBackStack()
            })
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.customAnimatedComposable(
    crossinline content: @Composable () -> Unit
) = composable<T>(enterTransition = {
    return@composable slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start, tween(700)
    )
}, popExitTransition = {
    return@composable slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End, tween(700)
    )
}) {
    content()
}