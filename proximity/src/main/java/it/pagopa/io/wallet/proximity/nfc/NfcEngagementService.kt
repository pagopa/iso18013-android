package it.pagopa.io.wallet.proximity.nfc

import android.app.Activity
import android.content.ComponentName
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.EngagementListener
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
 * import eu.europa.ec.eudi.iso18013.holder.transfer.engagement.NfcEngagementService
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
    private lateinit var nfcEngagement: NfcEngagement
    open val readerTrustStore: List<Any> = listOf()

    companion object {

        /**
         * Enable NFC engagement
         *
         * @param activity
         */
        @JvmStatic
        fun enable(
            activity: Activity,
            preferredNfcEngSerCls: Class<out NfcEngagementService>? = null,
        ) {
            // set preferred Nfc Engagement Service
            preferredNfcEngSerCls?.let {
                setAsPreferredNfcEngagementService(activity, preferredNfcEngSerCls)
            }
        }

        /**
         * Disable NFC engagement
         *
         * @param activity
         */
        @JvmStatic
        fun disable(activity: Activity) {
            // unset preferred Nfc Engagement Service
            unsetAsPreferredNfcEngagementService(activity)
        }

        @JvmStatic
        private fun setAsPreferredNfcEngagementService(
            activity: Activity,
            nfcEngagementServiceClass: Class<out NfcEngagementService>,
        ) {
            val cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(activity))
            val allowsForeground =
                cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)
            if (allowsForeground) {
                val hceComponentName = ComponentName(
                    activity,
                    nfcEngagementServiceClass,
                )
                cardEmulation.setPreferredService(activity, hceComponentName)
            }
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
        nfcEngagement = NfcEngagement.build(this.baseContext).configure()
        readerTrustStore.firstOrNull()?.let {
            when (it) {
                is ByteArray -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<ByteArray>)
                is Int -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<Int>)
                is String -> this.nfcEngagement.withReaderTrustStore(readerTrustStore as List<String>)
                else -> throw Exception("readerTrustStore type not supported")
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
        nfcEngagement.nfcEngagement.nfcOnDeactivated(reason)
        val timeoutSeconds = 15
        Handler(Looper.getMainLooper()).postDelayed({
            nfcEngagement.close()
        }, timeoutSeconds * 1000L)
    }

    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? {
        if (ProximityLogger.enabled)
            ProximityLogger.i("APDU_COMMAND", Base64.encodeToString(commandApdu, Base64.DEFAULT))
        return this.nfcEngagement.nfcEngagement.nfcProcessCommandApdu(commandApdu)
    }
}