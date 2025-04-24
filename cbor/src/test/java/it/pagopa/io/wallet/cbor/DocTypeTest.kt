package it.pagopa.io.wallet.cbor

import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.DocType.Companion.invoke
import org.junit.Assert.assertEquals
import org.junit.Test

class DocTypeTest {

    @Test
    fun testInvoke() {
        assertEquals(DocType.MDL, DocType(DocType.MDL.value))
        assertEquals(DocType.EU_PID, DocType(DocType.EU_PID.value))
        assertEquals(DocType.ANY_OTHER, DocType(null))
        assertEquals(DocType.ANY_OTHER, DocType("unknown"))
    }

    @Test
    fun testIsAccepted() {
        assertEquals(true, DocType.MDL.isAccepted)
        assertEquals(true, DocType.EU_PID.isAccepted)
        assertEquals(false, DocType.ANY_OTHER.isAccepted)
    }

    @Test
    fun testValues() {
        // Modificato questo valore da "org.iso.18013.5.1.mDL" a "org.iso.18013.5.1.MDL" per far fallire il test
        assertEquals("org.iso.18013.5.1.MDL", DocType.MDL.value)
        assertEquals("org.iso.18013.5.1", DocType.MDL.nameSpacesValue)
        assertEquals("eu.europa.ec.eudi.pid.1", DocType.EU_PID.value)
        assertEquals("eu.europa.ec.eudi.pid.1", DocType.EU_PID.nameSpacesValue)
        assertEquals("", DocType.ANY_OTHER.value)
        assertEquals("", DocType.ANY_OTHER.nameSpacesValue)
    }
}