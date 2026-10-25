package mod.hilal.saif.activities.tools;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooper GitHub OAuth Device Authorization Grant.
 * A native APK must not contain an OAuth client secret. GitHub's device flow uses the public
 * client ID and does not require a client secret. Enable Device Flow in the registered OAuth app.
 */
public final class GitHubAuthManager {
    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";

    public static final class AuthException extends Exception {
        public final String safeCode;
        AuthException(String code, String message) { super(message); safeCode = code; }
    }

    public static final class DeviceAuthorization {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresInSeconds;
        public final int pollingIntervalSeconds;

        private DeviceAuthorization(String deviceCode, String userCode, String verificationUri,
                                    int expiresInSeconds, int pollingIntervalSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresInSeconds = expiresInSeconds;
            this.pollingIntervalSeconds = pollingIntervalSeconds;
        }
    }

    private final GitCredentialStore credentialStore;

    public GitHubAuthManager(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        credentialStore = new GitCredentialStore(context.getApplicationContext());
    }

    public DeviceAuthorization beginDeviceAuthorization(String clientId) throws Exception {
        if (blank(clientId)) {
            throw new AuthException("CLIENT_ID_NOT_CONFIGURED",
                    "Set the public COOPER_GITHUB_CLIENT_ID build variable and enable Device Flow in the GitHub OAuth app.");
        }
        JSONObject body = new JSONObject();
        body.put("client_id", clientId);
        body.put("scope", "repo");
        JSONObject response = postJson(DEVICE_CODE_URL, body, "DEVICE_CODE_FAILED");
        String deviceCode = response.optString("device_code", "");
        String userCode = response.optString("user_code", "");
        String verificationUri = response.optString("verification_uri", "");
        int expires = response.optInt("expires_in", 0);
        int interval = response.optInt("interval", 5);
        if (blank(deviceCode) || blank(userCode) || expires <= 0
                || !verificationUri.startsWith("https://github.com/")) {
            throw new AuthException("INVALID_DEVICE_RESPONSE", "GitHub returned an incomplete device authorization response.");
        }
        return new DeviceAuthorization(deviceCode, userCode, verificationUri, expires, Math.max(5, interval));
    }

    public String pollForAccessToken(String clientId, DeviceAuthorization authorization,
                                     AtomicBoolean cancelled) throws Exception {
        if (authorization == null || blank(authorization.deviceCode) || blank(clientId)) {
            throw new AuthException("INVALID_DEVICE_SESSION", "The GitHub device authorization session is invalid.");
        }
        long deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1000L;
        int interval = authorization.pollingIntervalSeconds;
        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.get()) {
                throw new AuthException("CANCELLED", "GitHub sign-in was cancelled.");
            }
            Thread.sleep(interval * 1000L);
            if (cancelled != null && cancelled.get()) {
                throw new AuthException("CANCELLED", "GitHub sign-in was cancelled.");
            }
            JSONObject body = new JSONObject();
            body.put("client_id", clientId);
            body.put("device_code", authorization.deviceCode);
            body.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            JSONObject response;
            try {
                response = postJson(TOKEN_URL, body, "TOKEN_POLL_FAILED");
            } catch (AuthException e) {
                throw e;
            }
            String token = response.optString("access_token", "");
            if (!blank(token)) {
                credentialStore.saveAccessToken(token);
                return "connected";
            }
            String error = response.optString("error", "");
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            if ("access_denied".equals(error)) {
                throw new AuthException("ACCESS_DENIED", "GitHub authorization was denied by the user.");
            }
            if ("expired_token".equals(error) || "token_expired".equals(error)) {
                throw new AuthException("DEVICE_CODE_EXPIRED", "The GitHub device code expired. Start sign-in again.");
            }
            throw new AuthException("TOKEN_POLL_FAILED",
                    "GitHub could not complete device authorization. Check the OAuth app settings and retry.");
        }
        throw new AuthException("DEVICE_CODE_EXPIRED", "The GitHub device code expired. Start sign-in again.");
    }

    private static JSONObject postJson(String endpoint, JSONObject payload, String errorCode) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            if (!"https".equals(url.getProtocol()) || !"github.com".equalsIgnoreCase(url.getHost())) {
                throw new AuthException("HOST_REJECTED", "Cooper blocked an untrusted authentication host.");
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "Cooper-Android-GitHub-Manager");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) throw new AuthException(errorCode, "GitHub returned an empty authentication response.");
            String response;
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > 64 * 1024) {
                        throw new AuthException(errorCode, "GitHub returned an oversized authentication response.");
                    }
                }
                response = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONObject json = new JSONObject(response);
            if (status < 200 || status >= 300) {
                throw new AuthException(errorCode, "GitHub authentication request failed. Check Device Flow configuration.");
            }
            return json;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(errorCode, "GitHub authentication could not complete. Check the network and OAuth app settings.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
