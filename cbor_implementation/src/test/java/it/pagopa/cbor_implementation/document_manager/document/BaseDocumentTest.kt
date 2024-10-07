package it.pagopa.cbor_implementation.document_manager.document

import com.android.identity.document.Document as BaseDocument
import com.android.identity.document.NameSpacedData
import com.android.identity.util.ApplicationData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import it.pagopa.cbor_implementation.document_manager.state
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseDocumentTest {

    @Test
    fun `test state getter when state exists`() {
        val applicationData = mockk<ApplicationData>()
        val baseDocument = mockk<BaseDocument>()
        every { baseDocument.applicationData } returns applicationData
        every { applicationData.getNumber("state") } returns Document.State.ISSUED.value

        assertEquals(Document.State.ISSUED, baseDocument.state)
    }

    @Test
    fun `test state getter when state is missing and nameSpacedData is empty`() {
        val applicationData = mockk<ApplicationData>()
        val baseDocument = mockk<BaseDocument>()
        every { baseDocument.applicationData } returns applicationData
        every { applicationData.getNumber("state") } throws Exception("State not found")
        every { applicationData.keyExists("nameSpacedData") } returns false

        assertEquals(Document.State.UNSIGNED, baseDocument.state)
    }

    @Test
    fun `test state getter when state is missing and nameSpacedData is not empty`() {
        val applicationData = mockk<ApplicationData>()
        val baseDocument = mockk<BaseDocument>()
        val nameSpacedData = mockk<NameSpacedData>()

        every { baseDocument.applicationData } returns applicationData
        every { applicationData.getNumber("state") } throws Exception("State not found")
        every { applicationData.keyExists("nameSpacedData") } returns true
        every { applicationData.getNameSpacedData("nameSpacedData") } returns nameSpacedData
        every { nameSpacedData.nameSpaceNames } returns listOf("someNamespace")

        assertEquals(Document.State.ISSUED, baseDocument.state)
    }

    @Test
    fun `test state setter`() {
        val applicationData = mockk<ApplicationData>()
        val baseDocument = mockk<BaseDocument>()
        every { baseDocument.applicationData } returns applicationData
        every { applicationData.setNumber("state", any()) } returns applicationData

        baseDocument.state = Document.State.ISSUED

        verify { applicationData.setNumber("state", Document.State.ISSUED.value) }
    }
}