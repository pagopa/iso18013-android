package it.pagopa.proximity

import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import it.pagopa.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.proximity.retrieval.connectionMethod
import it.pagopa.proximity.retrieval.connectionMethods
import it.pagopa.proximity.retrieval.transportOptions
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
        val list=listOf<DeviceRetrievalMethod>(BleRetrievalMethod(
            false, false, true
        ))
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