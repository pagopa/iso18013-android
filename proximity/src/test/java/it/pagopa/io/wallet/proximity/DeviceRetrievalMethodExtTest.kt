package it.pagopa.io.wallet.proximity

import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import it.pagopa.io.wallet.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.connectionMethod
import it.pagopa.io.wallet.proximity.retrieval.connectionMethods
import it.pagopa.io.wallet.proximity.retrieval.transportOptions
import org.junit.Test

class DeviceRetrievalMethodExtTest {
    private class FakeInstance : DeviceRetrievalMethod

    @Test
    fun `test connectionMethod`() {
        val obj: DeviceRetrievalMethod = BleRetrievalMethod(
            false, false, false
        )
        assert(obj.connectionMethod.isEmpty())
        val obj2: DeviceRetrievalMethod = BleRetrievalMethod(
            true, false, false
        )
        assert(obj2.connectionMethod.isNotEmpty())
        val obj3: DeviceRetrievalMethod = FakeInstance()
        try {
            obj3.connectionMethod
        } catch (e: Exception) {
            assert(e is IllegalArgumentException)
        }
    }

    @Test
    fun `test transportOptions`() {
        val list=listOf<DeviceRetrievalMethod>(
            BleRetrievalMethod(
            false, false, true
        )
        )
        assert(list.transportOptions is DataTransportOptions)
    }
    @Test
    fun `test connectionMethods`() {
        val list = listOf<DeviceRetrievalMethod>(
            BleRetrievalMethod(
                false, false, true
            )
        )
        assert(list.connectionMethods is List<ConnectionMethod>)
    }
}