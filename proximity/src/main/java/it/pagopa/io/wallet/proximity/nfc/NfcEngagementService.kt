package it.pagopa.io.wallet.proximity.nfc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.CheckResult
import com.android.identity.util.toHex
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.EngagementListener
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Abstract Nfc engagement service.
 *
 * Implement this class to enable the NFC engagement.
 *
 * ```
 * class MyNfcEngagementService : NfcEngagementService()
 *```
 *
 * then add to application manifest file:
 *
 * ```
 * <service android:name=".MyNfcEngagementService"
 *         android:exported="true"
 *             android:label="@string/nfc_engagement_service_desc"
 *             android:permission="android.permission.BIND_NFC_SERVICE">
 *             <intent-filter>
 *                 <action android:name="android.nfc.action.NDEF_DISCOVERED" />
 *                 <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE"/>
 *             </intent-filter>
 *
 *             <meta-data
 *                 android:name="android.nfc.cardemulation.host_apdu_service"
 *                 android:resource="@xml/nfc_engagement_apdu_service" />
 *  </service>
 *  ```
 *
 * You can enable or disable the NFC device engagement in your app by calling the `enable()`
 * and `disable()` methods of the `NfcEngagementService` class.
 *
 * In the example below, the NFC device engagement is enabled when activity is resumed and disabled
 * when the activity is paused.
 *
 * ```
 * import androidx.appcompat.app.AppCompatActivity
 * import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
 *
 * class MainActivity : AppCompatActivity() {
 *
 *   override fun onResume() {
 *     super.onResume()
 *     NfcEngagementService.enable(this)
 *   }
 *
 *   override fun onPause() {
 *     super.onPause()
 *     NfcEngagementService.disable(this)
 *   }
 * }
 * ```
 * Optionally, in the `enable()` method you can define your class that implements `NfcEngagementService`, e.g.:
 *
 *```
 * NfcEngagementService.enable(this, NfcEngagementServiceImpl::class.java)
 *```
 *
 * This defines the nfc engagement service to be preferred while this activity is in the foreground.
 *
 * @constructor
 */
