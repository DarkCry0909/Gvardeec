package com.example.guardhr

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

fun pbkdf2(pass: CharArray, salt: ByteArray): ByteArray =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(pass, salt, 120_000, 256)).encoded

fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

/** Ключ шифрования базы: случайные 32 байта, зашифрованные мастер-ключом из Android Keystore. */
object Keys {
    private const val ALIAS = "gvardeec_master"

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun dbPassphrase(c: Context): ByteArray {
        val p = c.getSharedPreferences("sec", Context.MODE_PRIVATE)
        val stored = p.getString("dbkey", null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (stored != null) {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            return cipher.doFinal(raw, 12, raw.size - 12)
        }
        val pass = randomBytes(32)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val out = cipher.iv + cipher.doFinal(pass)
        p.edit().putString("dbkey", Base64.encodeToString(out, Base64.NO_WRAP)).commit()
        return pass
    }
}

/** PIN-код, блокировка после неудачных попыток и настройки защиты. */
object Sec {
    private fun p(c: Context) = c.getSharedPreferences("sec", Context.MODE_PRIVATE)

    fun hasPin(c: Context) = p(c).contains("pinHash")

    fun setPin(c: Context, pin: String) {
        val salt = randomBytes(16)
        p(c).edit()
            .putString("pinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pinHash", Base64.encodeToString(pbkdf2(pin.toCharArray(), salt), Base64.NO_WRAP))
            .commit()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val salt = p(c).getString("pinSalt", null) ?: return false
        val hash = p(c).getString("pinHash", null) ?: return false
        val calc = pbkdf2(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP))
        return MessageDigest.isEqual(calc, Base64.decode(hash, Base64.NO_WRAP))
    }

    fun lockUntil(c: Context): Long = p(c).getLong("lockUntil", 0L)

    fun registerFail(c: Context) {
        val n = p(c).getInt("fails", 0) + 1
        val e = p(c).edit().putInt("fails", n)
        if (n >= 5) e.putLong("lockUntil", System.currentTimeMillis() + 30_000L * (n - 4))
        e.commit()
    }

    fun resetFails(c: Context) {
        p(c).edit().putInt("fails", 0).putLong("lockUntil", 0L).commit()
    }

    fun biometricEnabled(c: Context) = p(c).getBoolean("bio", false)
    fun setBiometric(c: Context, v: Boolean) { p(c).edit().putBoolean("bio", v).commit() }
    fun themeMode(c: Context) = p(c).getInt("theme", 0)
    fun setThemeMode(c: Context, v: Int) { p(c).edit().putInt("theme", v).commit() }
    fun secureScreen(c: Context) = p(c).getBoolean("secure", true)
    fun setSecureScreen(c: Context, v: Boolean) { p(c).edit().putBoolean("secure", v).commit() }
}

fun canBiometric(c: Context): Boolean =
    BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(a: FragmentActivity, onOk: () -> Unit) {
    val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Вход в Гвардеец")
        .setNegativeButtonText("Ввести PIN-код")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}
