package it.pagopa.iso_android.navigation.menu

import it.pagopa.iso_android.R
import it.pagopa.iso_android.navigation.HomeDestination

data class MenuItem(
    val id: HomeDestination,
    val textId: Int
)

val drawerScreens = listOf(
    MenuItem(HomeDestination.SignAndVerify, R.string.sign_and_verify),
    MenuItem(HomeDestination.ReadDocument, R.string.read_document)
)
