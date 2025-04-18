package it.pagopa.io.wallet.cbor

import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.cose.FailureReason
import it.pagopa.io.wallet.cbor.cose.COSEManager
import it.pagopa.io.wallet.cbor.cose.CreateCOSE
import it.pagopa.io.wallet.cbor.cose.SignWithCOSEResult
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
        val mockResult = SignWithCOSEResult.Failure(FailureReason.FAIL_TO_SIGN)
        val result = prepareCreateCOSEToSign(mockResult)
        assertTrue(result is SignWithCOSEResult.Failure)
        assertEquals(
            FailureReason.FAIL_TO_SIGN.msg,
            (result as SignWithCOSEResult.Failure).reason.msg
        )
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

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test verify from jwk`() {
        val jwkKey = "{\n" +
                "  crv: 'P-256',\n" +
                "  kty: 'EC',\n" +
                "  x: 'd2SM2WRV0lOKlMQJGcN76P+mAyau4vhVLlhgzAxyWp4=',\n" +
                "  y: 'FiQJMW6agCMNC9i79ePkQqvtvsaOVaQwZkkcmbsQ/gQ=',\n" +
                "}"
        val back = coseManager.jwkToPublicKey(jwkKey)
        println("back:")
        println(Base64.encode(back.encoded))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test verify sign 1 from jwk`() {
        val dataToVerify =
            "hEOhASagUXRoaXMgaXMgdGVzdCBkYXRhWECWHFXxcZPkyupozacO5KTeBDcbXFYX6HaFynTZ85qXdtGGd9bhtgBq1vcjYdK0QHP+DmG15108cm497i83ScSf"
        val jwkKey = "{\n" +
                "  crv: 'P-256',\n" +
                "  kty: 'EC',\n" +
                "  x: 'RjbyQJnEKZuBIQ71mLMVs0+y5uzdEe1+pALMMoucqlQ=',\n" +
                "  y: 'hlVFni6N2sqKY6XK3KGmqU4m7Y+U606ElJKRvNQ0nH4=',\n" +
                "}"
        println("RESULT:")
        println(
            coseManager.verifySign1FromJWK(
                java.util.Base64.getDecoder().decode(dataToVerify),
                jwkKey
            )
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test signWithCOSE data byte array`() {
        val sign1 =
            "hEOhASZBoERjaWFvWEDrbkglFsrvakjKvIz5XXt0Pnv3cqoymPJV6F4wsw4swXBYSDinBN0NnMOMYGi74XkR1MeDo9AeaPJGfvM64PK1"
        val key =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOWGxSNh5Ilo5lLKukkB/HoytUDsSTm1IUbjOib+6Ld20SEWiM+ZRqT/aetmQBP84WpMPQ3gtflB6kMKpUj21MA=="
        println(coseManager.verifySign1(Base64.decode(sign1), Base64.decode(key)))
    }
}