package sg.foodbridge.navigator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only encrypted ciphertext enters preferences; the encryption key remains in Android Keystore. */
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("account_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("foodbridge_session_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("foodbridge_session_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject = try {
        val encoded = preferences.getString("encrypted", null)
        if (encoded == null) JSONObject() else {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
    } catch (_: Exception) { preferences.edit().clear().commit(); JSONObject() }
    @Synchronized fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun endSession(queueRevocation: Boolean = false) {
        val data = read()
        if (queueRevocation && data.has("token")) {
            val pending = data.optJSONArray("pending_revocations") ?: JSONArray()
            pending.put(JSONObject().put("token", data.getString("token")).put("expires_at", data.optString("expires_at")))
            data.put("pending_revocations", pending)
        }
        listOf("token", "account", "expires_at", "last_activity").forEach(data::remove)
        write(data)
    }
    @Synchronized fun update(change: (JSONObject) -> Unit) {
        val data = read(); change(data); write(data)
    }
}
