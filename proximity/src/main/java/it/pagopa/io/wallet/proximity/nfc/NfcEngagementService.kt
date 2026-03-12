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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

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
    /**
     * Override this method if you don't want to send some kind of documents or some kind of keys..
     * */
    open fun nfcOnlyFieldAcceptation(
        jsonString: String
    ): String {
        val originalReq = JSONObject(jsonString).optJSONObject("request")
        val jsonAccepted = JSONObject()
        originalReq?.keys()?.forEach {
            originalReq.optJSONObject(it)?.let { json ->
                val keyJson = JSONObject()
                json.keys().forEach { key ->
                    json.optJSONObject(key)?.let { internalJson ->
                        val internalNewJson = JSONObject()
                        internalJson.keys().forEach { dataKey ->
                            internalNewJson.put(dataKey, true)
                        }
                        keyJson.put(key, internalNewJson)
                    }
                }
                jsonAccepted.put(it, keyJson)
            }
        }
        return jsonAccepted.toString()
    }

    private val whatToDoWithRequest: (jsonString: String) -> String
        get() = { nfcOnlyFieldAcceptation(jsonString = it) }
    private val handler by lazy {
        Handler(Looper.getMainLooper())
    }
    private val runnable by lazy {
        Runnable {
            this.onDeactivated(0)
            NfcEngagementEventBus.tryEmit(
                NfcEngagementEvent.Error(
                    Throwable("NFC session timed out due to inactivity")
                )
            )
        }
    }

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
            ProximityLogger.i("NfcEngagementService", "disable")
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
                        sessionsTranscript,
                        onlyNfc = false
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
        if (nfcEngagement == null) {
            nfcEngagement = NfcEngagement
                .build(
                    this@NfcEngagementService.baseContext,
                    retrievalMethods,
                    whatToDoWithRequest = whatToDoWithRequest
                ).configure()
            createListeners()
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    @Suppress("UNCHECKED_CAST")
    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            NfcEngagementEventBus.internalEvent.collectLatest { event ->
                when (event) {
                    is ServiceEvents.SetupReady -> {
                        ProximityLogger.i("NfcEngagementService", "SetupReady")
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
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProximityLogger.i("NfcEngagementService", "On Destroy")
        serviceJob.cancel()
    }

    override fun onDeactivated(reason: Int) {
        if (nfcEngagement != null) {
            nfcEngagement?.nfcEngagementHelper?.nfcOnDeactivated(reason)
            val timeoutSeconds = 15
            nfcEngagement?.nfcEngagementHelper?.resetAll()
            Handler(Looper.getMainLooper()).postDelayed({
                nfcEngagement?.close()
                nfcEngagement = null
            }, timeoutSeconds * 1000L)
        }
    }

    /**
     * Processes incoming NFC APDU commands.
     */
    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? {
        serviceScope.launch {
            ProximityLogger.i("NfcEngagementService", "processCommandApdu: ${commandApdu.toHex()}")
            if (nfcEngagement == null) {
                NfcEngagementEvent.Error(
                    Throwable("NFC Engagement setup not DONE")
                )
            }
            val (back, theEnd) = nfcEngagement?.nfcEngagementHelper?.nfcProcessCommandApdu(
                commandApdu
            )
                ?: (null to true)
            ProximityLogger.i("Giving back (theEnd=$theEnd)", back?.toHex().orEmpty())
            back?.let {
                ProximityLogger.i(
                    "Giving back LAST (theEnd=$theEnd)",
                    byteArrayOf(back[back.size - 2], back[back.size - 1]).toHex()
                )
            }
            handler.removeCallbacks(runnable)
            if (theEnd) {
                this@NfcEngagementService.onDeactivated(0)
            } else {
                val timeoutSeconds = 3
                handler.postDelayed(runnable, timeoutSeconds * 1000L)
            }
            sendResponseApdu(back)
        }
        return null
    }
}