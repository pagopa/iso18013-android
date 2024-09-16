package it.pagopa.iso_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import it.pagopa.cbor_implementation.CborLogger
import it.pagopa.iso_android.navigation.IsoAndroidPocNavHost
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    this.IsoAndroidPocNavHost(navController, innerPadding)
                }
            }
        }
    }
}