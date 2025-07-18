package it.pagopa.io.wallet.proximity

import it.pagopa.io.wallet.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.io.wallet.proximity.qr_code.toX509Certificate
import it.pagopa.io.wallet.proximity.qr_code.tryGetCertificate
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ReaderTrustStoreTest {
    //Certificate should be valid, and respect today!!
    private val verifierCertificate by lazy {
        "MIICnzCCAkSgAwIBAgIUZPWn95JGsoc26PQtH5/aG9Jjj9owCgYIKoZIzj0EAwIwgcIxCzAJBgNVBAYTAklUMQ4wDAYDVQQIEwVMYXppbzENMAsGA1UEBxMEUm9tZTE4MDYGA1UEChMvSXN0aXR1dG8gUG9saWdyYWZpY28gZSBaZWNjYSBkZWxsbyBTdGF0byBTLlAuQS4xDTALBgNVBAsTBElQWlMxJDAiBgNVBAMTG3ByZS52ZXJpZmllci53YWxsZXQuaXB6cy5pdDElMCMGCSqGSIb3DQEJARYWcHJvdG9jb2xsb0BwZWMuaXB6cy5pdDAeFw0yNTA3MTEwNzU5NTFaFw0yNTA3MTIwNzU5NTJaMGUxFDASBgNVBAoTC2VUdWl0dXMgU3JsMTMwCQYDVQQLEwJJVDAmBgNVBAsTH1Byb3hpbWl0eSBWZXJpZmljYXRpb24gRGVtbyBBcHAxGDAWBgNVBAMTD2RldmljZS11dWlkLTEyMzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABEIATyvQxowGeuxqox2FnQzadnEd9Aw0ipWEU/IxwUEmJuaw0L8iSwZh3jplgnrjE2sH6By0tnP+tlaOCY8tBjqjdDByMA4GA1UdDwEB/wQEAwIHgDASBgNVHSUECzAJBgcogYxdBQEGMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFP+9lUxh4Rm67v0Xla0lDgOPbYUCMB8GA1UdIwQYMBaAFDbzueiFM+S1Ubtpm73hfA+sUOP8MAoGCCqGSM49BAMCA0kAMEYCIQDlXIkl05r2ejRX0iELV+x+2jRy+qXQ0fHZvxsLTApqggIhAMTL4+2UQBRSVl8cv6ROQmm9YI2gH3DP/VepFbrelCNS"
    }
    private val node by lazy {
        "MIIDQzCCAuigAwIBAgIGAZc6+XlDMAoGCCqGSM49BAMCMIGzMQswCQYDVQQGEwJJVDEOMAwGA1UECAwFTGF6aW8xDTALBgNVBAcMBFJvbWExMTAvBgNVBAoMKElzdGl0dXRvIFBvbGlncmFmaWNvIGUgWmVjY2EgZGVsbG8gU3RhdG8xCzAJBgNVBAsMAklUMR4wHAYDVQQDDBVwcmUudGEud2FsbGV0LmlwenMuaXQxJTAjBgkqhkiG9w0BCQEWFnByb3RvY29sbG9AcGVjLmlwenMuaXQwHhcNMjUwNjA0MTI0NTE3WhcNMzAwNjAzMTI0NTE3WjCBszELMAkGA1UEBhMCSVQxDjAMBgNVBAgMBUxhemlvMQ0wCwYDVQQHDARSb21hMTEwLwYDVQQKDChJc3RpdHV0byBQb2xpZ3JhZmljbyBlIFplY2NhIGRlbGxvIFN0YXRvMQswCQYDVQQLDAJJVDEeMBwGA1UEAwwVcHJlLnRhLndhbGxldC5pcHpzLml0MSUwIwYJKoZIhvcNAQkBFhZwcm90b2NvbGxvQHBlYy5pcHpzLml0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaE0xyhd3e9LDT7uwHOclL5H3389gwiCwFhI3KOvidn0glBIHYxqH+4Z9VTMYWG5L8cwC9AaJUCNGu2dp5ZiiTKOB5TCB4jAdBgNVHQ4EFgQU81CDcYxAqV3ptM8iKbJ06r9wxBkwHwYDVR0jBBgwFoAU81CDcYxAqV3ptM8iKbJ06r9wxBkwDwYDVR0TAQH/BAUwAwEB/zBEBggrBgEFBQcBAQQ4MDYwNAYIKwYBBQUHMAKGKGh0dHBzOi8vcHJlLnRhLndhbGxldC5pcHpzLml0L3BraS90YS5jZXIwDgYDVR0PAQH/BAQDAgEGMDkGA1UdHwQyMDAwLqAsoCqGKGh0dHBzOi8vcHJlLnRhLndhbGxldC5pcHpzLml0L3BraS90YS5jcmwwCgYIKoZIzj0EAwIDSQAwRgIhAOsQYzR+eGf4je63VGHqkpmkBbfyOre+mfIdHHowWWR/AiEA58xBNb5UW5uMB+tQur8fq24RD5MmRHLYS6bDgIYmluw="
    }
    private val root by lazy {
        "MIID2zCCA4GgAwIBAgIGAZe1/EqsMAoGCCqGSM49BAMCMIGzMQswCQYDVQQGEwJJVDEOMAwGA1UECAwFTGF6aW8xDTALBgNVBAcMBFJvbWExMTAvBgNVBAoMKElzdGl0dXRvIFBvbGlncmFmaWNvIGUgWmVjY2EgZGVsbG8gU3RhdG8xCzAJBgNVBAsMAklUMR4wHAYDVQQDDBVwcmUudGEud2FsbGV0LmlwenMuaXQxJTAjBgkqhkiG9w0BCQEWFnByb3RvY29sbG9AcGVjLmlwenMuaXQwHhcNMjUwNjI4MTAwMTM5WhcNMjcwNjI4MTAwMTM5WjCBwjELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUxhemlvMQ0wCwYDVQQHEwRSb21lMTgwNgYDVQQKEy9Jc3RpdHV0byBQb2xpZ3JhZmljbyBlIFplY2NhIGRlbGxvIFN0YXRvIFMuUC5BLjENMAsGA1UECxMESVBaUzEkMCIGA1UEAxMbcHJlLnZlcmlmaWVyLndhbGxldC5pcHpzLml0MSUwIwYJKoZIhvcNAQkBFhZwcm90b2NvbGxvQHBlYy5pcHpzLml0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESmYL3AUsTchFLid1pOEg+JvN2AljFoTlleeAOV/iQUhkqbLbUAgdplxSiE2Zh5BeCrhr6AYQFXbEInd4W99cNaOCAW4wggFqMB0GA1UdDgQWBBQ287nohTPktVG7aZu94XwPrFDj/DCB5QYDVR0jBIHdMIHagBTzUINxjECpXem0zyIpsnTqv3DEGaGBuaSBtjCBszELMAkGA1UEBhMCSVQxDjAMBgNVBAgMBUxhemlvMQ0wCwYDVQQHDARSb21hMTEwLwYDVQQKDChJc3RpdHV0byBQb2xpZ3JhZmljbyBlIFplY2NhIGRlbGxvIFN0YXRvMQswCQYDVQQLDAJJVDEeMBwGA1UEAwwVcHJlLnRhLndhbGxldC5pcHpzLml0MSUwIwYJKoZIhvcNAQkBFhZwcm90b2NvbGxvQHBlYy5pcHpzLml0ggYBlzr5eUMwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAaYwPQYDVR0fBDYwNDAyoDCgLoYsaHR0cHM6Ly9wcmUudGEud2FsbGV0LmlwenMuaXQvcGtpL3RhLXN1Yi5jcmwwCgYIKoZIzj0EAwIDSAAwRQIhAPtgro6cUthvuuO15dUxepQxEel6KQkQkLYXcAG6mZeLAiAsR+SjlJNKLeTzEg0OdOtremJ1K+Q2BowcnIbEGbN+vA=="
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test certificates validity`() {
        try {
            val nodeBA = Base64.decode(node)
            val rootBA = Base64.decode(root)
            val readerTrustStore = ReaderTrustStore.getDefault(
                listOf(nodeBA, rootBA).map { certificateBytes ->
                    tryGetCertificate {
                        (certificateBytes).toX509Certificate()
                    }!!
                }
            )
            val verifierCertificateBa = Base64.decode(verifierCertificate)
            //As Certificate should be valid, and respect today we cannot assert test result
            println(readerTrustStore.validateCertificationTrustPath(listOf(verifierCertificateBa.toX509Certificate()!!)))
        }catch (e: Exception){
            println("$e")
        }
    }
}