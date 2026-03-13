package it.pagopa.io.wallet.proximity.nfc

import kotlinx.coroutines.flow.StateFlow

/**
 * An abstraction of a ISO/IEC 18013-5:2021 device retrieval method.
 *
 * A [MdocTransport]'s state can be tracked in the [state] property which is [State.IDLE]
 * when constructed from the factory. This is a [StateFlow] and intended to be used by
 * the application to update its user interface.
 *
 * To open a connection to the other peer, call [open]. When [open] returns successfully
 * the state is [State.CONNECTED].
 *
 * The transport can fail at any time, for example if the other peer sends invalid data
 * or actively disconnects. In this case the state is changed to [State.FAILED] and
 *
 * The connection can be closed at any time using the [close] method which will transition
 * the state to [State.CLOSED] except if it's already in [State.FAILED].
 *
 * [MdocTransport] instances are thread-safe and methods and properties can be called from
 * any thread or coroutine.
 */
abstract class MdocTransport {

    /**
     * Possible states for transport.
     */
    enum class State {
        /** The transport is idle. */
        IDLE,

        /** The transport is being advertised. */
        ADVERTISING,

        /** The transport is scanning. */
        SCANNING,

        /** A remote peer has been identified and the connection is being set up. */
        CONNECTING,

        /** The transport is connected to the remote peer. */
        CONNECTED,

        /** The transport was connected at one point but one of the sides closed the connection. */
        CLOSED,

        /** The connection to the remote peer failed. */
        FAILED
    }

    /**
     * The current state of the transport.
     */
    abstract val state: StateFlow<State>

    /**
     * Starts advertising the connection.
     *
     * This is optional for transports to implement.
     */
    abstract suspend fun advertise()
    abstract suspend fun sendMessage(message: ByteArray)

    /**
     * Opens the connection to the other peer.
     */
    abstract suspend fun open()
    abstract suspend fun waitForMessage(): ByteArray

    /**
     * Closes the connection.
     *
     * This is idempotent and can be called from any thread.
     */
    abstract suspend fun close()
}