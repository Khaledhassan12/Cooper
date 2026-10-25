package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Cooper OAuth Authorization Code + PKCE (S256) helper.
 * Configure the public OAuth client ID via COOPER_GITHUB_CLIENT_ID at build time and register
 * cooper://github-callback as the OAuth callback. No client secret is used or embedded.
 */
public final class GitHubAuthManager {
    public static final String REDIRECT_URI = "cooper://github-callback";
    private static final String PREFS = "cooper_github_oauth_pending";
    private static final String STATE_KEY = "state";
    private static final String VERIFIER_KEY = "verifier";
    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";

    public static final class AuthException extends Exception {
        public final String safeCode;
        AuthException(String code, String message) { super(message); safeCode = code; }
    }

    private final Context context;
    private final SharedPreferences pending;
    private final GitCredentialStore credentialStore;

    public GitHubAuthManager(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        this.context = context.getApplicationContext();
        pending = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        credentialStore = new GitCredentialStore(this.context);
    }

    public Uri beginAuthorization(String clientId) throws Exception {
        if (blank(clientId)) throw new AuthException("CLIENT_ID_NOT_CONFIGURED",
                "Set COOPER_GITHUB_CLIENT_ID in the build environment and register the Cooper callback URI.");
        byte[] random = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(random);
        String verifier = base64Url(random);
        byte[] stateBytes = new byte[32];
        secureRandom.nextBytes(stateBytes);
        String state = base64Url(stateBytes);
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        boolean saved = pending.edit().putString(STATE_KEY, state)
                .putString(VERIFIER_KEY, verifier).commit();
        if (!saved) throw new AuthException("PKCE_STATE_NOT_SAVED",
                "Cooper could not prepare a secure authorization session.");
        return Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build();
    }

    public String completeAuthorization(Uri callback, String clientId) throws Exception {
        if (callback == null || !"cooper".equals(callback.getScheme())
                || !"github-callback".equals(callback.getHost())) {
            throw new AuthException("CALLBACK_REJECTED", "Cooper rejected an unexpected authorization callback.");
        }
        String expectedState = pending.getString(STATE_KEY, null);
        String verifier = pending.getString(VERIFIER_KEY, null);
        String returnedState = callback.getQueryParameter("state");
        String code = callback.getQueryParameter("code");
        if (blank(expectedState) || blank(verifier) || blank(returnedState)
                || !constantTimeEquals(expectedState, returnedState)) {
            clearPending();
            throw new AuthException("STATE_MISMATCH", "The GitHub authorization response could not be verified. Start again.");
        }
        if (blank(code)) {
            clearPending();
            throw new AuthException("AUTHORIZATION_DENIED", "GitHub did not return an authorization code.");
        }
        if (blank(clientId)) {
            clearPending();
            throw new AuthException("CLIENT_ID_NOT_CONFIGURED", "The GitHub OAuth client ID is not configured in this build.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(TOKEN_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "Cooper-Android-GitHub-Manager");
            JSONObject body = new JSONObject();
            body.put("client_id", clientId);
            body.put("code", code);
            body.put("redirect_uri", REDIRECT_URI);
            body.put("code_verifier", verifier);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (status < 200 || status >= 300 || input == null) {
                throw new AuthException("TOKEN_EXCHANGE_FAILED", "GitHub could not complete sign-in. Verify the OAuth app callback configuration.");
            }
            String response;
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > 128 * 1024) throw new AuthException("TOKEN_RESPONSE_TOO_LARGE", "GitHub returned an invalid sign-in response.");
                }
                response = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONObject json = new JSONObject(response);
            String token = json.optString("access_token", "");
            if (blank(token)) throw new AuthException("TOKEN_MISSING", "GitHub did not return an access token. Check OAuth settings and permissions.");
            credentialStore.saveAccessToken(token);
            clearPending();
            return "connected";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void clearPending() {
        pending.edit().remove(STATE_KEY).remove(VERIFIER_KEY).commit();
    }

    private static String base64Url(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
