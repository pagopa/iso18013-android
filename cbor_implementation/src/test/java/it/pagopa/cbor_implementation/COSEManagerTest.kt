package it.pagopa.cbor_implementation

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.cose.COSEManager
import it.pagopa.cbor_implementation.cose.CreateCOSE
import it.pagopa.cbor_implementation.cose.SignWithCOSEResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.*
import java.lang.IllegalArgumentException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class COSEManagerTest {

    private lateinit var coseManager: COSEManager
    private lateinit var mockCreateCOSE: CreateCOSE

    private val mockCiaoSignedB64 by lazy {
        "hEOhASZBoERjaWFvWEgwRgIhAMi5+IyN8Y29HTPgpoVKpZ5C79kKgXtEphkR9SThz7heAiEA6DqFDP/v3kjziF9gVFB+zq9urCI5QffxQY6HSPR8ApA="
    }

    private val mockCiaoSignedPubKey by lazy {
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEr6rRg9G4SDg0i8W1DBKSvk9wMlhumqTF353H2CmrycKClwgwErIY+COCVrpF6JH9k0vnOJUhtItPv2uMMo11yQ=="
    }

    private val anOtherPubKey by lazy {
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9Dpf7txDs+d/Eq1LCR1RpvMUqMjTf0vb5gRMatYJLdE2U/uypHxThvp1YZaPVLtGy9iGC6Gyigng6gOToxugng=="
    }

    private val mockHelloSignedB64 by lazy {
        "hEOhASZBoEVoZWxsb1hHMEUCIQC06Stj/TKM5gbhwAb4SC4sz22J9ZkMMOCQ3bq3HcIifgIgY/YZy7ya3I8I52jXfy/EhbWAl83/kH0vsGIkUb2YBDM="
    }

    @Before
    fun setUp() {
        coseManager = COSEManager()
        mockCreateCOSE = mock()
    }

    private fun prepareCreateCOSEToSign(expectedResult: SignWithCOSEResult): SignWithCOSEResult {
        val data = "test data".toByteArray()
        val alias = "pagoPaAlias"
        val isDetached = false
        `when`(mockCreateCOSE.sign(eq(data), eq(isDetached))).thenReturn(expectedResult)
        coseManager.withCreateCOSE(mockCreateCOSE)
        return coseManager.signWithCOSE(data, alias, isDetached)
    }

    @Test
    fun `test signWithCOSE success`() {
        val mockResult = SignWithCOSEResult.Success(
            "signedData".toByteArray(),
            "publicKey".toByteArray()
        )
        val result = prepareCreateCOSEToSign(mockResult)
        assertTrue(result is SignWithCOSEResult.Success)
        assertEquals("signedData", String((result as SignWithCOSEResult.Success).signature))
    }

    @Test
    fun `test signWithCOSE failure`() {
        val mockResult = SignWithCOSEResult.Failure("signing error")
        val result = prepareCreateCOSEToSign(mockResult)
        assertTrue(result is SignWithCOSEResult.Failure)
        assertEquals("signing error", (result as SignWithCOSEResult.Failure).msg)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test verifySign1 with valid signature`() {
        assert(
            coseManager.verifySign1(
                dataSigned = Base64.decode(mockCiaoSignedB64),
                publicKey = Base64.decode(mockCiaoSignedPubKey)
            )
        )
        assert(
            coseManager.verifySign1(
                dataSigned = Base64.decode(mockHelloSignedB64),
                publicKey = Base64.decode(mockCiaoSignedPubKey)
            )
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test verifySign1 with non valid signature`() {
        assert(
            !coseManager.verifySign1(
                dataSigned = Base64.decode(mockCiaoSignedB64),
                publicKey = Base64.decode(anOtherPubKey)
            )
        )
        assert(
            !coseManager.verifySign1(
                dataSigned = Base64.decode(mockHelloSignedB64),
                publicKey = Base64.decode(anOtherPubKey)
            )
        )
    }

    @Test
    fun `test algorithmFromProtectedHeader`() {
        val protectedHeader = mock<CBORObject>()
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-7))
        val result = coseManager.algorithmFromProtectedHeader(protectedHeader)
        assertEquals("SHA256withECDSA", result)
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-35))
        assertEquals("SHA384withECDSA", coseManager.algorithmFromProtectedHeader(protectedHeader))
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-36))
        assertEquals("SHA512withECDSA", coseManager.algorithmFromProtectedHeader(protectedHeader))
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-37))
        assertEquals("SHA256withRSA", coseManager.algorithmFromProtectedHeader(protectedHeader))
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-38))
        assertEquals("SHA384withRSA", coseManager.algorithmFromProtectedHeader(protectedHeader))
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-39))
        assertEquals("SHA512withRSA", coseManager.algorithmFromProtectedHeader(protectedHeader))
        whenever(protectedHeader[1]).thenReturn(CBORObject.FromObject(-80))
        try {
            coseManager.algorithmFromProtectedHeader(protectedHeader)
        } catch (e: Exception) {
            assert(e is IllegalArgumentException)
        }
    }
}