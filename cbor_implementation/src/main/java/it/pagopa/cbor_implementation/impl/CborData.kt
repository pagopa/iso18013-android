package it.pagopa.cbor_implementation.impl

import it.pagopa.cbor_implementation.CborLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@OptIn(ExperimentalSerializationApi::class)
suspend inline fun <reified T> T.asCbor(
    crossinline onDone: (ByteArray) -> Unit
) {
    withContext(Dispatchers.Default + SupervisorJob()) {
        val back = Cbor.encodeToByteArray(this@asCbor)
        CborLogger.i("Cbor encoded", back.joinToString(", "))
        onDone.invoke(back)
    }
}

@OptIn(ExperimentalSerializationApi::class)
suspend inline fun <reified T> ByteArray.fromCborTo(
    crossinline onDone: (T) -> Unit
) {
    withContext(Dispatchers.Default + SupervisorJob()) {
        val back = Cbor.decodeFromByteArray<T>(this@fromCborTo)
        CborLogger.i("Cbor decoded", "$back")
        onDone.invoke(back)
    }
}