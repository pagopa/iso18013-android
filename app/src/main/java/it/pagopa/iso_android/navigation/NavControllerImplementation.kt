package it.pagopa.iso_android.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.pagopa.iso_android.MainActivity
import it.pagopa.iso_android.ui.view.CborView
import it.pagopa.iso_android.ui.view.DocumentStorageView
import it.pagopa.iso_android.ui.view.HomeView
import it.pagopa.iso_android.ui.view.MasterView
import it.pagopa.iso_android.ui.view.SignAndVerifyView
import it.pagopa.iso_android.ui.view.SlaveView

private const val AnimDurationMillis = 700

@Composable
fun MainActivity.IsoAndroidPocNavHost(
    navController: NavHostController,
    showMenu: MutableState<Boolean>,
    innerPadding: PaddingValues,
    topBarImage: MutableState<ImageVector>
) {
    NavHost(
        navController = navController,
        modifier = Modifier.padding(innerPadding),
        startDestination = Home
    ) {
        composable<Home>(exitTransition = {
            return@composable slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(AnimDurationMillis)
            )
        }, popEnterTransition = {
            return@composable slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(AnimDurationMillis)
            )
        }) {
            topBarImage.value = Icons.Default.Menu
            HomeView(onBack = {
                backLogic(showMenu) {
                    this@IsoAndroidPocNavHost.finishAndRemoveTask()
                }
            }, onNavigate = { destination ->
                navController.navigateIfDifferent(destination)
            })
        }
        customAnimatedComposable<HomeDestination.Master> {
            topBarImage.value = Icons.AutoMirrored.Filled.ArrowBack
            MasterView(onBack = {
                backLogic(showMenu) {
                    navController.popBackStack()
                }
            })
        }
        customAnimatedComposable<HomeDestination.Slave> {
            topBarImage.value = Icons.AutoMirrored.Filled.ArrowBack
            SlaveView(onBack = {
                backLogic(showMenu) {
                    navController.popBackStack()
                }
            })
        }
        customAnimatedComposable<HomeDestination.ReadDocument> {
            topBarImage.value = Icons.AutoMirrored.Filled.ArrowBack
            CborView(onBack = {
                backLogic(showMenu) {
                    navController.popToHome()
                }
            })
        }
        customAnimatedComposable<HomeDestination.SignAndVerify> {
            topBarImage.value = Icons.AutoMirrored.Filled.ArrowBack
            SignAndVerifyView(onBack = {
                backLogic(showMenu) {
                    navController.popToHome()
                }
            })
        }
        customAnimatedComposable<HomeDestination.DocumentStorage> {
            topBarImage.value = Icons.AutoMirrored.Filled.ArrowBack
            DocumentStorageView(onBack = {
                backLogic(showMenu) {
                    navController.popToHome()
                }
            })
        }
    }
}

/**It removes menu if visible else will go back to destination passed in action*/
fun backLogic(showMenu: MutableState<Boolean>, action: () -> Unit) {
    if (showMenu.value)
        showMenu.value = false
    else
        action.invoke()
}

/**
 * Moves to an other destination if the current is not the same
 **/
fun NavController.navigateIfDifferent(to: HomeDestination) {
    if (currentBackStackEntry?.destination?.route != to.javaClass.canonicalName)
        navigate(to)
}

/**
 * Directly back to Home destination
 *  @return true if the stack was popped at least once and the user has been navigated to another
 *   destination, false otherwise
 */
fun NavController.popToHome() = this.popBackStack(Home, false)

private inline fun <reified T : Any> NavGraphBuilder.customAnimatedComposable(
    crossinline content: @Composable () -> Unit
) = composable<T>(enterTransition = {
    return@composable slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start, tween(AnimDurationMillis)
    )
}, popEnterTransition = {
    return@composable slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(AnimDurationMillis)
    )
}, popExitTransition = {
    return@composable slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End, tween(AnimDurationMillis)
    )
}) {
    content()
}