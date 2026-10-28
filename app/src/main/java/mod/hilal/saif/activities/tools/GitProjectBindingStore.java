package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.wq;

/**
 * Cooper project binding. The sourceProjectRoot identifies the original Sketchware project;
 * projectRoot is the dedicated Android Studio export tree used by Git and GitHub operations.
 */
public final class GitProjectBindingStore {
    private static final String PREFS = "cooper_github_project_binding";
    private static final String KEY_SC_ID = "sc_id";
    private static final String KEY_WORKSPACE_NAME = "workspace_name";
    private static final String KEY_REMOTE_OWNER = "remote_owner";
    private static final String KEY_REMOTE_REPOSITORY = "remote_repository";

    public static final class Binding {
        public final String scId;
        public final String workspaceName;
        public final String appName;
        public final String packageName;
        public final String versionName;
        public final String versionCode;
        /** Dedicated, generated Android Studio project tree used by all Git operations. */
        public final File projectRoot;
        /** Original Sketchware project data directory; used only to validate project identity/export sources. */
        public final File sourceProjectRoot;
        public final String remoteOwner;
        public final String remoteRepository;

        private Binding(String scId, String workspaceName, String appName,
                        String packageName, String versionName, String versionCode,
                        File projectRoot, File sourceProjectRoot, String remoteOwner, String remoteRepository) {
            this.scId = scId;
            this.workspaceName = workspaceName;
            this.appName = appName;
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.projectRoot = projectRoot;
            this.sourceProjectRoot = sourceProjectRoot;
            this.remoteOwner = remoteOwner;
            this.remoteRepository = remoteRepository;
        }

        public String remoteUrl() {
            return blank(remoteOwner) || blank(remoteRepository) ? "" :
                    "https://github.com/" + remoteOwner + "/" + remoteRepository + ".git";
        }
    }

    private final Context appContext;
    private final SharedPreferences preferences;

    public GitProjectBindingStore(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Binding bind(String scId) throws Exception {
        if (blank(scId) || !scId.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("A valid Cooper project ID is required.");
        }
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null || !scId.equals(String.valueOf(metadata.get("sc_id")))) {
            throw new IllegalStateException("Cooper could not verify the selected project metadata.");
        }
        File sourceRoot = new File(wq.b(scId)).getCanonicalFile();
        File root = GitAndroidStudioExportManager.getWorkspaceRoot(appContext, scId).getCanonicalFile();
        File metadataFile = new File(wq.c(scId), "project").getCanonicalFile();
        if (!sourceRoot.isDirectory() || !metadataFile.isFile()) {
            throw new IllegalStateException("The selected Cooper project directory does not match its project metadata.");
        }
        if (root.exists() && !root.isDirectory()) {
            throw new IllegalStateException("The Android Studio Git workspace path is not a directory.");
        }
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("Cooper could not create the Android Studio Git workspace.");
        }
        String workspace = safeValue(metadata.get("my_ws_name"), "Cooper project");
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_SC_ID, scId)
                .putString(KEY_WORKSPACE_NAME, workspace);
        String previousId = preferences.getString(KEY_SC_ID, null);
        if (previousId != null && !previousId.equals(scId)) {
            editor.remove(KEY_REMOTE_OWNER).remove(KEY_REMOTE_REPOSITORY);
        }
        boolean saved = editor.commit();
        if (!saved) throw new IllegalStateException("Cooper could not save the project binding.");
        return new Binding(scId, workspace, safeValue(metadata.get("my_app_name"), ""),
                safeValue(metadata.get("my_sc_pkg_name"), ""), safeValue(metadata.get("sc_ver_name"), ""),
                safeValue(metadata.get("sc_ver_code"), ""), root, sourceRoot,
                preferences.getString(KEY_REMOTE_OWNER, ""), preferences.getString(KEY_REMOTE_REPOSITORY, ""));
    }

    public Binding getBoundProject() throws Exception {
        String scId = preferences.getString(KEY_SC_ID, null);
        if (blank(scId)) return null;
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null || !scId.equals(String.valueOf(metadata.get("sc_id")))) {
            throw new IllegalStateException("The bound Cooper project is no longer available or its identity changed.");
        }
        File sourceRoot = new File(wq.b(scId)).getCanonicalFile();
        File root = GitAndroidStudioExportManager.getWorkspaceRoot(appContext, scId).getCanonicalFile();
        File metadataFile = new File(wq.c(scId), "project").getCanonicalFile();
        if (!sourceRoot.isDirectory() || !metadataFile.isFile()) {
            throw new IllegalStateException("The bound Cooper project directory could not be verified.");
        }
        if (root.exists() && !root.isDirectory()) {
            throw new IllegalStateException("The Android Studio Git workspace path is not a directory.");
        }
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("Cooper could not create the Android Studio Git workspace.");
        }
        return new Binding(scId,
                safeValue(metadata.get("my_ws_name"), preferences.getString(KEY_WORKSPACE_NAME, "Cooper project")),
                safeValue(metadata.get("my_app_name"), ""),
                safeValue(metadata.get("my_sc_pkg_name"), ""), safeValue(metadata.get("sc_ver_name"), ""),
                safeValue(metadata.get("sc_ver_code"), ""), root, sourceRoot,
                preferences.getString(KEY_REMOTE_OWNER, ""), preferences.getString(KEY_REMOTE_REPOSITORY, ""));
    }

    public void linkRemote(String owner, String repository) {
        if (blank(owner) || blank(repository)
                || !owner.matches("[A-Za-z0-9_.-]{1,100}")
                || !repository.matches("[A-Za-z0-9_.-]{1,100}")
                || owner.equals(".") || owner.equals("..") || repository.equals(".") || repository.equals("..")) {
            throw new IllegalArgumentException("A valid GitHub owner and repository are required.");
        }
        if (blank(preferences.getString(KEY_SC_ID, null))) {
            throw new IllegalStateException("Select a Cooper project before linking a remote repository.");
        }
        boolean saved = preferences.edit().putString(KEY_REMOTE_OWNER, owner)
                .putString(KEY_REMOTE_REPOSITORY, repository).commit();
        if (!saved) throw new IllegalStateException("Cooper could not save the remote repository binding.");
    }

    public void clearRemote() {
        preferences.edit().remove(KEY_REMOTE_OWNER).remove(KEY_REMOTE_REPOSITORY).commit();
    }

    public void clearBinding() {
        preferences.edit().remove(KEY_SC_ID).remove(KEY_WORKSPACE_NAME)
                .remove(KEY_REMOTE_OWNER).remove(KEY_REMOTE_REPOSITORY).commit();
    }

    private static String safeValue(Object value, String fallback) {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
