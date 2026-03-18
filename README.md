# ISO18013-Android

An Android implementation of the ISO 18013-5 standard for mobile driving licenses and digital identity documents.

## Project Overview

This project implements the ISO 18013-5 standard for mobile driving licenses (mDL) and other identity documents on Android devices. It exposes two independent Android libraries:

- **`proximity`** — handles device engagement (NFC, QR Code), secure transport (BLE, NFC data transfer) and document response generation.
- **`cbor`** — handles CBOR encoding/decoding, COSE signing/verification and mDL document parsing.

## Requirements

- Android SDK 26+
- Kotlin 1.8+
- Android device with NFC and/or Bluetooth support
- Android Studio Arctic Fox or later

## Getting Started

1. Clone this repository:

   ```bash
   git clone https://github.com/pagopa/iso18013-android.git
   cd iso18013-android
   ```

2. Open the project in Android Studio.

3. Build the project:
   ```bash
   ./gradlew build
   ```

## Running Tests

### Unit Tests

To run unit tests for the CBOR module:

```bash
./gradlew :cbor:test
```

To run unit tests for the Proximity module:

```bash
./gradlew :proximity:test
```

To run all unit tests:

```bash
./gradlew test
```

## Release

This section describes the steps required to publish a new version of the library to Maven.

### 1. Bump the Version

Open your Gradle file, `proximity/build.gradle.kts` and/or `cbor/build.gradle.kts` and update the version in the `mavenPublishing` block:

```diff
mavenPublishing {
-    coordinates("it.pagopa.io.wallet.proximity", "proximity", "x.y.z")
+    coordinates("it.pagopa.io.wallet.proximity", "proximity", "x.y.z+1")
}
```

Replace `proximity` with `cbor` (and the respective coordinates) if you are releasing that library instead.
Commit the changes to the remote repository with a commit message like `chore: Release proximity/cbor vx.y.z`.

### 2. Create and Push the Tag

Create a git tag corresponding to your version, then push it to your remote repository. For example:

```bash
git tag proximity-v1.1.1
git push origin proximity-v1.1.1
```

Adjust the tag name according to your library and version.

### 3. Automatic Release to Maven

Once the tag is pushed, the release process on Maven will be triggered automatically through GitHub Actions.

## Logging

You can enable or disable logging for both libraries via `ProximityLogger` and `CborLogger`. For example, in `MainActivity`:

```kotlin
ProximityLogger.enabled = BuildConfig.DEBUG
CborLogger.enabled = BuildConfig.DEBUG
```

---

## Modules

### CBOR

The CBOR module handles Concise Binary Object Representation (CBOR) encoding and decoding, which is the data format specified by ISO 18013-5 for identity documents. It includes:

- CBOR parsing and encoding
- COSE (CBOR Object Signing and Encryption) implementation
- Document data structures

The public classes in this library are:

- `COSEManager`
- `MDoc`

The `COSEManager` is designed for signing arbitrary byte arrays using COSE (CBOR Object Signing and Encryption) and for verifying COSE signatures.

Sign data:

```kotlin
val data = // your byte array data
when (val result = coseManager.signWithCOSE(data = data, alias = "pagoPA")) {
    is SignWithCOSEResult.Failure -> failureAppDialog(result.msg)
    is SignWithCOSEResult.Success -> {
        signature = result.signature  // ByteArray
        publicKey = result.publicKey  // ByteArray
    }
}
```

Verify a Sign1Message:

```kotlin
// returns Boolean
coseManager.verifySign1(dataSigned = what, publicKey = pubKey)
```

The `MDoc` class is a polymorphic parser for CBOR-encoded identity documents that returns a `ModelMDoc` object, which can be converted to JSON.

```kotlin
// From Base64 string
val mdoc = MDoc(base64String)

// From raw bytes
val mdoc = MDoc(byteArray)
```

---

### Proximity

The Proximity module manages secure communication between devices for document exchange according to ISO 18013-5. It supports:

- **NFC device engagement** (ISO 18013-5 NFC Static Handover)
- **QR Code device engagement**
- **BLE data transfer** (Peripheral Server Mode / Central Client Mode)
- **NFC-only data transfer** (engagement + data exchange entirely over NFC)
- Reader certificate validation (Reader Trust Store)
- Document request and response protocols

