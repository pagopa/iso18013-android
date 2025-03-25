# iso18013-android

This project contains two main libraries and a sample application demonstrating how to use them:

1. [cbor_implementation](#CBOR).
2. [proximity](#Proximity).

You can enable or disable logging for both libraries via `ProximityLogger` and `CborLogger`. For example, in `MainActivity`:

```kotlin
ProximityLogger.enabled = BuildConfig.DEBUG
CborLogger.enabled = BuildConfig.DEBUG
```

# CBOR

The public classes in this library are:

- COSEManager
- MDoc

The `COSEManager` is designed for signing arbitrary byte arrays using COSE (CBOR Object Signing and Encryption) and for verifying COSE signatures.

Examples: </br>
Sign data:

```kotlin
val data = //your byte array data
when (val result = coseManager.signWithCOSE(
    data = data,
    alias = "pagoPA"
)) {
    is SignWithCOSEResult.Failure -> failureAppDialog(result.msg)
    is SignWithCOSEResult.Success -> {
        // handle result
        signature = result.signature//bytes[]
        publicKey = result.publicKey//bytes[]
    }
}
```

Verify Sign1Message:

```kotlin
// it returns a Boolean
coseManager.verifySign1(
    dataSigned = what,
    publicKey = pubKey
)
```

Examples of how to use `COSEManager` can be found in the `SignAndVerifyViewViewModel` class.

MDoc: </br>

The `MDoc` class is a polymorphic class in Kotlin that returns an object of type `ModelMDoc`. The `ModelMDoc` is a
parser for a CBOR format, which includes a method to convert it to JSON.
The `MDoc` class has three constructors:

1. **Primary Constructor**: This is private and is used internally by the other constructors.
    ```kotlin
    private constructor(source: Any, isByteArray: Boolean = false)
    ```

2. **String Constructor**: This takes a `Base64String` source and sets `isByteArray` to `false`.
    ```kotlin
    constructor(source: String) : this(source, false)
    ```

3. **ByteArray Constructor**: This takes a `bytes[]` source and sets `isByteArray` to `true`.
    ```kotlin
    constructor(source: ByteArray) : this(source, true)
    ```

# Proximity

The public classes here are:

- QrEngagement
- ResponseGenerator

and a data class:

```kotlin
/**
 * BLE Retrieval Method
 * @property peripheralServerMode set if the peripheral server mode is enabled
 * @property centralClientMode set if the central client mode is enabled
 * @property clearBleCache set if the BLE cache should be cleared
 */
data class BleRetrievalMethod(
    val peripheralServerMode: Boolean,
    val centralClientMode: Boolean,
    val clearBleCache: Boolean
) : DeviceRetrievalMethod
```

 ## QrEngagement:

 `QrEngagement` is used to generate and handle QR-based connections for an mdoc session.

 ### Instantiation Example

```kotlin
companion object {
        /**
         * Create an instance and configures the QR engagement.
         * First of all you must call [configure] to build QrEngagementHelper.
         * To accept just some certificates use [withReaderTrustStore] method.
         * To create a QrCode use [getQrCodeString] method.
         * To observe all events call [withListener] method.
         * To close the connection call [close] method.
         */
        fun build(context: Context, retrievalMethods: List<DeviceRetrievalMethod>): QrEngagement {
            return QrEngagement(context).apply {
                this.retrievalMethods = retrievalMethods
                qrEngagementBuilder = QrEngagementHelper.Builder(
                    context,
                    eDevicePrivateKey.publicKey,
                    retrievalMethods.transportOptions,
                    qrEngagementListener,
                    context.mainExecutor()
                ).setConnectionMethods(retrievalMethods.connectionMethods)
            }
        }
    }
```

This is thew way this class is intended to be instantiated. Examples can be found into MasterViewViewModel class.

### Key Methods:</br>

1. **configure**: builds QrEngagementHelper by com.android.identity package and returns QrEngagement instance created
   via QrEngagement.build static method.
   ```kotlin
   fun configure() = apply {
      qrEngagement = qrEngagementBuilder.build()
   }
   ```

2. **withReaderTrustStore**: Method to inject certificates to be verified sent by mdoc verifier app.
   ```kotlin
   /**
   * Use this if you have certificates into your **Raw Resource** folder.
   * *You have still other two methods with [List] of [ByteArray] for raw certificates and [List] of [String] for pem*
   * @param certificates a [List] of [Int] representing your raw resource
   * @return [QrEngagement]
     */
     fun withReaderTrustStore(certificates: List<Int>) = apply {
        certificates.setReaderTrustStore()
     }

   /**
   * Use this if you have certificates **As [ByteArray]**.
   * *You have still other two methods with [List] of [Int] for raw resources and [List] of [String] for pem*
   * @param certificates a [List] of [ByteArray] representing your raw certificates
   * @return [QrEngagement]
     */
     @JvmName("withReaderTrustStore1")
     fun withReaderTrustStore(certificates: List<ByteArray>) = apply {
        certificates.setReaderTrustStore()
     }

   /**
   * Use this if you have certificates **As [String]**.
   * *You have still other two methods with [List] of [Int] for raw resources and [List] of [ByteArray] for raw certificates*
   * @param certificates a [List] of [String] representing your pem certificates
   * @return [QrEngagement]
     */
     @JvmName("withReaderTrustStore2")
     fun withReaderTrustStore(certificates: List<String>) = apply {
        certificates.setReaderTrustStore()
     }
      ```
3. **getQrCodeString**: Gives back QR code string for engagement
   ```kotlin
   fun getQrCodeString() = apply {
      if (!checkQrEngagementInit())
         return ""
     return qrEngagement.deviceEngagementUriEncoded
   }
   ```
4. **withListener**: Starts the listener for qrCodeEngagement
   ```kotlin
   fun withListener(callback: QrEngagementListener) = apply {
     this.listener = callback
   }
   ```
5. **close**: Closes the connection with the mdoc verifier
   ```kotlin
   fun close() {
     if (!checkQrEngagementInit())
         return
     try {
         if (deviceRetrievalHelper != null)
             deviceRetrievalHelper!!.disconnect()
         qrEngagement.close()
     } catch (exception: RuntimeException) {
         ProximityLogger.e(this.javaClass.name, "Error closing QR engagement $exception")
     }
   }
   ```

The listener:

```kotlin
interface QrEngagementListener {
    fun onConnecting()
    fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper)
    fun onCommunicationError(msg: String)
    fun onNewDeviceRequest(request: String?, sessionsTranscript: ByteArray)
    fun onDeviceDisconnected(transportSpecificTermination: Boolean)
}
```

### ResponseGenerator
`ResponseGenerator` is used to create a response in `ByteArray` format for the connected mdoc verifier.

```kotlin
interface Response {
     /**@param [response] [ByteArray] generated for response*/
     fun onResponseGenerated(response: ByteArray)

     /**@param [message] [String] for error reached*/
     fun onError(message: String)
 }

 /**
  * It creates a mdoc response in ByteArray format respect documents requested and disclosed
  * @return[Response.onResponseGenerated] if ByteArray is created without Exceptions, else
  * [Response.onError] if disclosedDocumentsArray is Empty with "no doc found" message or if an
  * [Exception] was reached with [Throwable.message].
  */
 @JvmName("createResponseWithCallback")
 fun createResponse(
     documents: Array<DocRequested>,
     fieldRequestedAndAccepted: String,
     response: Response
 ) {
     val (responseToSend, message) = this.createResponse(
         documents, fieldRequestedAndAccepted
     )
     responseToSend?.let {
         response.onResponseGenerated(it)
     } ?: run {
         response.onError(message)
         ProximityLogger.e(
             "Sending resp",
             "found doc but fail to generate raw response: $message"
         )
     }
 }
```

where `DocRequested` is:

```kotlin
@Parcelize
data class DocRequested(
   val issuerSignedContent: String,
   val alias: String,
   val docType: String
) : Parcelable
```

See in app `MasterViewViewModel.shareInfo` method to understand how to retrieve documents from JSON request and correctly
send to response.

#  ISO 18013-7

The `OpenID4VP` class can be used to generate a `sessionTranscript` to pass to `ResponseGenerator` to use standard ISO 18013-7.
Class parameters can be retrieved by calling backend an getting parameters themselves.
Example:
```kotlin
val sessionTranscript = OpenID4VP(
   clientId,
   responseUri,
   authorizationRequestNonce,
   mdocGeneratedNonce
).createSessionTranscript()
```
Then you can create a device response doing:
```kotlin
val responseGenerator = ResponseGenerator(sessionTranscript)
responseGenerator.createResponse(
    documents,
    fieldRequestedAndAccepted,
    object : ResponseGenerator.Response {
        override fun onResponseGenerated(response: ByteArray) {
            //do what you want with response
        }

        override fun onError(message: String) {
            //ERROR!!
        }
    }
)
```

# Release

This section describes the steps required to publish a new version of the library to Maven. 

## 1. Bump the Version
Open your Gradle file, `proximity/build.gradle.kts` and/or `cbor/build.gradle.kts` and update the version in the `mavenPublishing` block:

```diff
mavenPublishing {
-    coordinates("it.pagopa.io.wallet.proximity", "proximity", "x.y.z")
+    coordinates("it.pagopa.io.wallet.proximity", "proximity", "x.y.z+1")
}
```

Replace `proximity` with `cbor` (and the respective coordinates) if you are releasing that library instead.

## 2. Create and Push the Tag
Create a git tag corresponding to your version, then push it to your remote repository. For example:

```perl
git tag proximity-v1.1.1
git push origin proximity-v1.1.1
(Adjust the tag name according to your library and version.)
```

## 3. Automatic Release to Maven
Once the tag is pushed, the release process will trigger automatically. Your library will be published to Maven if the pipeline completes successfully.

## 4. Automatic Approval in Maven
After the release is published, Maven automatically approve the new version. This final step ensures that the artifact is visible and available for consumers.

