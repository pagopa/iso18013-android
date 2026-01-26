package it.pagopa.iso_android.ui.view

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CheckResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.pagopa.io.wallet.proximity.bluetooth.BluetoothUtils
import it.pagopa.io.wallet.proximity.nfc.HceCompatibilityChecker
import it.pagopa.io.wallet.proximity.nfc.NfcChecks
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.iso_android.R
import it.pagopa.iso_android.navigation.HomeDestination
import it.pagopa.iso_android.navigation.getActivity
import it.pagopa.iso_android.nfc.AppNfcEngagementService
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.PopUpMenu
import it.pagopa.iso_android.ui.PopUpMenuItem
import it.pagopa.iso_android.ui.SmallText
import it.pagopa.iso_android.ui.TwoButtonsInARow
import it.pagopa.iso_android.ui.preview.ThemePreviews

@Composable
fun HomeView(
    onBack: () -> Unit,
    onNavigate: (destination: HomeDestination) -> Unit
) {
    @Composable
    fun DeviceInfoDialog(context: Context, destination: HomeDestination, onClick: () -> Unit) {
        val hceStatus = NfcEngagementService.enable(
            context.getActivity()!!,
            AppNfcEngagementService::class.java
        )
        val manufacturer = Build.MANUFACTURER
        val deviceModel = Build.MODEL
        val description =
            "- Device manufacturer: \n$manufacturer\n- Device model: \n${deviceModel}\n- Nfc Status:\n${
                HceCompatibilityChecker.getStatusMessage(hceStatus)
            }\n- Should work:\n${hceStatus.canWork()}"
        GenericDialog(
            AppDialog(
                title = context.resources.getString(R.string.nfc_engagement_service_desc),
                description = description,
                button = AppDialog.DialogButton(
                    context.resources.getString(R.string.ok)
                ) {
                    if (hceStatus.canWork())
                        onNavigate.invoke(destination)
                    else
                        Toast.makeText(
                            context,
                            "Sorry but seems it will not work",
                            Toast.LENGTH_LONG
                        ).show()
                    onClick.invoke()
                }
            ), dismissOnBackPress = false)
    }

    @CheckResult
    fun dialogLogic(destination: HomeDestination): Boolean {
        return destination is HomeDestination.MasterNfc || destination is HomeDestination.MasterNfcExchange
    }

    val context = LocalContext.current
    val whereToGo = remember { mutableStateOf<HomeDestination>(HomeDestination.Master) }
    val dialog = remember { mutableStateOf<AppDialog?>(null) }
    val showPopupMenu = remember { mutableStateOf(false) }
    val shouldShowInfoDialog = remember { mutableStateOf(false) }
    val manyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val granted = permissionsMap.filter {
            it.value
        }
        if (granted.size >= permissionsMap.size - 1)
            shouldShowInfoDialog.value = dialogLogic(whereToGo.value)
        else
            Toast.makeText(
                context,
                "Ho bisogno del permesso al bluetooth per procedere..",
                Toast.LENGTH_LONG
            ).show()
    }

    fun bluetoothCheck(onDone: () -> Unit) {
        if (!BluetoothUtils.isBluetoothEnabled(context))
            Toast.makeText(context, "Prima accendi il bluetooth", Toast.LENGTH_LONG).show()
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                manyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    )
                )
            else
                onDone.invoke()
        }
    }
    BackHandler {
        onBack.invoke()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CenteredComposable(
            modifier = Modifier
                .height(0.dp)
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BigText(
                modifier = Modifier.wrapContentSize(),
                text = "Welcome",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            MediumText(
                modifier = Modifier.wrapContentSize(),
                text = "to",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            SmallText(
                modifier = Modifier.wrapContentSize(),
                text = "Home",
                color = MaterialTheme.colorScheme.primary
            )
        }
        PopUpMenu(showPopupMenu, listOf(PopUpMenuItem("Qr code") {
            whereToGo.value = HomeDestination.Master
            bluetoothCheck {
                onNavigate.invoke(HomeDestination.Master)
            }
        }, PopUpMenuItem("Nfc") {
            val nfcChecks = NfcChecks(context)
            if (!nfcChecks.isNfcAvailable())
                nfcChecks.openNfcSettings()
            else {
                if (nfcChecks.isNfcReadyForEngagement()) {
                    whereToGo.value = HomeDestination.MasterNfc
                    bluetoothCheck {
                        shouldShowInfoDialog.value = true
                        whereToGo.value = HomeDestination.MasterNfc
                    }
                } else {
                    Toast.makeText(
                        context, "Sorry, but your device has not card emulation feature",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, PopUpMenuItem("Only Nfc") {
            val nfcChecks = NfcChecks(context)
            if (!nfcChecks.isNfcAvailable())
                nfcChecks.openNfcSettings()
            else {
                if (nfcChecks.isNfcReadyForEngagement()) {
                    shouldShowInfoDialog.value = true
                    whereToGo.value = HomeDestination.MasterNfcExchange
                } else {
                    Toast.makeText(
                        context, "Sorry, but your device has not card emulation feature",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }))
        TwoButtonsInARow(leftBtnText = "do as Master", leftBtnAction = {
            val nfcChecks = NfcChecks(context)
            if (nfcChecks.hasNfcFeature())
                showPopupMenu.value = true
            else {
                whereToGo.value = HomeDestination.Master
                bluetoothCheck {
                    onNavigate.invoke(HomeDestination.Master)
                }
            }
        }, rightBtnText = "do as Slave", rightBtnAction = {
            whereToGo.value = HomeDestination.Slave
            val permissionList = arrayListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
                permissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            manyPermissionLauncher.launch(permissionList.toTypedArray())
        })
        Spacer(Modifier.height(16.dp))
    }
    if (dialog.value != null)
        GenericDialog(dialog.value!!)
    if (shouldShowInfoDialog.value)
        DeviceInfoDialog(context, whereToGo.value) {
            shouldShowInfoDialog.value = false
        }
}

@ThemePreviews
@Composable
fun PreviewHomeView() {
    BasePreview {
        HomeView(onBack = {

        }, onNavigate = {

        })
    }
}