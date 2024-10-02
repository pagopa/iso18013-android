package it.pagopa.cbor_implementation

import it.pagopa.cbor_implementation.impl.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MDocDataTest {
    @org.junit.Test
    fun `test encode and decode`() {
        val mDoc = MDoc(source = oneDocSource)
        mDoc.decodeMDoc(onComplete = { modelMDoc ->
            println(modelMDoc.documents)
        }, onError = {

        })
    }

    @org.junit.Test
    fun `test validity`() {
        val mDoc = MDoc(source = moreDocsSource)
        mDoc.decodeMDoc(
            onComplete = { docModel ->
                docModel.documents?.forEachIndexed { i, it ->
                    val (isValid, error) = mDoc.verifyValidity(it)
                    val errorToPrint = if (error != null) " error: $error" else ""
                    println("document -> $i: isValid: $isValid$errorToPrint")
                }
            },
            onError = {

            })
    }

    @OptIn(ExperimentalEncodingApi::class)
    @org.junit.Test
    fun `test encode and decode with Byte Array`() {
        val mDoc = MDoc(source = Base64.decode(oneDocSource))
        mDoc.decodeMDoc(onComplete = { modelMDoc ->
            println(modelMDoc.documents)
        }, onError = {

        })
    }

    @OptIn(ExperimentalEncodingApi::class)
    @org.junit.Test
    fun `test validity with Byte Array`() {
        val mDoc = MDoc(source = Base64.decode(moreDocsSource))
        mDoc.decodeMDoc(
            onComplete = { docModel ->
                docModel.documents?.forEachIndexed { i, it ->
                    val (isValid, error) = mDoc.verifyValidity(it)
                    val errorToPrint = if (error != null) " error: $error" else ""
                    println("document -> $i: isValid: $isValid$errorToPrint")
                }
            },
            onError = {

            })
    }
}