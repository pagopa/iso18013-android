package it.pagopa.cbor_implementation

import it.pagopa.cbor_implementation.parser.CBorParser
import org.json.JSONObject
import org.junit.Test
import java.util.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CborParserTest {
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test is json`() {
        val rawCbor = kotlin.io.encoding.Base64.decode(moreDocsSource)
        val json = CBorParser(rawCbor).toJson()
        println(json)
        assert(json != null)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test one document json`() {
        val rawCbor = kotlin.io.encoding.Base64.decode(oneDocSource)
        CBorParser(rawCbor).documentsCborToJson { json ->
            println(json)
            assert(json != null)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test documents json`() {
        val rawCbor = kotlin.io.encoding.Base64.decode(moreDocsSource)
        CBorParser(rawCbor).documentsCborToJson { json ->
            println(json)
            assert(json != null)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test documents json issuer auth`() {
        val rawCbor = kotlin.io.encoding.Base64.decode(moreDocsIssuerAuth)
        CBorParser(rawCbor).documentsCborToJson { json ->
            assert(json != null)
            val myJson = JSONObject(json!!)
            myJson.optJSONArray("documents")?.let {
                for (i in 0 until it.length()) {
                    it.getJSONObject(i).optJSONObject("issuerSigned")?.let { issuerSigned ->
                        issuerSigned.optString("issuerAuth").let { issuerAuth ->
                            val ba = Base64.getUrlDecoder().decode(issuerAuth)
                            println("###############################")
                            println("ISSUER_AUTH PARSED:")
                            val parsed = CBorParser(ba).toJson()
                            println(parsed)
                            println("###############################")
                        }
                    }
                }
            }

            println(json)
        }
    }
}