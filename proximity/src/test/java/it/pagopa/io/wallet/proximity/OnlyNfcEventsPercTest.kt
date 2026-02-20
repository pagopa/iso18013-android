package it.pagopa.io.wallet.proximity

import it.pagopa.io.wallet.proximity.nfc.utils.OnlyNfcEvents
import org.junit.Test

class OnlyNfcEventsPercTest {
    @Test
    fun percentageTest() {
        OnlyNfcEvents.entries.forEach {
            val percentage = OnlyNfcEvents.calculatePercentage(it)
            when (it) {
                OnlyNfcEvents.SELECT_BY_AID -> assert(percentage < 15 && percentage > 14)
                OnlyNfcEvents.SELECT_FILE -> assert(percentage < 29 && percentage > 28)
                OnlyNfcEvents.READ_BINARY -> assert(percentage < 43 && percentage > 42)
                OnlyNfcEvents.SELECT_FILE_1 -> assert(percentage < 58 && percentage > 57)
                OnlyNfcEvents.READ_BINARY_1 -> assert(percentage < 72 && percentage > 71)
                OnlyNfcEvents.READ_BINARY_2 -> assert(percentage < 86 && percentage > 85)
                OnlyNfcEvents.SELECT_BY_AID_1 -> assert(percentage == 100f)
            }
        }
    }
}