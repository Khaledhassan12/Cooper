package mod.hilal.saif.activities.tools;

import android.app.Activity;
import android.content.Context;

import org.eclipse.jgit.api.Git;
import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import a.a.a.wq;
import com.besome.sketch.export.ExportSource;
import pro.sketchware.utility.FilePathUtil;

/**
 * Produces the same Android Studio project tree used by Source code > Export new for Android Studio
 * and mirrors those files into a stable Git workspace. The export generator's temporary directory
 * is never used as the Git root because ExportSource deletes/recreates that directory on each run.
 */
public final class GitAndroidStudioExportManager {
    private static final Object EXPORT_LOCK = new Object();
    private static final String WORKSPACE_DIRECTORY = "Cooper" + File.separator + "github_projects";
    private static final int BUFFER_SIZE = 16 * 1024;

    private GitAndroidStudioExportManager() { }

    public static File getWorkspaceRoot(Context context, String scId) throws IOException {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        if (scId == null || !scId.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("A valid Cooper project ID is required.");
        }
        File root = new File(wq.getAbsolutePathOf(WORKSPACE_DIRECTORY), scId).getCanonicalFile();
        File parent = new File(wq.getAbsolutePathOf(WORKSPACE_DIRECTORY)).getCanonicalFile();
        if (!root.getPath().startsWith(parent.getPath() + File.separator)) {
            throw new IOException("The Android Studio Git workspace resolved outside Cooper's workspace directory.");
        }
        return root;
    }

    /**
     * Refreshes the exported source tree only when the Git worktree has no pending local changes.
     *
     * @return true when the source tree was refreshed; false when an initialized repository already
     *         has changes that must first be reviewed/committed.
     */
    public static boolean refreshIfClean(Activity activity, GitProjectBindingStore.Binding binding) throws Exception {
        if (activity == null || binding == null) throw new IllegalArgumentException("Activity and project binding are required.");
        if (activity.isFinishing() || activity.isDestroyed()) {
            throw new IllegalStateException("Cooper cannot export the Android Studio project after this screen has closed.");
        }
        if (binding.scId == null || !binding.scId.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("The selected Cooper project ID is invalid.");
        }

        File sourceRoot = binding.sourceProjectRoot.getCanonicalFile();
        File expectedSourceRoot = new File(wq.b(binding.scId)).getCanonicalFile();
        if (!sourceRoot.equals(expectedSourceRoot)) {
            throw new IllegalStateException("The selected source project no longer matches its verified Cooper project ID.");
        }

        File workspaceRoot = binding.projectRoot.getCanonicalFile();
        File expectedWorkspaceRoot = getWorkspaceRoot(activity, binding.scId);
        if (!workspaceRoot.equals(expectedWorkspaceRoot)) {
            throw new IllegalStateException("The Git workspace does not match the selected Cooper project.");
        }
        if (!workspaceRoot.exists() && !workspaceRoot.mkdirs() && !workspaceRoot.isDirectory()) {
            throw new IOException("Cooper could not create the Android Studio Git workspace.");
        }
        if (!workspaceRoot.isDirectory()) {
            throw new IOException("The Android Studio Git workspace path is not a directory.");
        }

        synchronized (EXPORT_LOCK) {
            File destinationGit = new File(workspaceRoot, ".git");
            File legacyGit = new File(sourceRoot, ".git");
            // Only transfer an old local repository when it was already configured for GitHub.
            // Local-only repositories may contain the internal Sketchware tree; keep them out of
            // the new workspace so the first Android Studio export starts with a clean history.
            boolean migrateLegacyRepository = !destinationGit.exists() && legacyGit.isDirectory()
                    && hasUsableGitHubOrigin(sourceRoot);

            LocalGitBackend backend = new LocalGitBackend();
            if (destinationGit.exists()) {
                LocalGitBackend.Snapshot snapshot = backend.inspect(workspaceRoot);
                if (!snapshot.initialized) {
                    throw new IllegalStateException("The Android Studio Git workspace contains invalid Git metadata.");
                }
                if (hasPendingChanges(snapshot)) {
                    return false;
                }
            } else if (migrateLegacyRepository) {
                if (!legacyGit.isDirectory()) {
                    throw new IllegalStateException("Cooper found old Git metadata in the Sketchware data folder that cannot be migrated safely.");
                }
                LocalGitBackend.Snapshot legacySnapshot = backend.inspect(sourceRoot);
                if (!legacySnapshot.initialized) {
                    throw new IllegalStateException("Cooper found old Git metadata but could not verify the repository.");
                }
                if (hasPendingChanges(legacySnapshot)) {
                    throw new IllegalStateException("The previous Git repository contains uncommitted changes in the Sketchware data folder. Review that repository before switching to the Android Studio project structure.");
                }
            }

            if (activity.isFinishing() || activity.isDestroyed()) {
                throw new IllegalStateException("Cooper cancelled the export because the Git Manager screen is no longer active.");
            }
            boolean exported = ExportSource.startExport(activity, binding.scId, null);
            if (!exported) {
                throw new IllegalStateException("Android Studio source export failed. Cooper did not upload the original Sketchware data files.");
            }

            File exportRoot = new File(wq.d(binding.scId)).getCanonicalFile();
            if (!exportRoot.isDirectory()) {
                throw new IOException("The Android Studio exporter did not produce a project directory.");
            }

            mirrorExportTree(activity.getApplicationContext(), binding.scId, exportRoot, workspaceRoot);

            if (migrateLegacyRepository) {
                String expectedHead = backend.inspect(sourceRoot).headCommit;
                File temporaryGit = new File(workspaceRoot, ".git-migration-temp");
                if (temporaryGit.exists() && !deleteRecursively(temporaryGit)) {
                    throw new IOException("Cooper could not clean a previous incomplete Git metadata migration.");
                }
                copyDirectory(legacyGit, temporaryGit, legacyGit.getCanonicalFile());
                if (!temporaryGit.renameTo(destinationGit)) {
                    deleteRecursively(temporaryGit);
                    throw new IOException("Cooper could not atomically install the previous Git metadata in the Android Studio workspace.");
                }
                LocalGitBackend.Snapshot migrated;
                try {
                    migrated = backend.inspect(workspaceRoot);
                } catch (Exception verificationError) {
                    deleteRecursively(destinationGit);
                    throw new IOException("Cooper could not verify the migrated Git history. The original repository was preserved.", verificationError);
                }
                if (!migrated.initialized || !same(expectedHead, migrated.headCommit)) {
                    deleteRecursively(destinationGit);
                    throw new IOException("Cooper could not verify the migrated Git history. The original repository was preserved.");
                }
                if (!deleteRecursively(legacyGit)) {
                    throw new IOException("The Android Studio Git workspace is ready, but the old Git metadata could not be removed from the Sketchware data folder.");
                }
            }
            return true;
        }
    }

