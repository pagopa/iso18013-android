package it.pagopa.iso_android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX
import android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
import android.security.keystore.KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec


/**
 * A secure storage class for writing on encrypted files.
 * This class has a private constructor, thus instances must be created by using its [Builder].
 * @property context the application context.
 * @property storageDirectory the directory where the files will be stored.
 * @property useEncryption if manual encryption has to be used.
 */
class SecureStorage private constructor(
    private val context: Context,
    private val storageDirectory: File,
    private val useEncryption: Boolean
) {
    private val keyAlias = "${PREFIX}_KEY_$storageDirectory"

    /**
     * Removes the file associated with the given [key].
     * @param key the identifier of the file.
     * @throws SecureStorageException if an error occurs while deleting the file.
     */
    fun remove(key: String) {
        try {
            AtomicFile(getFile(key)).delete()
        } catch (e: Exception) {
            throw SecureStorageException("An error occurred while deleting the file", e)
        }
    }

    /**
     * Clears [storageDirectory] from every file.
     */
    fun clear() {
        storageDirectory.listFiles()
            ?.filter { it.name.startsWith(PREFIX) }
            ?.forEach { it.delete() }
    }

    /**
     * Enumerates keys for each file in [storageDirectory].
     * @return a list containing each file name UTF-8 encoded.
     * @throws SecureStorageException when UTF-8 encoding is not supported.
     */
    fun keys(): List<String> {
        return storageDirectory.listFiles()?.filter { it.name.startsWith(PREFIX) }
            ?.map { file ->
                try {
                    URLDecoder.decode(file.name.substring(PREFIX.length), "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                    throw SecureStorageException(
                        "UTF-8 encoding is not supported",
                        e
                    )
                }
            } ?: emptyList()
    }

    /**
     * Gets the content of the file associated with [key].
     * This function checks whether or not the file has been manually encrypted or not by parsing
     * the first [PREFIX_ENCRYPTED_SIZE] bytes and comparing it with a known set of values.
     * If the file has been manually encrypted then it calls the decryption function, otherwise
     * it just reads the content of the file. See [put] for more information about encryption.
     * The prefix is stripped in both cases.
     * @param key the identifier of the file.
     * @return an array of byte containing the content of the file associated with the [key].
     * null if the key is not mapped to any value or if the encryption key doesn't exist when
     * manually [useEncryption] is true
     * @throws SecureStorageException when the file has a size less than [PREFIX_ENCRYPTED_SIZE],
     * when the file has an unrecognized prefix, or when an unexpected error occurs.
     */
    fun get(key: String): ByteArray? = runCatching {
        val file = AtomicFile(getFile(key))
        val data = file.readFully()
        check(data.size >= PREFIX_ENCRYPTED_SIZE) { "File must be bigger than $PREFIX_ENCRYPTED_SIZE bytes" }
        val prefix = data.copyOfRange(0, PREFIX_ENCRYPTED_SIZE)
        when {
            prefix.contentEquals(MANUAL_ENCRYPTED) -> {
                getSecretKey()?.let { key ->
                    decrypt(key, data.copyOfRange(PREFIX_ENCRYPTED_SIZE, data.size))
                }
            }

            prefix.contentEquals(AUTOMATIC_ENCRYPTED) -> {
                data.copyOfRange(PREFIX_ENCRYPTED_SIZE, data.size)
            }

            else -> throw IllegalStateException("Unrecognized file prefix")
        }
    }.getOrElse { e ->
        when (e) {
            is FileNotFoundException -> null
            else -> throw SecureStorageException("Unexpected exception", e)
        }
    }

    /**
     * Puts [data] in a file in [storageDirectory].
     * The content of the file might be manually encrypted is [useEncryption] is true, otherwise
     * it won't be encrypted to avoid double encryption if [storageDirectory] is already encrypted.
     * The [Builder] class also allows to disable or enable it manually, thus making double
     * encryption or no encryption at all possible. With the default behavior the content will
     * always be encrypted, automatically if [storageDirectory] is already encrypted or manually
     * otherwise.
     * Manual encryption uses AES-128 GCM with hardware backed key for each file in [storageDirectory]
     * Automatic encryption possibly uses [file-based encryption](https://source.android.com/docs/security/features/encryption/file-based)
     * thus AES-256 in XTS mode for file content and AES-256 in CBC-CTS mode for file names.
     * @param key the identifier of the file.
     * @param data the data to be written.
     * @throws SecureStorageException when hardware backed keys are not supported for manual
     * encryption or when an error occurs while writing data.
     */
    fun put(key: String, data: ByteArray) {
        /**
         * Atomic file guarantees file integrity by ensuring that a file has been completely written
         * ensuring no invalid files are written in case of a crash thus preventing partial files.
         */
        synchronized(this) {
            val file = AtomicFile(getFile(key))
            var outputStream: FileOutputStream? = null
            try {
                outputStream = file.startWrite()
                if (useEncryption) {
                    val secretKey = getSecretKey() ?: generateHardwareBackedSecretKey()
                    if (!isKeyHardwareBacked(secretKey)) throw IllegalStateException("Hardware backed keys not supported")
                    outputStream.write(MANUAL_ENCRYPTED)
                    outputStream.write(encrypt(secretKey, data))
                } else {
                    outputStream.write(AUTOMATIC_ENCRYPTED)
                    outputStream.write(data)
                }
                file.finishWrite(outputStream)
            } catch (e: Exception) {
                outputStream?.let {
                    file.failWrite(it)
                }
                throw SecureStorageException("Error while writing data", e)
            }
        }
    }

    /**
     * Gets an already existing [SecretKey] used to encrypt a file content.
     * @returns the [SecretKey] or null if it doesn't exist.
     * @throws IllegalStateException when the request key has not been found.
     */
    private fun getSecretKey(): SecretKey? {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keystore.getEntry(keyAlias, null) ?: return null
        return (entry as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Generates an AES-128 GCM hardware backed [SecretKey] for symmetric encryption.
     * Uses StrongBox if supported, TEE otherwise.
     * @returns the generated key pair.
     */
    private fun generateHardwareBackedSecretKey(): SecretKey {
        val hasStrongBox =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && this.context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_STRONGBOX_KEYSTORE
            )
        val builder = KeyGenParameterSpec.Builder(
            keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setKeySize(128)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (hasStrongBox) builder.setIsStrongBoxBacked(true)
            builder.setUnlockedDeviceRequired(true)
        }
        val keyPairGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        keyPairGenerator.init(builder.build())
        val secretKey = keyPairGenerator.generateKey()
        if (!isKeyHardwareBacked(secretKey)) throw IllegalStateException("Hardware backed keys not supported")
        return secretKey
    }

    /**
     * Checks whether or not a [SecretKey] is hardware backed (TEE/StrongBox) or not.
     * @param key the [SecretKey] to be checked.
     * @returns true if the key is hardware backed according to its [security level](https://developer.android.com/reference/android/security/keystore/KeyProperties)
     * with a fallback to the [isInsideSecureHardware](https://developer.android.com/reference/android/security/keystore/KeyInfo#isInsideSecureHardware())
     * for version codes older than [Build.VERSION_CODES.S].
     * False otherwise.
     */
    private fun isKeyHardwareBacked(key: SecretKey): Boolean {
        val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // https://developer.android.com/reference/android/security/keystore/KeyProperties
            keyInfo.securityLevel == SECURITY_LEVEL_TRUSTED_ENVIRONMENT
                    || keyInfo.securityLevel == SECURITY_LEVEL_STRONGBOX
                    || keyInfo.securityLevel == SECURITY_LEVEL_UNKNOWN_SECURE
        } else {
            @Suppress("DEPRECATION") return keyInfo.isInsideSecureHardware
        }
    }

    /**
     * Gets a file reference for a given key.
     * Each file is stored in [storageDirectory] with a fixed [PREFIX].
     * @param key the identifier of the file.
     * @return the file handler associated with [key].
     */
    private fun getFile(key: String): File {
        val fileName = PREFIX + URLEncoder.encode(key, "UTF-8")
        return File(storageDirectory, fileName)
    }


    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val PREFIX = "SECURE_STORAGE"

        // Because some older Android versions have a buggy Android Keystore where encryption
        // only works with small amounts of data (b/234563696) chop the cleartext into smaller
        // chunks and encrypt them separately.
        private const val CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE = 16384

        // 96 bit but we also use an unique key for each device
        private const val GCM_IV_LENGTH = 12

        // We use AES-128 GCM with no padding for the cipher
        private const val CIPHER_TYPE = "AES/GCM/NoPadding"

        // We prepend these two strings to distinguish manually encrypted from automatically encrypted files
        private const val PREFIX_ENCRYPTED_SIZE = 4
        private val MANUAL_ENCRYPTED = "manu".toByteArray()
        private val AUTOMATIC_ENCRYPTED = "auto".toByteArray()

        /**
         * Decrypts [encryptedData] with [secretKey] using [CIPHER_TYPE].
         * The function extracts the IV from the first [GCM_IV_LENGTH] bytes of [encryptedData]
         * and uses it along with [secretKey] to decrypt [encryptedData].
         * @return a decrypted byte array.
         * @throws IllegalStateException when an error occurs while decrypting.
         */
        private fun decrypt(
            secretKey: SecretKey, encryptedData: ByteArray
        ): ByteArray {
            try {
                val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
                val cipherText = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
                val cipher = Cipher.getInstance(CIPHER_TYPE)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                val result = ByteArrayOutputStream()
                var isLastChunk = false
                // We track how many bytes we already read
                var offset = 0
                while (!isLastChunk) {
                    var chunkSize = cipherText.size - offset
                    if (chunkSize <= CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE) {
                        isLastChunk = true
                    } else {
                        chunkSize = CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE
                    }
                    if (isLastChunk) {
                        /* Decryption happens here */
                        val decryptedData = cipher.doFinal(cipherText, offset, chunkSize)
                        result.write(decryptedData)
                    } else {
                        /* The SP800-38D specs calls for the plaintext not to be released if the authentication
                        fails, which can only be determined at .doFinal time, thus this doesn't return
                        anything */
                        cipher.update(cipherText, offset, chunkSize)
                    }
                    offset += chunkSize
                }
                return result.toByteArray()
            } catch (e: Exception) {
                throw IllegalStateException("Error decrypting data", e)
            }
        }

        /**
         * Encrypts [data] with [secretKey] using [CIPHER_TYPE].
         * The prepend the IV and then we encrypt [data] by using small chunks with [CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE]
         * as size.
         * @throws IllegalStateException when an error occurs while encrypting.
         */
        private fun encrypt(
            secretKey: SecretKey, data: ByteArray
        ): ByteArray {
            try {
                val result = ByteArrayOutputStream()
                /* We don't need to specify a random IV as Cipher already generates a random IV when needed.
                 We need to prepend it and we know for sure its size is [GCM_IV_LENGTH] */
                val cipher = Cipher.getInstance(CIPHER_TYPE)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                result.write(cipher.iv)
                var isLastChunk = false
                // We track how many bytes we already wrote
                var offset = 0
                while (!isLastChunk) {
                    var chunkSize = data.size - offset
                    if (chunkSize <= CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE) {
                        isLastChunk = true
                    } else {
                        chunkSize = CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE
                    }
                    val cipherTextForChunk = if (isLastChunk) {
                        /* This also appends the authentication tag with the last chunk. */
                        cipher.doFinal(data, offset, chunkSize)
                    } else {
                        cipher.update(data, offset, chunkSize)
                    }
                    result.write(cipherTextForChunk)
                    offset += chunkSize
                }
                return result.toByteArray()
            } catch (e: Exception) {
                throw IllegalStateException("Error encrypting data", e)
            }
        }
    }

    /**
     * Builder class for creating immutable instances of [SecureStorage].
     * @property context the application context.
     * @property storageDirectory the directory where the files will be stored.
     */
    class Builder(private var context: Context, private var storageDirectory: File) {
        /**
         * This checks whether or not the storageDirectory is already encrypted.
         * This might be the case for app-specific files (https://developer.android.com/training/data-storage/app-specific).
         * If true, files are encrypted accordingly (https://source.android.com/docs/security/features/encryption/file-based):
         * Encrypt file contents with AES-256 in XTS mode
         * Encrypt file names with AES-256 in CBC-CTS mode
         */
        private var useEncryption = when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) -> {
                !((context.getSystemService(StorageManager::class.java) as StorageManager).isEncrypted(
                    storageDirectory
                ))
            }

            else -> true
        }

        private var enforceManualEncryption = false

        /**
         * Forces manual encryption. By default, the builder takes care of
         * using an appropriate [useEncryption] value according to the
         * provided [storageDirectory] which might be already encrypted.
         */
        fun setEnforceManualEncryption(isEnforced: Boolean) =
            apply { this.enforceManualEncryption = isEnforced }

        /**
         * Builds an instance of [SecureStorage].
         */
        fun build() =
            SecureStorage(context, storageDirectory, useEncryption || enforceManualEncryption)
    }
}