abstract class NfcEngagementService : HostApduService() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var nfcEngagement: NfcEngagement? = null

        /**
         * Enable NFC engagement
         *
         * @param activity
         */
        @JvmStatic
        @CheckResult
        fun enable(
            activity: Activity,
            preferredNfcEngSerCls: Class<out NfcEngagementService>? = null,
        ): HceServiceStatus {
            // set preferred Nfc Engagement Service
            return preferredNfcEngSerCls?.let {
                setAsPreferredNfcEngagementService(activity, it)
            } ?: HceServiceStatus.PreferredClassNotSet
        }

        /**
         * Disable NFC engagement
         *
         * @param activity
         */
        @JvmStatic
        fun disable(activity: Activity) {
            nfcEngagement?.nfcEngagementHelper?.close()
            nfcEngagement = null
            unsetAsPreferredNfcEngagementService(activity)
        }

        private fun Int.cardEmulationClearLog() = when (this) {
            CardEmulation.SELECTION_MODE_PREFER_DEFAULT -> "Default"
            CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT -> "Ask if conflict"
            CardEmulation.SELECTION_MODE_ALWAYS_ASK -> "Always ask"
            else -> "Unknown"
        }

        @JvmStatic
        private fun setAsPreferredNfcEngagementService(
            activity: Activity,
            nfcEngagementServiceClass: Class<out NfcEngagementService>,
        ): HceServiceStatus {
            // Check device compatibility first
            if (HceCompatibilityChecker.isKnownProblematicDevice()) {
                ProximityLogger.i(
                    "NfcEngagementService",
                    "Device may have HCE compatibility issues. Proceeding with caution."
                )
            }

            // Perform comprehensive HCE status check
            val hceStatus = HceCompatibilityChecker.checkHceServiceStatus(
                activity.applicationContext,
                nfcEngagementServiceClass as Class<out HostApduService>
            )

            ProximityLogger.i(
                "NfcEngagementService",
                "HCE Status: ${HceCompatibilityChecker.getStatusMessage(hceStatus)}"
            )

            // If service cannot work, return early
            if (!hceStatus.canWork()) {
                ProximityLogger.e(
                    "NfcEngagementService",
                    "HCE service cannot work on this device: ${
                        HceCompatibilityChecker.getStatusMessage(
                            hceStatus
                        )
                    }"
                )
                return hceStatus
            }

            val cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(activity))
            val allowsForeground =
                cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)
            if (allowsForeground) {
                val hceComponentName = ComponentName(
                    activity,
                    nfcEngagementServiceClass,
                )
                cardEmulation.setPreferredService(activity, hceComponentName)
                val selectionMode =
                    cardEmulation.getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER)

                // Use more reliable AID-based check instead of category check
                // as isDefaultServiceForCategory may return false on some devices even when working
                val isNdefAidRegistered = try {
                    cardEmulation.isDefaultServiceForAid(
                        hceComponentName,
                        "D2760000850101"
                    )
                } catch (e: Exception) {
                    ProximityLogger.e(
                        "NfcEngagementService",
                        "Error checking NDEF AID: ${e.message}"
                    )
                    false
                }

                val isMdlAidRegistered = try {
                    cardEmulation.isDefaultServiceForAid(
                        hceComponentName,
                        "A0000002480400"
                    )
                } catch (e: Exception) {
                    ProximityLogger.e(
                        "NfcEngagementService",
                        "Error checking MDL AID: ${e.message}"
                    )
                    false
                }

                val isServiceRegistered = isNdefAidRegistered || isMdlAidRegistered

                ProximityLogger.i(
                    "NfcEngagementService",
                    """HCE Service Configuration:
                        |Selection mode: ${selectionMode.cardEmulationClearLog()}
                        |NDEF AID (D2760000850101): $isNdefAidRegistered
                        |MDL AID (A0000002480400): $isMdlAidRegistered
                        |Service registered: $isServiceRegistered
                    """.trimMargin()
                )
                return hceStatus
            }
            return hceStatus
        }

        @JvmStatic
        private fun unsetAsPreferredNfcEngagementService(activity: Activity) {
            val cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(activity))
            val allowsForeground =
                cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)
            if (allowsForeground) {
                cardEmulation.unsetPreferredService(activity)
            }
        }
    }

    private fun createListeners() {
        nfcEngagement?.withListener(object : EngagementListener {
            override fun onDeviceConnecting() {
                NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Connecting)
            }

            override fun onDeviceConnected(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                ProximityLogger.i(this.javaClass.name, "CONNECTED")
                NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Connected(deviceRetrievalHelper))
            }

            override fun onError(error: Throwable) {
                NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Error(error))
            }

            override fun onDocumentRequestReceived(
                request: String?,
                sessionsTranscript: ByteArray
            ) {
                NfcEngagementEventBus.tryEmit(
                    NfcEngagementEvent.DocumentRequestReceived(
                        request,
                        sessionsTranscript
                    )
                )
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                NfcEngagementEventBus.tryEmit(
                    NfcEngagementEvent.Disconnected(
                        transportSpecificTermination
                    )
                )
            }
        })
    }

    private fun buildNfcEngagement(retrievalMethods: List<DeviceRetrievalMethod>) {
        nfcEngagement = NfcEngagement
            .build(
                this@NfcEngagementService.baseContext,
                retrievalMethods
            ) {
                this.deactivateAll()
            }
            .configure()
        createListeners()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            NfcEngagementEventBus.internalEvent.collectLatest { event ->
                when (event) {
                    is ServiceEvents.SetupReady -> {
                        buildNfcEngagement(event.retrievalMethods)
                        val readerTrustStore = event.readerTrustStore
                        readerTrustStore?.firstOrNull()?.let { list ->
                            list.firstOrNull()?.let {
                                when (it) {
                                    is Int -> nfcEngagement?.withReaderTrustStore(readerTrustStore as List<List<Int>>)
                                    is ByteArray -> nfcEngagement?.withReaderTrustStore(
                                        readerTrustStore as List<List<ByteArray>>
                                    )

                                    is String -> nfcEngagement?.withReaderTrustStore(
                                        readerTrustStore as List<List<String>>
                                    )

                                    else -> throw Exception("readerTrustStore type not supported")
                                }
                            } ?: run {
                                throw Exception("readerTrustStore type not supported")
                            }
                        }
                        event.documents?.let {
                            nfcEngagement
                                ?.nfcEngagementHelper
                                ?.withDocs(event.documents.toTypedArray())
                        }
                        event.alias?.let {
                            nfcEngagement
                                ?.nfcEngagementHelper
                                ?.withAlias(event.alias)
                        }
                    }

                    is ServiceEvents.QrCodeDeviceEngagement -> nfcEngagement
                        ?.nfcEngagementHelper
                        ?.deviceEngagementFromQr(
                            event.deviceEngagementSetup.first
                        )?.setKeyFromQr(
                            event.deviceEngagementSetup.second
                        )

                    else -> Unit
                }
            }
        }
    }

    private fun deactivateAll() {
        if (nfcEngagement != null) {
            nfcEngagement?.nfcEngagementHelper?.resetAll()
            nfcEngagement?.close()
        }
    }

    override fun onDeactivated(reason: Int) {
        if (nfcEngagement != null) {
            nfcEngagement?.nfcEngagementHelper?.nfcOnDeactivated(reason)
            val timeoutSeconds = 15
            Handler(Looper.getMainLooper()).postDelayed({
                nfcEngagement?.close()
            }, timeoutSeconds * 1000L)
        }
    }

    /**
     * Processes incoming NFC APDU commands.
     */
    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? {
        ProximityLogger.i("NfcEngagementService", "processCommandApdu: ${commandApdu.toHex()}")
        val back = if (nfcEngagement != null)
            nfcEngagement?.nfcEngagementHelper?.nfcProcessCommandApdu(commandApdu)
        else {
            null
        }
        ProximityLogger.i("GIVING BACK", back?.toHex() ?: "null")
        return back
    }
}