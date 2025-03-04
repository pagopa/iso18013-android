package it.pagopa.io.wallet.cbor

import com.upokecenter.cbor.CBORException
import it.pagopa.io.wallet.cbor.exception.DocTypeNotValid
import it.pagopa.io.wallet.cbor.exception.MandatoryFieldNotFound
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.cbor.impl.MDoc
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class MDocDataTest {
    @org.junit.Test
    fun `test doc type not valid exception`() {
        val doc = Document(
            docType = "bla bla bla",
            issuerSigned = null, rawValue = byteArrayOf()
        )
        assert(doc.verifyValidity().second is DocTypeNotValid)
    }

    @org.junit.Test
    fun `test encode and decode`() {
        val mDoc = MDoc(source = oneDocSource)
        mDoc.decodeMDoc(onComplete = { modelMDoc ->
            println(modelMDoc.documents)
            assert(modelMDoc.documents?.size!! > 0)
            val document = modelMDoc.documents?.get(0)!!
            assert(document.docType == "org.iso.18013.5.1.mDL")
            assert(document.issuerSigned!!.nameSpaces!!["org.iso.18013.5.1"]!=null)
        }, onError = {

        })
    }

    @org.junit.Test
    fun `test validity`() {
        val mDoc = MDoc(source = moreDocsSource)
        mDoc.decodeMDoc(
            onComplete = { docModel ->
                docModel.documents?.forEachIndexed { i, it ->
                    val (isValid, error) = it.verifyValidity()
                    val errorToPrint = if (error != null) " error: $error" else ""
                    println("document -> $i: isValid: $isValid$errorToPrint")
                    assert(if (i == 0) isValid else !isValid)
                    if (i == 1) {
                        assert(error is MandatoryFieldNotFound)
                    }
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
            assert(modelMDoc.documents?.size!! > 0)
            val document = modelMDoc.documents?.get(0)!!
            assert(document.docType == "org.iso.18013.5.1.mDL")
            assert(document.issuerSigned!!.nameSpaces!!["org.iso.18013.5.1"]!=null)
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
                    val (isValid, error) = it.verifyValidity()
                    val errorToPrint = if (error != null) " error: $error" else ""
                    println("document -> $i: isValid: $isValid$errorToPrint")
                    assert(if (i == 0) isValid else !isValid)
                    if (i == 1) {
                        assert(error is MandatoryFieldNotFound)
                    }
                }
            },
            onError = {

            })
    }


    @OptIn(ExperimentalEncodingApi::class)
    @org.junit.Test
    fun base64NotValid() {
        val mDoc = MDoc(source = Base64.decode("Test"))
        mDoc.decodeMDoc(onComplete = { modelMDoc ->
            println(modelMDoc.documents)
        }, onError = {
            assert(it is CBORException)
        })
    }
}