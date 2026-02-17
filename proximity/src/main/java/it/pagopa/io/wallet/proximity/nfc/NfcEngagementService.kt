package it.pagopa.io.wallet.proximity.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.CheckResult
import com.android.identity.android.util.NfcUtil
import com.android.identity.util.toHex
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.EngagementListener
import it.pagopa.io.wallet.proximity.nfc.apdu.ApduManager
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

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
    abstract val javaClassToLaunch: Class<out Activity>
    open val docs: Array<Document> = arrayOf()
    private var manager: ApduManager? = null
    open val alias: String = ""
    private val nfcEngagement: NfcEngagement by lazy {
        NfcEngagement.build(this.baseContext, listOf(NfcRetrievalMethod())).configure()
    }
    open val readerTrustStore: List<List<Any>> = listOf()
    private var numApdusReceived = 0
    private var firstCommandApdu: ByteArray? = null
    private var engagementStarted = false

    companion object {

        /**
         * Enable NFC engagement
         *
         * @param activity
         */
        @JvmStatic
        @CheckResult
        fun enable(
            activity: Activity,
            preferredNfcEngSerCls: Class<out HostApduService>? = null,
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
            nfcEngagementServiceClass: Class<out HostApduService>,
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
                nfcEngagementServiceClass
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                cardEmulation.registerPollingLoopPatternFilterForService(
                    ComponentName(activity.applicationContext, NfcEngagementService::class.java),
                    "6a028103.*",
                    false
                )
            }
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

    @Suppress("UNCHECKED_CAST")
    override fun onCreate() {
        super.onCreate()
        readerTrustStore.firstOrNull()?.let { list ->
            list.firstOrNull()?.let {
                when (it) {
                    is ByteArray -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<List<ByteArray>>)
                    is Int -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<List<Int>>)
                    is String -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<List<String>>)
                    else -> throw Exception("readerTrustStore type not supported")
                }
            } ?: run {
                throw Exception("readerTrustStore type not supported")
            }
        }
        this.nfcEngagement.withListener(object : EngagementListener {
            override fun onDeviceConnecting() {
                NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Connecting)
            }

            override fun onDeviceConnected(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
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

    override fun onDeactivated(reason: Int) {
        numApdusReceived = 0
        engagementStarted = false
        firstCommandApdu = null
        nfcEngagement.nfcEngagementHelper.nfcOnDeactivated(reason)
        val timeoutSeconds = 15
        manager = null
        Handler(Looper.getMainLooper()).postDelayed({
            nfcEngagement.close()
        }, timeoutSeconds * 1000L)
    }

    private fun ByteArray.processApdu(): ByteArray {
        if (NfcEngagementEventBus.bluetoothOn) {
            ProximityLogger.i("COMMAND_TYPE_BLE", NfcUtil.nfcGetCommandType(this).toString())
            return this@NfcEngagementService.nfcEngagement.nfcEngagementHelper.nfcProcessCommandApdu(
                this
            )
        }
        if (manager == null)
            manager = ApduManager(nfcEngagement, docs, alias)
        ProximityLogger.i(
            "NFC_ENG_SERVICE",
            NfcUtil.nfcGetCommandType(this).toString()
        )
        return when (NfcUtil.nfcGetCommandType(this)) {
            NfcUtil.COMMAND_TYPE_SELECT_BY_AID -> {
                manager?.let {
                    val selectedAid = this.copyOfRange(5, 12)
                    if (selectedAid.contentEquals(NfcUtil.AID_FOR_MDL_DATA_TRANSFER)) {
                        ProximityLogger.i("SELECTED", "AID_FOR_MDL_DATA_TRANSFER")
                        //MDL AID detected, using ApduManager for NFC-only
                        it.resetApduDataRetrievalState()
                        NfcUtil.STATUS_WORD_OK
                    } else {
                        //Unknown AID, rejecting
                        NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
                    }
                } ?: run {
                    //Manager null in NFC-only mode - this shouldn't happen
                    NfcUtil.STATUS_WORD_FILE_NOT_FOUND
                }
            }

            NfcUtil.COMMAND_TYPE_ENVELOPE -> manager?.handleEnvelope(this)
                ?: NfcUtil.STATUS_WORD_FILE_NOT_FOUND

            NfcUtil.COMMAND_TYPE_RESPONSE -> manager?.handleGetResponse(this)
                ?: NfcUtil.STATUS_WORD_FILE_NOT_FOUND

            else -> NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
        }
    }

    /**
     * Processes incoming NFC APDU commands.
     */
    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? {
        if (numApdusReceived == 0) {
            numApdusReceived = 1
            firstCommandApdu = commandApdu
            return NfcUtil.STATUS_WORD_OK
        }

        if (!engagementStarted) {
            engagementStarted = true
            val intent = Intent(applicationContext, javaClassToLaunch)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            intent.putExtra("NfcEngagement", "true")
            applicationContext.startActivity(intent)
        }
        if (numApdusReceived++ == 1) {
            val responseApdu = commandApdu.processApdu()
            if (!responseApdu.contentEquals(NfcUtil.STATUS_WORD_OK)) {
                ProximityLogger.e(
                    this.javaClass.name, "Expected response 9000 to SELECT APPLICATION, " +
                            " got ${responseApdu.toHex()}"
                )
            }
        }
        return commandApdu.processApdu()
    }
}