package com.pratyush.orbitmobile.core.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pratyush.orbitmobile.core.common.Constants
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Interface contract for secure credential and token storage using Android Keystore.
 */
interface KeystoreManager {
    fun encrypt(plainText: ByteArray): Pair<ByteArray, ByteArray>
    fun decrypt(cipherText: ByteArray, initializationVector: ByteArray): ByteArray
    fun hasKey(alias: String = Constants.Security.KEYSTORE_ALIAS): Boolean
}

/**
 * Default Android Keystore implementation using AES/GCM/NoPadding.
 */
class DefaultKeystoreManager : KeystoreManager {

    private val keyStore: KeyStore = KeyStore.getInstance(Constants.Security.ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(alias: String = Constants.Security.KEYSTORE_ALIAS): SecretKey {
        val existingKey = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            Constants.Security.ANDROID_KEYSTORE_PROVIDER
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val cipherText = cipher.doFinal(plainText)
        return Pair(cipherText, cipher.iv)
    }

    override fun decrypt(cipherText: ByteArray, initializationVector: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateSecretKey()
        val spec = GCMParameterSpec(128, initializationVector)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(cipherText)
    }

    override fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
