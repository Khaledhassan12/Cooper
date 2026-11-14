package pro.sketchware.activities.importicon.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SketchwareUtil;

public class IconDownloader {

    private static final String TAG = "IconDownloader";

    public static final String STYLE_OUTLINED = "outlined";
    public static final String STYLE_ROUNDED = "rounded";
    public static final String STYLE_SHARP = "sharp";

    public static final String FILL_DEFAULT = "default";
    public static final String FILL_FILLED = "fill1";

    public static final String DEFAULT_STYLE = STYLE_OUTLINED;
    public static final String DEFAULT_FILL_TYPE = FILL_DEFAULT;
    public static final int DEFAULT_OPTICAL_SIZE = 40;

    private static final String SVG_URL_TEMPLATE =
            "https://fonts.gstatic.com/s/i/short-term/release/materialsymbols%s/%s/%s/%dpx.svg";
    private static final String BRAND_SVG_URL_TEMPLATE = "https://api.iconify.design/%s/%s.svg";
    private static final String CACHE_DIR_NAME = "material_symbols";
    private static final String BRAND_CACHE_DIR_NAME = "brand_icons";
    private static final String PREVIEW_CACHE_DIR_NAME = "material_symbols_preview";

    private static final Pattern SVG_OPEN_TAG_PATTERN =
            Pattern.compile("<svg\\b([^>]*?)(/?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WIDTH_ATTR_PATTERN =
            Pattern.compile("(?i)((?:^|\\s)width\\s*=\\s*)(\"[^\"]*\"|'[^']*')");
    private static final Pattern HEIGHT_ATTR_PATTERN =
            Pattern.compile("(?i)((?:^|\\s)height\\s*=\\s*)(\"[^\"]*\"|'[^']*')");

    public interface DownloadCallback {
        void onSuccess(@NonNull File svgFile);

        void onError(@NonNull String errorMessage);
    }

    private final File baseCacheDir;
    private final File brandCacheDir;
    private final File previewCacheDir;
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Map<String, List<DownloadCallback>> inFlightCallbacks = new HashMap<>();
    private final Map<String, Set<String>> pendingBrandRequests = new HashMap<>();
    private final Runnable brandBatchFlushRunnable = this::flushPendingBrandRequests;
    private volatile boolean isShutdown = false;

    public IconDownloader(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.baseCacheDir = new File(appContext.getCacheDir(), CACHE_DIR_NAME);
        if (!this.baseCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.baseCacheDir.mkdirs();
        }
        this.brandCacheDir = new File(appContext.getCacheDir(), BRAND_CACHE_DIR_NAME);
        if (!this.brandCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.brandCacheDir.mkdirs();
        }
        this.previewCacheDir = new File(appContext.getCacheDir(), PREVIEW_CACHE_DIR_NAME);
        if (!this.previewCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.previewCacheDir.mkdirs();
        }
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    public static String normalizeStyle(@Nullable String style) {
        if (STYLE_ROUNDED.equalsIgnoreCase(style)) {
            return STYLE_ROUNDED;
        }
        if (STYLE_SHARP.equalsIgnoreCase(style)) {
            return STYLE_SHARP;
        }
        return STYLE_OUTLINED;
    }

    @NonNull
    public static String normalizeFillType(@Nullable String fillType) {
        if (FILL_FILLED.equalsIgnoreCase(fillType)) {
            return FILL_FILLED;
        }
        return FILL_DEFAULT;
    }

    public static int normalizeOpticalSize(int size) {
        if (size == 20 || size == 24 || size == 40 || size == 48) {
            return size;
        }
        return DEFAULT_OPTICAL_SIZE;
    }

    @NonNull
    public File getCachedSvgFile(
            @NonNull String iconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize
    ) {
        String safeStyle = normalizeStyle(style);
        String safeFill = normalizeFillType(fillType);
        int safeOpsz = normalizeOpticalSize(opticalSize);
        String safeName = sanitizeFileName(iconName);

        File variantDir = new File(
                new File(new File(baseCacheDir, safeStyle), safeFill),
                String.valueOf(safeOpsz)
        );
        return new File(variantDir, safeName + ".svg");
    }

    public boolean isCached(
            @NonNull String iconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize
    ) {
        File file = getCachedSvgFile(iconName, style, fillType, opticalSize);
        return file.exists() && file.length() > 0;
    }

    @NonNull
    private String buildFlightKey(
            @NonNull String iconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize
    ) {
        return normalizeStyle(style)
                + "/" + normalizeFillType(fillType)
                + "/" + normalizeOpticalSize(opticalSize)
                + "/" + iconName;
    }

    public void downloadSvgAsync(
            @NonNull String iconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        String safeStyle = normalizeStyle(style);
        String safeFill = normalizeFillType(fillType);
        int safeOpsz = normalizeOpticalSize(opticalSize);

        File cachedFile = getCachedSvgFile(iconName, safeStyle, safeFill, safeOpsz);
        if (cachedFile.exists() && cachedFile.length() > 0) {
            mainHandler.post(() -> {
                if (!isShutdown) {
                    callback.onSuccess(cachedFile);
                }
            });
            return;
        }

        String flightKey = buildFlightKey(iconName, safeStyle, safeFill, safeOpsz);
        boolean shouldStartTask = false;
        synchronized (inFlightCallbacks) {
            List<DownloadCallback> waiters = inFlightCallbacks.get(flightKey);
            if (waiters != null) {
                waiters.add(callback);
            } else {
                waiters = new ArrayList<>();
                waiters.add(callback);
                inFlightCallbacks.put(flightKey, waiters);
                shouldStartTask = true;
            }
        }

        if (!shouldStartTask || isShutdown) {
            return;
        }

        executor.execute(() -> {
            try {
                File downloaded = ensureCachedSvgSync(iconName, safeStyle, safeFill, safeOpsz);
                notifySuccess(flightKey, downloaded);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to download icon";
                notifyError(flightKey, msg);
            }
        });
    }

    @NonNull
    public File ensureCachedSvgSync(
            @NonNull String iconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize
    ) throws IOException {
        String safeStyle = normalizeStyle(style);
        String safeFill = normalizeFillType(fillType);
        int safeOpsz = normalizeOpticalSize(opticalSize);

        File targetFile = getCachedSvgFile(iconName, safeStyle, safeFill, safeOpsz);
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        if (!SketchwareUtil.isConnected()) {
            throw new IOException("No internet connection and icon is not cached.");
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }

        String url = String.format(Locale.ROOT, SVG_URL_TEMPLATE, safeStyle, iconName, safeFill, safeOpsz);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        File tempFile = new File(parentDir, sanitizeFileName(iconName) + "_" + Thread.currentThread().getId() + ".tmp");
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " while downloading " + iconName);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty SVG response for " + iconName);
            }

            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            if (tempFile.length() == 0) {
                throw new IOException("Downloaded SVG is empty for " + iconName);
            }

            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } finally {
            if (tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    public void prepareResizedSvgPreviewAsync(
            @NonNull String sourceIconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize,
            int outputSizePx,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        String safeStyle = normalizeStyle(style);
        String safeFill = normalizeFillType(fillType);
        int safeOpsz = normalizeOpticalSize(opticalSize);

        executor.execute(() -> {
            try {
                File cachedSvg = ensureCachedSvgSync(sourceIconName, safeStyle, safeFill, safeOpsz);
                String rawSvg = new String(Files.readAllBytes(cachedSvg.toPath()), StandardCharsets.UTF_8);
                String resizedSvg = resizeSvgDimensionsOnly(rawSvg, outputSizePx);

                if (!previewCacheDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    previewCacheDir.mkdirs();
                }

                String previewFileName = safeStyle + "_" + safeFill + "_" + safeOpsz
                        + "_" + sanitizeFileName(sourceIconName) + "_" + outputSizePx + "px.svg";
                File previewFile = new File(previewCacheDir, previewFileName);
                Files.write(previewFile.toPath(), resizedSvg.getBytes(StandardCharsets.UTF_8));

                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onSuccess(previewFile);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to prepare resized SVG preview", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to load icon preview";
                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onError(msg);
                    }
                });
            }
        });
    }

    public void saveResizedSvgToProjectAsync(
            @NonNull String sourceIconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize,
            @NonNull String scId,
            @NonNull String outputResourceName,
            int outputSizePx,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        executor.execute(() -> {
            try {
                File savedFile = saveResizedSvgToProject(
                        sourceIconName,
                        style,
                        fillType,
                        opticalSize,
                        scId,
                        outputResourceName,
                        outputSizePx
                );
                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onSuccess(savedFile);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to save SVG icon to project", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to save icon";
                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onError(msg);
                    }
                });
            }
        });
    }

    @NonNull
    public File getBrandCachedSvgFile(@NonNull String prefix, @NonNull String iconName) {
        File prefixDir = new File(brandCacheDir, sanitizeFileName(prefix));
        return new File(prefixDir, sanitizeFileName(iconName) + ".svg");
    }

    public boolean isBrandCached(@NonNull String prefix, @NonNull String iconName) {
        File file = getBrandCachedSvgFile(prefix, iconName);
        return file.exists() && file.length() > 0;
    }

    @NonNull
    private String buildBrandFlightKey(@NonNull String prefix, @NonNull String iconName) {
        return "brand/" + prefix + "/" + iconName;
    }

    public void downloadBrandSvgAsync(
            @NonNull String prefix,
            @NonNull String iconName,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        File cachedFile = getBrandCachedSvgFile(prefix, iconName);
        if (cachedFile.exists() && cachedFile.length() > 0) {
            mainHandler.post(() -> {
                if (!isShutdown) {
                    callback.onSuccess(cachedFile);
                }
            });
            return;
        }

        String flightKey = buildBrandFlightKey(prefix, iconName);
        synchronized (inFlightCallbacks) {
            List<DownloadCallback> waiters = inFlightCallbacks.get(flightKey);
            if (waiters != null) {
                waiters.add(callback);
            } else {
                waiters = new ArrayList<>();
                waiters.add(callback);
                inFlightCallbacks.put(flightKey, waiters);
            }
        }

        synchronized (pendingBrandRequests) {
            pendingBrandRequests.computeIfAbsent(prefix, k -> new LinkedHashSet<>()).add(iconName);
        }

        mainHandler.removeCallbacks(brandBatchFlushRunnable);
        mainHandler.postDelayed(brandBatchFlushRunnable, 35L);
    }

    private void flushPendingBrandRequests() {
        if (isShutdown) return;

        Map<String, List<String>> toFetch = new HashMap<>();
        synchronized (pendingBrandRequests) {
            for (Map.Entry<String, Set<String>> entry : pendingBrandRequests.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    toFetch.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
            pendingBrandRequests.clear();
        }

        if (toFetch.isEmpty()) return;

        executor.execute(() -> {
            for (Map.Entry<String, List<String>> entry : toFetch.entrySet()) {
                String prefix = entry.getKey();
                List<String> names = entry.getValue();
                for (int i = 0; i < names.size(); i += 50) {
                    int end = Math.min(i + 50, names.size());
                    List<String> chunk = names.subList(i, end);
                    try {
                        fetchBrandBatchSync(prefix, chunk);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to fetch brand batch for " + prefix, e);
                        for (String name : chunk) {
                            File f = getBrandCachedSvgFile(prefix, name);
                            if (f.exists() && f.length() > 0) {
                                notifySuccess(buildBrandFlightKey(prefix, name), f);
                            } else {
                                notifyError(buildBrandFlightKey(prefix, name),
                                        e.getMessage() != null ? e.getMessage() : "Download error");
                            }
                        }
                    }
                }
            }
        });
    }

    public void prefetchBrandBatchAsync(@NonNull String prefix, @NonNull List<String> iconNames) {
        if (isShutdown || iconNames.isEmpty()) return;

        executor.execute(() -> {
            try {
                List<String> uncached = new ArrayList<>();
                for (String name : iconNames) {
                    if (!isBrandCached(prefix, name)) {
                        uncached.add(name);
                    }
                }
                for (int i = 0; i < uncached.size(); i += 50) {
                    int end = Math.min(i + 50, uncached.size());
                    fetchBrandBatchSync(prefix, uncached.subList(i, end));
                }
            } catch (Exception e) {
                Log.w(TAG, "Prefetch brand batch failed for " + prefix, e);
            }
        });
    }

    public void fetchBrandBatchSync(@NonNull String prefix, @NonNull List<String> iconNames) throws IOException {
        if (iconNames.isEmpty()) return;

        List<String> toQuery = new ArrayList<>();
        for (String name : iconNames) {
            File cached = getBrandCachedSvgFile(prefix, name);
            if (cached.exists() && cached.length() > 0) {
                notifySuccess(buildBrandFlightKey(prefix, name), cached);
            } else {
                toQuery.add(name);
            }
        }
        if (toQuery.isEmpty()) return;

        if (!SketchwareUtil.isConnected()) {
            throw new IOException("No internet connection and icons not cached.");
        }

        String joinedNames = String.join(",", toQuery);
        String url = "https://api.iconify.design/" + sanitizeFileName(prefix) + ".json?icons=" + joinedNames;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        int maxRetries = 2;
        int attempt = 0;
        String jsonResponse = null;

        while (attempt <= maxRetries) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 429) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for rate limit retry");
                        }
                        continue;
                    }
                    throw new IOException("HTTP 429 (Rate limit) from Iconify API");
                }
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + " while fetching brand batch");
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty response body from Iconify batch endpoint");
                }
                jsonResponse = body.string();
                break;
            }
        }

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            throw new IOException("No response from brand server");
        }

        try {
            JSONObject root = new JSONObject(jsonResponse);
            int rootWidth = root.optInt("width", 24);
            int rootHeight = root.optInt("height", 24);

            JSONObject iconsObj = root.optJSONObject("icons");
            if (iconsObj != null) {
                Iterator<String> keys = iconsObj.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONObject iconObj = iconsObj.optJSONObject(name);
                    if (iconObj == null) continue;
                    String body = iconObj.optString("body", "");
                    if (body.isEmpty()) continue;
                    int width = iconObj.optInt("width", rootWidth);
                    int height = iconObj.optInt("height", rootHeight);
                    int left = iconObj.optInt("left", 0);
                    int top = iconObj.optInt("top", 0);

                    String svgContent = String.format(Locale.ROOT,
                            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"%d %d %d %d\">%s</svg>",
                            width, height, left, top, width, height, body);

                    File targetFile = getBrandCachedSvgFile(prefix, name);
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        parentDir.mkdirs();
                    }

                    File tempFile = new File(parentDir, sanitizeFileName(name) + "_" + Thread.currentThread().getId() + ".tmp");
                    try {
                        Files.write(tempFile.toPath(), svgContent.getBytes(StandardCharsets.UTF_8));
                        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        notifySuccess(buildBrandFlightKey(prefix, name), targetFile);
                    } finally {
                        if (tempFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                        }
                    }
                }
            }

            JSONObject aliasesObj = root.optJSONObject("aliases");
            if (aliasesObj != null) {
                Iterator<String> aliasKeys = aliasesObj.keys();
                while (aliasKeys.hasNext()) {
                    String aliasName = aliasKeys.next();
                    JSONObject aliasObj = aliasesObj.optJSONObject(aliasName);
                    if (aliasObj == null) continue;
                    String parentName = aliasObj.optString("parent", "");
                    File parentFile = getBrandCachedSvgFile(prefix, parentName);
                    if (parentFile.exists() && parentFile.length() > 0) {
                        File aliasFile = getBrandCachedSvgFile(prefix, aliasName);
                        try {
                            Files.copy(parentFile.toPath(), aliasFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            notifySuccess(buildBrandFlightKey(prefix, aliasName), aliasFile);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            for (String name : toQuery) {
                File cached = getBrandCachedSvgFile(prefix, name);
                if (!cached.exists() || cached.length() == 0) {
                    try {
                        File singleDownloaded = downloadSingleBrandSvgDirectSync(prefix, name);
                        notifySuccess(buildBrandFlightKey(prefix, name), singleDownloaded);
                    } catch (Exception singleEx) {
                        notifyError(buildBrandFlightKey(prefix, name),
                                singleEx.getMessage() != null ? singleEx.getMessage() : "Icon not found");
                    }
                }
            }

        } catch (JSONException e) {
            throw new IOException("Failed to parse brand batch JSON", e);
        }
    }

    @NonNull
    public File ensureBrandCachedSvgSync(@NonNull String prefix, @NonNull String iconName) throws IOException {
        File targetFile = getBrandCachedSvgFile(prefix, iconName);
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        if (!SketchwareUtil.isConnected()) {
            throw new IOException("No internet connection and brand icon is not cached.");
        }

        try {
            fetchBrandBatchSync(prefix, Collections.singletonList(iconName));
        } catch (Exception e) {
            Log.w(TAG, "Batch fetch failed for single icon " + iconName + ", trying direct download", e);
        }

        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        return downloadSingleBrandSvgDirectSync(prefix, iconName);
    }

    @NonNull
    private File downloadSingleBrandSvgDirectSync(@NonNull String prefix, @NonNull String iconName) throws IOException {
        File targetFile = getBrandCachedSvgFile(prefix, iconName);
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile;
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }

        String url = String.format(Locale.ROOT, BRAND_SVG_URL_TEMPLATE, sanitizeFileName(prefix), sanitizeFileName(iconName));
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        int maxRetries = 2;
        int attempt = 0;
        while (attempt <= maxRetries) {
            File tempFile = new File(parentDir, sanitizeFileName(iconName) + "_" + Thread.currentThread().getId() + ".tmp");
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 429) {
                    attempt++;
                    if (attempt <= maxRetries) {
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for retry");
                        }
                        continue;
                    }
                    throw new IOException("HTTP 429 while downloading " + iconName);
                }
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + " while downloading " + iconName);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty SVG response for " + iconName);
                }

                try (InputStream in = body.byteStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                if (tempFile.length() == 0) {
                    throw new IOException("Downloaded SVG is empty for " + iconName);
                }

                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return targetFile;
            } finally {
                if (tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        }
        throw new IOException("Failed to download brand icon " + iconName);
    }

    public void prepareBrandResizedSvgPreviewAsync(
            @NonNull String prefix,
            @NonNull String sourceIconName,
            int outputSizePx,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        executor.execute(() -> {
            try {
                File cachedSvg = ensureBrandCachedSvgSync(prefix, sourceIconName);
                String rawSvg = new String(Files.readAllBytes(cachedSvg.toPath()), StandardCharsets.UTF_8);
                String resizedSvg = resizeSvgDimensionsOnly(rawSvg, outputSizePx);

                if (!previewCacheDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    previewCacheDir.mkdirs();
                }

                String previewFileName = "brand_" + sanitizeFileName(prefix)
                        + "_" + sanitizeFileName(sourceIconName) + "_" + outputSizePx + "px.svg";
                File previewFile = new File(previewCacheDir, previewFileName);
                Files.write(previewFile.toPath(), resizedSvg.getBytes(StandardCharsets.UTF_8));

                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onSuccess(previewFile);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to prepare resized brand SVG preview", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to load icon preview";
                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onError(msg);
                    }
                });
            }
        });
    }

    public void saveBrandResizedSvgToProjectAsync(
            @NonNull String prefix,
            @NonNull String sourceIconName,
            @NonNull String scId,
            @NonNull String outputResourceName,
            int outputSizePx,
            @NonNull DownloadCallback callback
    ) {
        if (isShutdown) return;

        executor.execute(() -> {
            try {
                File cachedSvg = ensureBrandCachedSvgSync(prefix, sourceIconName);
                String rawSvg = new String(Files.readAllBytes(cachedSvg.toPath()), StandardCharsets.UTF_8);
                String resizedSvg = resizeSvgDimensionsOnly(rawSvg, outputSizePx);

                FilePathUtil filePathUtil = new FilePathUtil();
                String outputPath = filePathUtil.getSvgFullPath(scId, outputResourceName);
                File outputFile = new File(outputPath);
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    parentDir.mkdirs();
                }

                Files.write(outputFile.toPath(), resizedSvg.getBytes(StandardCharsets.UTF_8));

                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onSuccess(outputFile);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to save brand SVG icon to project", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to save icon";
                mainHandler.post(() -> {
                    if (!isShutdown) {
                        callback.onError(msg);
                    }
                });
            }
        });
    }

    @NonNull
    public File saveResizedSvgToProject(
            @NonNull String sourceIconName,
            @NonNull String style,
            @NonNull String fillType,
            int opticalSize,
            @NonNull String scId,
            @NonNull String outputResourceName,
            int outputSizePx
    ) throws IOException {
        File cachedSvg = ensureCachedSvgSync(sourceIconName, style, fillType, opticalSize);
        String rawSvg = new String(Files.readAllBytes(cachedSvg.toPath()), StandardCharsets.UTF_8);
        String resizedSvg = resizeSvgDimensionsOnly(rawSvg, outputSizePx);

        FilePathUtil filePathUtil = new FilePathUtil();
        String outputPath = filePathUtil.getSvgFullPath(scId, outputResourceName);
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }

        Files.write(outputFile.toPath(), resizedSvg.getBytes(StandardCharsets.UTF_8));
        return outputFile;
    }

    @NonNull
    public static String resizeSvgDimensionsOnly(@NonNull String svgContent, int sizePx) {
        Matcher svgMatcher = SVG_OPEN_TAG_PATTERN.matcher(svgContent);
        if (!svgMatcher.find()) {
            return svgContent;
        }

        String attrs = svgMatcher.group(1) != null ? svgMatcher.group(1) : "";
        String selfClose = svgMatcher.group(2) != null ? svgMatcher.group(2) : "";
        String sizeValue = sizePx + "px";

        Matcher widthMatcher = WIDTH_ATTR_PATTERN.matcher(attrs);
        if (widthMatcher.find()) {
            attrs = widthMatcher.replaceFirst(Matcher.quoteReplacement(widthMatcher.group(1) + "\"" + sizeValue + "\""));
        } else {
            attrs = attrs + " width=\"" + sizeValue + "\"";
        }

        Matcher heightMatcher = HEIGHT_ATTR_PATTERN.matcher(attrs);
        if (heightMatcher.find()) {
            attrs = heightMatcher.replaceFirst(Matcher.quoteReplacement(heightMatcher.group(1) + "\"" + sizeValue + "\""));
        } else {
            attrs = attrs + " height=\"" + sizeValue + "\"";
        }

        String replacementTag = "<svg" + attrs + selfClose + ">";
        StringBuffer sb = new StringBuffer();
        svgMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacementTag));
        svgMatcher.appendTail(sb);
        return sb.toString();
    }

    private void notifySuccess(@NonNull String flightKey, @NonNull File file) {
        List<DownloadCallback> waiters;
        synchronized (inFlightCallbacks) {
            waiters = inFlightCallbacks.remove(flightKey);
        }
        if (waiters == null || waiters.isEmpty()) return;

        mainHandler.post(() -> {
            if (isShutdown) return;
            for (DownloadCallback cb : waiters) {
                try {
                    cb.onSuccess(file);
                } catch (Exception e) {
                    Log.w(TAG, "Error delivering icon download success callback", e);
                }
            }
        });
    }

    private void notifyError(@NonNull String flightKey, @NonNull String errorMessage) {
        List<DownloadCallback> waiters;
        synchronized (inFlightCallbacks) {
            waiters = inFlightCallbacks.remove(flightKey);
        }
        if (waiters == null || waiters.isEmpty()) return;

        mainHandler.post(() -> {
            if (isShutdown) return;
            for (DownloadCallback cb : waiters) {
                try {
                    cb.onError(errorMessage);
                } catch (Exception e) {
                    Log.w(TAG, "Error delivering icon download error callback", e);
                }
            }
        });
    }

    @NonNull
    private static String sanitizeFileName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return "icon";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public void shutdown() {
        isShutdown = true;
        mainHandler.removeCallbacks(brandBatchFlushRunnable);
        synchronized (pendingBrandRequests) {
            pendingBrandRequests.clear();
        }
        synchronized (inFlightCallbacks) {
            inFlightCallbacks.clear();
        }
        executor.shutdownNow();
    }
}