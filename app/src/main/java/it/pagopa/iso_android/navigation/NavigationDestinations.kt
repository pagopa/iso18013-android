package it.pagopa.iso_android.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

sealed class HomeDestination {
    @Serializable
    data object Master : HomeDestination()

    @Serializable
    data object Slave : HomeDestination()

    @Serializable
    data object SignAndVerify : HomeDestination()

    @Serializable
    data object ReadDocument : HomeDestination()
}