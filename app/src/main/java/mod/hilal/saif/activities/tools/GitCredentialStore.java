package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cooper GitHub credential storage backed by Android Keystore AES-GCM.
 * Only encrypted token bytes are persisted in app-private preferences.
 */
public final class GitCredentialStore {
    private static final String PREFS = "cooper_github_credentials";
    private static final String TOKEN_CIPHER_TEXT = "token_ciphertext";
    private static final String TOKEN_IV = "token_iv";
    private static final String KEY_ALIAS = "CooperGitHubCredentialKey";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences preferences;

    public GitCredentialStore(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void saveAccessToken(String token) throws Exception {
        if (token == null || token.trim().isEmpty() || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("A valid access token is required.");
        }
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        boolean saved = preferences.edit()
                .putString(TOKEN_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .commit();
        if (!saved) throw new IllegalStateException("Cooper could not persist encrypted credentials.");
    }

    public synchronized String readAccessToken() throws Exception {
        String ciphertext = preferences.getString(TOKEN_CIPHER_TEXT, null);
        String ivValue = preferences.getString(TOKEN_IV, null);
        if (ciphertext == null || ivValue == null) return null;
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivValue, Base64.NO_WRAP)));
        byte[] plain = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP));
        return new String(plain, StandardCharsets.UTF_8);
    }

    public synchronized void clear() {
        preferences.edit().clear().commit();
        try {
            KeyStore store = KeyStore.getInstance(ANDROID_KEY_STORE);
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // The encrypted value is already removed; do not log credential-related details.
        }
    }

    public synchronized boolean hasCredential() {
        return preferences.contains(TOKEN_CIPHER_TEXT) && preferences.contains(TOKEN_IV);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEY_STORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            java.security.Key existing = store.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;
            throw new IllegalStateException("Cooper credential key is unavailable.");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
