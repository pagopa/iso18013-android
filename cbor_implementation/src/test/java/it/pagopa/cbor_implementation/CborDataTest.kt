package it.pagopa.cbor_implementation

import it.pagopa.cbor_implementation.impl.asCbor
import it.pagopa.cbor_implementation.impl.fromCborTo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class Test(
    val name: String,
    val surname: String,
    val age: Int
)

class CborDataTest {
    @org.junit.Test
    fun `test encode and decode`() {
        val testClass = Test("Joe", "McConnor", 30)
        val desired = testClass.toString()
        runBlocking {
            testClass.asCbor { encoded ->
                runBlocking {
                    encoded.fromCborTo<Test> {
                        assert(it.toString() == desired)
                    }
                }
            }
        }
    }
}