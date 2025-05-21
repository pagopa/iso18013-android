package it.pagopa.io.wallet.proximity.session_data

enum class SessionDataStatus(val value: Long) {
    ERROR_SESSION_ENCRYPTION(10L),
    ERROR_CBOR_DECODING(11L),
    SESSION_TERMINATION(20L)
}