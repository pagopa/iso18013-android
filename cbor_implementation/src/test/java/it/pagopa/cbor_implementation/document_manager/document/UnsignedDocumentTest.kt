package it.pagopa.cbor_implementation.document_manager.document

import com.android.identity.credential.Credential
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.document.NameSpacedData
import com.android.identity.securearea.KeyAttestation
import com.android.identity.util.ApplicationData
import io.mockk.every
import io.mockk.mockk
import it.pagopa.cbor_implementation.document_manager.SignedWithAuthKeyResult
import it.pagopa.cbor_implementation.document_manager.createdAt
import it.pagopa.cbor_implementation.document_manager.docType
import it.pagopa.cbor_implementation.document_manager.documentName
import it.pagopa.cbor_implementation.document_manager.requiresUserAuth
import it.pagopa.cbor_implementation.document_manager.usesStrongBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import java.time.Instant
import com.android.identity.document.Document as BaseDocument

class UnsignedDocumentTest {

    private val documentId = "documentId"
    private val documentName = "documentName"
    private val docType = "docType"
    private val usesStrongBox = true
    private val requiresUserAuth = true
    private val createdAt = Instant.ofEpochMilli(0)
    private val x509Certificate = mockk<X509Certificate>()

    private lateinit var unsignedDocument: UnsignedDocument
    private lateinit var baseDocument: BaseDocument

    @Before
    fun setup() {
        every { x509Certificate.publicKey } returns mockk()
        val applicationData = mockk<ApplicationData>()
        val nameSpacedData = mockk<NameSpacedData>()
        every { nameSpacedData.nameSpaceNames } returns listOf("someNamespace")
        every { applicationData.setNumber("state", any()) } returns applicationData
        every { applicationData.getNumber("createdAt") } returns 0L
        every { applicationData.keyExists("nameSpacedData") } returns true
        every { applicationData.getNameSpacedData("nameSpacedData") } returns nameSpacedData
        every { applicationData.getString("name") } returns documentName
        every { applicationData.getString("docType") } returns docType
        baseDocument = mockk<BaseDocument>()
        every { baseDocument.name } returns documentId
        every { baseDocument.documentName } returns documentName
        every { baseDocument.docType } returns docType
        every { baseDocument.usesStrongBox } returns usesStrongBox
        every { baseDocument.requiresUserAuth } returns requiresUserAuth
        every { baseDocument.createdAt } returns createdAt
        every { baseDocument.applicationData } returns applicationData
        every { baseDocument.certifiedCredentials } returns listOf()

        val secureAreaBoundCredential = mockk<SecureAreaBoundCredential>()
        val keyAttestation = mockk<KeyAttestation>()
        every { keyAttestation.publicKey } returns mockk()
        every { keyAttestation.certChain } returns null
        every { secureAreaBoundCredential.attestation } returns keyAttestation
        every { secureAreaBoundCredential.alias } returns "testAlias"

        val pendingCredentials = listOf<Credential>(secureAreaBoundCredential)
        every { baseDocument.pendingCredentials } returns pendingCredentials

        unsignedDocument = UnsignedDocument(baseDocument)
    }

    @Test
    fun `test properties`() {
        assertEquals(documentId, unsignedDocument.id)
        assertEquals(documentName, unsignedDocument.name)
        assertEquals(docType, unsignedDocument.docType)
        assertEquals(false, unsignedDocument.usesStrongBox)// this is false cause certChain is null
        assertEquals(false, unsignedDocument.requiresUserAuth)
        assertEquals(createdAt, unsignedDocument.createdAt)
    }

    @Test
    fun `test state getter when base document is null`() {
        val certificatesNeedAuth: List<X509Certificate> = listOf(x509Certificate)
        val unsignedDocument = UnsignedDocument(
            id = documentId,
            name = documentName,
            docType = docType,
            usesStrongBox = usesStrongBox,
            requiresUserAuth = requiresUserAuth,
            createdAt = createdAt,
            certificatesNeedAuth = certificatesNeedAuth
        )
        assertEquals(Document.State.UNSIGNED, unsignedDocument.state)
    }

    @Test
    fun `test signWithAuthKey failure`() {
        every { baseDocument.pendingCredentials } returns emptyList()
        val unsignedDocument = UnsignedDocument(baseDocument)
        val data = "testData".toByteArray()

        val result = unsignedDocument.signWithAuthKey(data)

        assertTrue(result is SignedWithAuthKeyResult.Failure)
    }
}