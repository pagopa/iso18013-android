package it.pagopa.io.wallet.proximity.nfc.utils

enum class OnlyNfcEvents(var percentage: Float = 0f) {
    AGREEMENT,
    ENVELOPE,
    GET_RESPONSE;

    companion object {
        @JvmStatic
        fun calculatePercentage(currentValue: OnlyNfcEvents): Float {
            return (OnlyNfcEvents
                .entries
                .indexOf(currentValue)
                .toFloat() + 1) * 100f / OnlyNfcEvents.entries.size.toFloat()
        }
    }
}