#### Retrieval Methods

Two `DeviceRetrievalMethod` implementations are available to configure the transport layer:

```kotlin
/**
 * BLE Retrieval Method.
 * @property peripheralServerMode enables BLE Peripheral Server mode
 * @property centralClientMode enables BLE Central Client mode
 * @property clearBleCache clears the BLE GATT cache before connecting
 */
data class BleRetrievalMethod(
    val peripheralServerMode: Boolean,
    val centralClientMode: Boolean,
    val clearBleCache: Boolean
) : DeviceRetrievalMethod

/**
 * NFC Retrieval Method.
 * Used when the entire data transfer must happen over NFC (no BLE).
 * @property commandDataFieldMaxLength max APDU command data length (default 256)
 * @property responseDataFieldMaxLength max APDU response data length (default 256)
 */
data class NfcRetrievalMethod(
    val commandDataFieldMaxLength: Long = 256L,
    val responseDataFieldMaxLength: Long = 256L
) : DeviceRetrievalMethod
```

---

### NFC Engagement — `NfcEngagementService`

`NfcEngagementService` is an abstract `HostApduService` that manages ISO 18013-5 NFC engagement. It supports two operating modes depending on the `DeviceRetrievalMethod` combination passed at setup:

| Mode                              | `retrievalMethods`                                | Description                                                                |
|-----------------------------------|---------------------------------------------------|----------------------------------------------------------------------------|
| **NFC engagement + BLE transfer** | `[NfcRetrievalMethod(), BleRetrievalMethod(...)]` | NFC is used only for the initial handshake; data transfer happens over BLE |
| **NFC-only**                      | `[NfcRetrievalMethod()]`                          | Both engagement and data transfer happen entirely over NFC (APDU-based)    |

#### Step 1 — Implement the service

Extend `NfcEngagementService` in your application module:

```kotlin
class MyNfcEngagementService : NfcEngagementService()
```

#### Step 2 — Declare the service in `AndroidManifest.xml`

```xml
<service
    android:name=".MyNfcEngagementService"
    android:exported="true"
    android:label="@string/nfc_engagement_service_desc"
    android:permission="android.permission.BIND_NFC_SERVICE">
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE"/>
    </intent-filter>
    <meta-data
        android:name="android.nfc.cardemulation.host_apdu_service"
        android:resource="@xml/nfc_engagement_apdu_service" />
</service>
```

> The `nfc_engagement_apdu_service` XML resource must declare the AIDs supported by the service (NDEF AID `D2760000850101` and mDL AID `A0000002480400`).

#### Step 3 — Initialise the service before navigating to the NFC screen

Before navigating to the NFC engagement screen, call `NfcEngagementEventBus.setupNfcService(...)` to configure the session. This call is non-suspending and can be invoked from any coroutine context or UI thread:

```kotlin
// NFC engagement + BLE data transfer
NfcEngagementEventBus.setupNfcService(
    retrievalMethods = listOf(
        NfcRetrievalMethod(),
        BleRetrievalMethod(
            peripheralServerMode = true,
            centralClientMode = false,
            clearBleCache = true
        )
    ),
    readerTrustStore = listOf(listOf(R.raw.eudi_pid_issuer_ut)),  // raw resource certificates
    inactivityTimeoutSeconds = 15
)

// NFC-only (engagement + data transfer entirely over NFC)
NfcEngagementEventBus.setupNfcService(
    retrievalMethods = listOf(NfcRetrievalMethod()),
    readerTrustStore = listOf(listOf(R.raw.eudi_pid_issuer_ut))
)
```

The `readerTrustStore` parameter accepts three formats:
- `List<List<Int>>` — Android raw resource IDs (`.cer` files in `res/raw/`)
- `List<List<ByteArray>>` — raw DER-encoded certificate bytes
- `List<List<String>>` — PEM-encoded certificate strings

#### Step 4 — Enable/disable the foreground preferred service

Call `enable` and `disable` to register your service as the preferred HCE service while your activity is in the foreground:

