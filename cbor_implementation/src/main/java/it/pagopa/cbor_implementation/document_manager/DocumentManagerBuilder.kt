@file:Suppress("UNUSED")

package it.pagopa.cbor_implementation.document_manager

import android.app.KeyguardManager
import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.storage.StorageEngine
import kotlinx.io.files.Path
import java.io.File
import kotlin.Boolean

class DocumentManagerBuilder(val context: Context) {
    private val _context = context.applicationContext
    var storageDir: File = _context.noBackupFilesDir
    var useEncryption: Boolean = true
    var userAuth: Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    var userAuthTimeoutInMillis: Long = AUTH_TIMEOUT
    var checkPublicKeyBeforeAdding: Boolean = true

    /**
     * The directory to store data files in.
     * By default, the [Context.getNoBackupFilesDir] is used.
     *
     * @param storageDir
     * @return [DocumentManagerBuilder]
     */
    fun withStorageDir(storageDir: File) = apply {
        this.storageDir = storageDir
    }

    /**
     * Sets whether to encrypt the values stored on disk.
     * Note that keys are not encrypted, only values.
     * By default, this is set to true.
     *
     * @param useEncryption
     * @return [DocumentManagerBuilder]
     */
    fun useEncryption(useEncryption: Boolean) = apply { this.useEncryption = useEncryption }

    /**
     * Sets whether to require user authentication to access the document.
     * If the device is not secured, this will be set to false.
     * @param enable
     * @return [DocumentManagerBuilder]
     */
    fun enableUserAuth(enable: Boolean) = apply {
        this.userAuth = enable && context.getSystemService(KeyguardManager::class.java).isDeviceSecure
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
     * The public key from the [UnsignedDocument] must match the public key in MSO.
     *
     * @see [DocumentManager.storeIssuedDocument]
     *
     * @param check
     */
    fun checkPublicKeyBeforeAdding(check: Boolean) =
        apply { this.checkPublicKeyBeforeAdding = check }
    /**
     * The directory to store data files in.
     * By default, the [Context.getNoBackupFilesDir] is used.
     *
     * @param storageDir
     * @return [DocumentManagerBuilder]
     */
    internal val storageEngine: StorageEngine by lazy {
        val path = Path(File(storageDir.path, "pagopa-identity.bin").path)
        AndroidStorageEngine.Builder(_context, path)
            .setUseEncryption(useEncryption)
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