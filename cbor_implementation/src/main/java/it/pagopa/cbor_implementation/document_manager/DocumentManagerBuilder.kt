package it.pagopa.cbor_implementation.document_manager

import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.storage.StorageEngine
import it.pagopa.cbor_implementation.extensions.isDeviceSecure
import kotlinx.io.files.Path
import java.io.File
import kotlin.Boolean

class DocumentManagerBuilder(val context: Context) {
    private val _context = context.applicationContext
    var userAuth: Boolean = context.isDeviceSecure
    var userAuthTimeoutInMillis: Long = AUTH_TIMEOUT
    var checkPublicKeyBeforeAdding: Boolean = true

    /**
     * Sets whether to require user authentication to access the document.
     * If the device is not secured, this will be set to false.
     * @param enable
     * @return [DocumentManagerBuilder]
     */
    fun enableUserAuth(enable: Boolean) = apply {
        this.userAuth = enable && context.isDeviceSecure
    }

    /**
     * Sets the timeout in milliseconds for user authentication.
     *
     * @param timeoutInMillis timeout in milliseconds for user authentication
     * @return [DocumentManagerBuilder]
     */
    fun userAuthTimeout(timeoutInMillis: Long) =
        apply { this.userAuthTimeoutInMillis = timeoutInMillis }

    /**
     * Sets whether to check public key in MSO before adding document to storage.
     * By default this is set to true.
     * This check is done to prevent adding documents with public key that is not in MSO.
     * The public key from the [it.pagopa.cbor_implementation.document_manager.document.UnsignedDocument] must match the public key in MSO.
     *
     * @see [DocumentManager.storeDocument]
     *
     * @param check
     */
    fun checkPublicKeyBeforeAdding(check: Boolean) =
        apply { this.checkPublicKeyBeforeAdding = check }

    /**
     * The directory to store data files in.
     * By default, the [Context.getNoBackupFilesDir] is used.
     *
     * @return [DocumentManagerBuilder]
     */
    internal val storageEngine: StorageEngine by lazy {
        val path = Path(File(_context.noBackupFilesDir.path, "pagopa-identity.bin").path)
        AndroidStorageEngine.Builder(_context, path)
            .setUseEncryption(true)
            .build()
    }

    internal val androidSecureArea: AndroidKeystoreSecureArea by lazy {
        AndroidKeystoreSecureArea(_context, storageEngine)
    }

    companion object {
        const val AUTH_TIMEOUT = 30_000L
        val AUTH_TYPE = setOf(UserAuthenticationType.BIOMETRIC, UserAuthenticationType.LSKF)
    }
}