```kotlin
override fun onResume() {
    super.onResume()
    val status = NfcEngagementService.enable(this, MyNfcEngagementService::class.java)
    if (!status.canWork()) {
        // Handle incompatible device: show a warning to the user
    }
}

override fun onPause() {
    super.onPause()
    NfcEngagementService.disable(this)
}
```

`enable` returns an `HceServiceStatus` that describes the HCE readiness of the device:

| Status                         | Meaning                                                      |
|--------------------------------|--------------------------------------------------------------|
| `FullyOperational`             | Service is registered and ready                              |
| `RequiresUserSelection`        | The user must manually select the service in system settings |
| `NotRegistered`                | Service is not registered for the required AIDs              |
| `NfcNotSupported`              | Device has no NFC hardware                                   |
| `HceNotSupported`              | Device does not support Host Card Emulation                  |
| `NfcDisabled`                  | NFC is turned off in system settings                         |
| `ServiceNotDeclaredInManifest` | Service missing from `AndroidManifest.xml`                   |

Use `status.canWork()` to determine whether to proceed or notify the user.

#### Step 5 — Observe NFC engagement events

Subscribe to `NfcEngagementEventBus.events` (a `SharedFlow`) to react to the engagement lifecycle. All events are instances of `NfcEngagementEvent`:

```kotlin
viewModelScope.launch {
    NfcEngagementEventBus.events.collect { event ->
        when (event) {
            is NfcEngagementEvent.Connecting -> { /* device is connecting */ }

            is NfcEngagementEvent.Connected -> {
                // event.device: DeviceRetrievalHelperWrapper
                // Store it to send the response later
                deviceConnected = event.device
            }

            is NfcEngagementEvent.DocumentRequestReceived -> {
                // event.request: String? — JSON with the verifier's document request
                // event.sessionTranscript: ByteArray — required for ResponseGenerator
                // event.onlyNfc: Boolean — true when using NfcRetrievalMethod only
                //   (in NFC-only mode the response must be sent automatically,
                //    without waiting for user confirmation)
                val request = event.request.orEmpty()
                // Show a consent UI to the user before disclosing data
                manageRequestFromDeviceUi(event.sessionTranscript, event.onlyNfc)
            }

            is NfcEngagementEvent.Disconnected -> {
                // event.transportSpecificTermination: Boolean
                // true = session terminated correctly by the verifier
            }

            is NfcEngagementEvent.Error -> {
                // event.error: Throwable
            }

            is NfcEngagementEvent.DocumentSent -> { /* document sent successfully */ }

            is NfcEngagementEvent.NotSupported -> { /* NFC not supported on device */ }

            is NfcEngagementEvent.NfcOnlyEventListener -> {
                // event.event: OnlyNfcEvents
                // OnlyNfcEvents.NFC_ENGAGEMENT_STARTED or DATA_TRANSFER_STARTED
            }
        }
    }
}
```

> **Important:** `NfcEngagementEventBus.events` uses `replay = 0`. You must be subscribed before the engagement starts. Start collecting in `onResume` or in a `LaunchedEffect` that is active before NFC is tapped.

#### Step 6 — Build and send the response

Once the user has consented (or immediately in NFC-only mode), use `ResponseGenerator` to build the CBOR response and send it, onlyNfc means that request is from a fully NFC exchange:

```kotlin
ResponseGenerator(sessionsTranscript = sessionTranscript)
    .createResponse(
        documents = docRequested.toTypedArray(),        // Array<DocRequested>
        fieldRequestedAndAccepted = acceptedFieldsJson, // JSON from nfcOnlyFieldAcceptation
        response = object : ResponseGenerator.Response {
            override fun onResponseGenerated(response: ByteArray) {
                // Send via DeviceRetrievalHelperWrapper (NFC+BLE or NFC-only)
                if(onlyNfc){
                   val ok = NfcEngagementEventBus.sendDocumentResponse(response)
                   ProximityLogger.i("RESPONSE SENT", ok.toString())
                   return
                }
                deviceConnected?.sendResponse(response, SessionDataStatus.SESSION_DATA.value)
            }
            override fun onError(message: String) {
                deviceConnected?.sendResponse(null, SessionDataStatus.ERROR_CBOR_DECODING.value)
            }
        }
    )
```

---

