package it.pagopa.io.wallet.proximity.nfc

import android.content.Context
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.NfcEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.Engagement
import it.pagopa.io.wallet.proximity.retrieval.connectionMethods
import it.pagopa.io.wallet.proximity.retrieval.transportOptions
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

internal class NfcEngagement(
    context: Context
) : Engagement(context) {
    private lateinit var nfcEngagementBuilder: NfcEngagementHelperRefactor.Builder
    lateinit var nfcEngagementHelper: NfcEngagementHelperRefactor
    override val nfcEngagementListener = object : NfcEngagementHelperRefactor.Listener {
        override fun onTwoWayEngagementDetected() {
            ProximityLogger.i(this@NfcEngagement.tag, "Two way engagement detected")
        }

        override fun onDeviceConnecting() {
            ProximityLogger.i(this@NfcEngagement.tag, "Device connecting")
            this@NfcEngagement.listener?.onDeviceConnecting()
        }

        override fun onDeviceConnected(transport: DataTransport) {
            ProximityLogger.i(this@NfcEngagement.tag, "Device connected")
            if (deviceRetrievalHelper != null) {
                ProximityLogger.d(
                    this@NfcEngagement.tag,
                    "OnDeviceConnected for NFC engagement -> ignoring due to active presentation"
                )
                return
            }
            ProximityLogger.d(
                this@NfcEngagement.tag,
                "OnDeviceConnected via NFC: qrEngagement=$nfcEngagementHelper"
            )
            val deviceRetrievalHelperBuilt = DeviceRetrievalHelper.Builder(
                this@NfcEngagement.context,
                deviceRetrievalHelperListener,
                this@NfcEngagement.context.mainExecutor(),
                eDevicePrivateKey,
            ).useForwardEngagement(
                transport,
                nfcEngagementHelper.deviceEngagement,
                nfcEngagementHelper.handover,
            ).build()
            deviceRetrievalHelper = DeviceRetrievalHelperWrapper(deviceRetrievalHelperBuilt)
            listener?.onDeviceConnected(
                requireNotNull(
                    deviceRetrievalHelper
                )
            )
        }

        override fun onError(error: Throwable) {
            ProximityLogger.e(this@NfcEngagement.tag, "Error: ${error.message}")
            nfcEngagementHelper.close()
            listener?.onError(error)
        }

        override fun onHandoverSelectMessageSent() {
            ProximityLogger.i(this@NfcEngagement.tag, "Handover select message sent")
        }
    }

    override fun close() {
        try {
            if (deviceRetrievalHelper != null)
                deviceRetrievalHelper!!.disconnect()
            nfcEngagementHelper.close()
            deviceRetrievalHelper = null
        } catch (exception: RuntimeException) {
            ProximityLogger.e(this.javaClass.name, "Error closing NFC engagement $exception")
        }
    }

    /**
     * builds [NfcEngagementHelper] by com.android.identity package
     * @return [NfcEngagement] instance created via [NfcEngagement.build] static method
     * */
    override fun configure() = apply {
        nfcEngagementHelper = nfcEngagementBuilder.build()
    }


    companion object {
        /**
         * Create an instance and configures the QR engagement.
         * First of all you must call [configure] to build NfcEngagementBuilder.
         * To accept just some certificates use [withReaderTrustStore] method.
         * To observe all events call [withListener] method.
         * To close the connection call [close] method.
         */
        fun build(context: Context, retrievalMethods: List<NfcRetrievalMethod>): NfcEngagement {
            return NfcEngagement(context).apply {
                this@apply.retrievalMethods = retrievalMethods.map {
                    it.useBluetooth = NfcEngagementEventBus.bluetoothOn
                    it
                }
                this@apply.nfcEngagementBuilder = NfcEngagementHelperRefactor.Builder(
                    context,
                    this@apply.eDevicePrivateKey.publicKey,
                    this@apply.retrievalMethods.transportOptions,
                    this@apply.nfcEngagementListener,
                    context.mainExecutor(),
                    usingBle = NfcEngagementEventBus.bluetoothOn
                ).apply {
                    if (NfcEngagementEventBus.bluetoothOn) {
                        val originalMethods = retrievalMethods.connectionMethods
                        ProximityLogger.i(
                            "NfcEngagement",
                            "Original connection methods (${originalMethods.size}): $originalMethods"
                        )
                        // Non chiamare combine() qui perché viene già fatto dentro useStaticHandover()
                        useStaticHandover(originalMethods)
                    } else {
                        // NFC-only mode: use negotiated handover to establish the initial NDEF communication
                        // The actual data transfer will be handled by ApduManager using mDL AID
                        useNegotiatedHandover()
                    }
                }
            }
        }
    }
}