    private static boolean hasUsableGitHubOrigin(File projectRoot) {
        try (Git git = Git.open(projectRoot)) {
            String origin = git.getRepository().getConfig().getString("remote", "origin", "url");
            return origin != null && origin.matches(
                    "https://github\\.com/[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}\\.git");
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
    private static boolean hasPendingChanges(LocalGitBackend.Snapshot snapshot) {
        return !snapshot.conflicting.isEmpty() || !snapshot.staged.isEmpty()
                || !snapshot.modified.isEmpty() || !snapshot.added.isEmpty()
                || !snapshot.removed.isEmpty() || !snapshot.missing.isEmpty()
                || !snapshot.untracked.isEmpty();
    }

    private static void mirrorExportTree(Context context, String scId, File exportRoot, File workspaceRoot) throws IOException {
        File canonicalExportRoot = exportRoot.getCanonicalFile();
        File canonicalWorkspaceRoot = workspaceRoot.getCanonicalFile();
        if (canonicalExportRoot.equals(canonicalWorkspaceRoot)
                || canonicalExportRoot.getPath().startsWith(canonicalWorkspaceRoot.getPath() + File.separator)
                || canonicalWorkspaceRoot.getPath().startsWith(canonicalExportRoot.getPath() + File.separator)) {
            throw new IOException("The exporter and Git workspace must be separate directories.");
        }

        boolean hasCustomSketchApplication = new File(
                new FilePathUtil().getPathJava(scId), "SketchApplication.java").isFile();

        Set<String> nextManifest = new TreeSet<>();
        collectExportFiles(canonicalExportRoot, canonicalExportRoot, nextManifest, hasCustomSketchApplication);
        Set<String> previousManifest = readManifest(manifestFile(context, scId));

        for (String relativePath : nextManifest) {
            File source = resolveChild(canonicalExportRoot, relativePath);
            File target = resolveChild(canonicalWorkspaceRoot, relativePath);
            if (!source.isFile()) throw new IOException("An exported project file disappeared during synchronization: " + relativePath);
            if (target.exists() && !previousManifest.contains(relativePath) && !filesEqual(source, target)) {
                throw new IOException("An unmanaged file conflicts with an exported Android Studio file: " + relativePath
                        + ". Resolve the file conflict before refreshing the project.");
            }
        }

        for (String stalePath : previousManifest) {
            if (nextManifest.contains(stalePath)) continue;
            File stale = resolveChild(canonicalWorkspaceRoot, stalePath);
            if (stale.exists()) {
                if (!stale.isFile() || !stale.delete()) {
                    throw new IOException("Cooper could not remove a stale exported project file: " + stalePath);
                }
                deleteEmptyParents(stale.getParentFile(), canonicalWorkspaceRoot);
            }
        }

        for (String relativePath : nextManifest) {
            File source = resolveChild(canonicalExportRoot, relativePath);
            File target = resolveChild(canonicalWorkspaceRoot, relativePath);
            copyFile(source, target);
        }
        writeManifest(manifestFile(context, scId), nextManifest);
    }

    private static void collectExportFiles(File root, File current, Set<String> result,
                                           boolean hasCustomSketchApplication) throws IOException {
        File canonicalCurrent = current.getCanonicalFile();
        String rootPath = root.getCanonicalPath();
        if (!canonicalCurrent.equals(root) && !canonicalCurrent.getPath().startsWith(rootPath + File.separator)) {
            throw new IOException("The exported project contains a path that resolves outside its root.");
        }
        File[] entries = current.listFiles();
        if (entries == null) throw new IOException("Cooper could not read the generated Android Studio project directory.");
        for (File entry : entries) {
            String name = entry.getName();
            if (entry.isDirectory()) {
                if (isExcludedDirectory(name)) continue;
                collectExportFiles(root, entry, result, hasCustomSketchApplication);
                continue;
            }
            if (!entry.isFile()) continue;
            File absoluteEntry = entry.getAbsoluteFile();
            File canonicalEntry = entry.getCanonicalFile();
            if (!canonicalEntry.equals(absoluteEntry)) {
                throw new IOException("The exported project contains a symbolic link or path that cannot be copied safely: " + name);
            }
            String absolutePath = canonicalEntry.getPath();
            if (!absolutePath.startsWith(rootPath + File.separator)) {
                throw new IOException("An exported source file resolves outside its project root.");
            }
            String relativePath = absolutePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
            if (shouldIncludeFile(relativePath, hasCustomSketchApplication)) result.add(relativePath);
        }
    }

    private static boolean isExcludedDirectory(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith(".git") || lower.equals(".gradle") || lower.equals("build")
                || lower.equals("bin") || lower.equals("gen") || lower.equals("outputs")
                || lower.equals("intermediates") || lower.equals("cache") || lower.equals("caches");
    }

    private static boolean shouldIncludeFile(String relativePath, boolean hasCustomSketchApplication) {
        String normalized = relativePath.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.equalsIgnoreCase("DebugActivity.java")) return false;
        if (!hasCustomSketchApplication && name.equalsIgnoreCase("SketchApplication.java")) return false;
        if (GitSafetyGuard.isExcludedByDefault(normalized) || GitSafetyGuard.isSensitivePath(normalized)) return false;
        return !name.equalsIgnoreCase("local.properties");
    }

    private static File manifestFile(Context context, String scId) {
        return new File(context.getFilesDir(), "cooper_github_export_manifest_" + scId + ".json");
    }

    private static Set<String> readManifest(File file) throws IOException {
        if (!file.isFile()) return Collections.emptySet();
        StringBuilder data = new StringBuilder();
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) data.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        try {
            JSONArray array = new JSONArray(data.toString());
            Set<String> paths = new TreeSet<>();
            for (int i = 0; i < array.length(); i++) {
                String path = array.optString(i, "");
                if (path.trim().isEmpty() || path.startsWith("/") || path.contains("\\")
                        || path.matches("^[A-Za-z]:.*")) {
                    throw new IOException("The saved Android Studio export manifest contains an invalid path.");
                }
                for (String segment : path.split("/")) {
                    if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                        throw new IOException("The saved Android Studio export manifest contains an unsafe path.");
                    }
                }
                paths.add(path);
            }
            return paths;
        } catch (org.json.JSONException error) {
            throw new IOException("Cooper could not verify its saved Android Studio export manifest.", error);
        }
    }

    private static void writeManifest(File file, Set<String> paths) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cooper could not create the Android Studio export manifest directory.");
        }
        JSONArray array = new JSONArray();
        for (String path : paths) array.put(path);
        File temporary = new File(file.getAbsolutePath() + ".tmp");
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary, false))) {
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        File backup = new File(file.getAbsolutePath() + ".bak");
        if (backup.exists() && !backup.delete()) throw new IOException("Cooper could not replace the old export manifest safely.");
        if (file.exists() && !file.renameTo(backup)) throw new IOException("Cooper could not prepare the previous export manifest.");
        if (!temporary.renameTo(file)) {
            if (backup.exists()) backup.renameTo(file);
            throw new IOException("Cooper could not save the Android Studio export manifest.");
        }
        if (backup.exists()) backup.delete();
    }

    private static File resolveChild(File root, String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()
                || relativePath.startsWith("/") || relativePath.contains("\\")
                || relativePath.matches("^[A-Za-z]:.*")) {
            throw new IOException("An invalid relative path was rejected during Android Studio project synchronization.");
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("A path-traversal attempt was rejected during Android Studio project synchronization.");
            }
        }
        File target = new File(root, relativePath.replace('/', File.separatorChar)).getCanonicalFile();
        if (!target.getPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("A project file resolved outside the authorized Android Studio workspace.");
        }
        return target;
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cooper could not create an exported project folder: " + parent.getName());
        }
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, false))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
        target.setLastModified(source.lastModified());
    }

    private static boolean filesEqual(File first, File second) throws IOException {
        if (!second.isFile() || first.length() != second.length()) return false;
        try (BufferedInputStream a = new BufferedInputStream(new FileInputStream(first));
             BufferedInputStream b = new BufferedInputStream(new FileInputStream(second))) {
            byte[] left = new byte[BUFFER_SIZE];
            byte[] right = new byte[BUFFER_SIZE];
            while (true) {
                int countLeft = a.read(left);
                int countRight = b.read(right);
                if (countLeft != countRight) return false;
                if (countLeft == -1) return true;
                for (int i = 0; i < countLeft; i++) if (left[i] != right[i]) return false;
            }
        }
    }

    private static void deleteEmptyParents(File parent, File workspaceRoot) {
        File root;
        try { root = workspaceRoot.getCanonicalFile(); }
        catch (IOException ignored) { return; }
        File current = parent;
        while (current != null && !current.equals(root)) {
            try {
                File canonical = current.getCanonicalFile();
                if (!canonical.getPath().startsWith(root.getPath() + File.separator)
                        || ".git".equals(canonical.getName())) return;
                File[] children = canonical.listFiles();
                if (children == null || children.length != 0 || !canonical.delete()) return;
                current = canonical.getParentFile();
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private static boolean copyDirectory(File source, File target, File allowedRoot) throws IOException {
        File canonicalSource = source.getCanonicalFile();
        if (!canonicalSource.equals(allowedRoot) && !canonicalSource.getPath().startsWith(allowedRoot.getPath() + File.separator)) {
            throw new IOException("Git metadata resolved outside its authorized directory.");
        }
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory()) {
                throw new IOException("Cooper could not copy the previous Git metadata.");
            }
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cooper could not read the previous Git metadata.");
            for (File child : children) copyDirectory(child, new File(target, child.getName()), allowedRoot);
        } else if (source.isFile()) {
            copyFile(source, target);
        } else {
            throw new IOException("Unsupported file encountered in the previous Git metadata.");
        }
        return true;
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) if (!deleteRecursively(child)) return false;
        }
        return file.delete();
    }

    private static boolean same(String first, String second) {
        if (first == null) return second == null;
        return first.equals(second);
    }
}