### QR Code Engagement — `QrEngagement`

`QrEngagement` is used to generate and handle QR-based device engagement. After engagement, the data transfer happens over BLE.

#### Instantiation

```kotlin
val engagement = QrEngagement.build(
    context = context,
    retrievalMethods = listOf(
        BleRetrievalMethod(
            peripheralServerMode = true,
            centralClientMode = false,
            clearBleCache = true
        )
    )
).configure()
```

#### Key Methods

| Method                        | Description                                                            |
|-------------------------------|------------------------------------------------------------------------|
| `configure()`                 | Builds the internal `QrEngagementHelper` and returns the instance      |
| `getQrCodeString()`           | Returns the URI-encoded device engagement string to embed in a QR code |
| `withReaderTrustStore(certs)` | Injects verifier certificates for reader authentication                |
| `withListener(callback)`      | Registers an `EngagementListener` for session events                   |
| `close()`                     | Terminates the session and releases resources                          |

#### Listener

```kotlin
engagement.withListener(object : EngagementListener {
    override fun onDeviceConnecting() { }
    override fun onDeviceConnected(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) { }
    override fun onError(error: Throwable) { }
    override fun onDocumentRequestReceived(request: String?, sessionsTranscript: ByteArray) { }
    override fun onDeviceDisconnected(transportSpecificTermination: Boolean) { }
})
```

`withReaderTrustStore` accepts the same three formats as `NfcEngagementEventBus.setupNfcService`:

```kotlin
// Raw resource IDs
engagement.withReaderTrustStore(listOf(R.raw.eudi_pid_issuer_ut))

// ByteArray certificates
engagement.withReaderTrustStore(listOf(certByteArray))

// PEM strings
engagement.withReaderTrustStore(listOf(pemString))
```

---

### ResponseGenerator

`ResponseGenerator` creates the CBOR-encoded device response to return to the verifier.

```kotlin
data class DocRequested(
    val issuerSignedContent: String, // Base64-encoded IssuerSigned CBOR
    val alias: String,               // Android Keystore alias used to sign
    val docType: String              // e.g. "org.iso.18013.5.1.mDL"
) : Parcelable
```

```kotlin
ResponseGenerator(sessionsTranscript = sessionsTranscript)
    .createResponse(
        documents = arrayOf(
            DocRequested(
                issuerSignedContent = issuerSignedBase64,
                alias = "myKeyAlias",
                docType = "org.iso.18013.5.1.mDL"
            )
        ),
        fieldRequestedAndAccepted = """
            {
              "org.iso.18013.5.1.mDL": {
                "org.iso.18013.5.1": {
                  "family_name": true,
                  "given_name": true,
                  "birth_date": true,
                  "document_number": true,
                  "portrait": true
                }
              }
            }
        """.trimIndent(),
        response = object : ResponseGenerator.Response {
            override fun onResponseGenerated(response: ByteArray) {
                // Send response to verifier
            }
            override fun onError(message: String) {
                // Handle error
            }
        }
    )
```

---

## ISO 18013-7

The `OpenID4VP` class can be used to generate a `sessionTranscript` compatible with ISO 18013-7 (OpenID4VP). Parameters are typically obtained from a backend or authorization request:

```kotlin
val sessionTranscript = OpenID4VP(
    clientId = clientId,
    responseUri = responseUri,
    authorizationRequestNonce = authorizationRequestNonce,
    mdocGeneratedNonce = mdocGeneratedNonce
).createSessionTranscript()

ResponseGenerator(sessionTranscript).createResponse(
    documents = documents,
    fieldRequestedAndAccepted = acceptedJson,
    response = object : ResponseGenerator.Response {
        override fun onResponseGenerated(response: ByteArray) { /* send response */ }
        override fun onError(message: String) { /* handle error */ }
    }
)
```

---

## Permissions

### Bluetooth

Declare the following permissions in `AndroidManifest.xml` for BLE-based transport:

```xml
<!-- Android 12+ (API 31+): must be requested at runtime -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- Android 11 and below (API 30-): ACCESS_FINE_LOCATION must be requested at runtime -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />
```

### NFC

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
<uses-feature android:name="android.hardware.nfc.hce" android:required="true" />
```
